package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.data.model.DownloadState as PersistedDownloadState
import com.episode6.podcasthacker.data.model.toDomain
import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.downloads.DownloadScheduler
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.DeleteDownload
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.SetEpisodeDownloadStatus
import com.episode6.podcasthacker.store.SetEpisodes
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.tacita.DownloadState
import com.episode6.tacita.Tacita
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/** How many episodes may download simultaneously (matches podcast-puller-2's default). */
internal const val MAX_CONCURRENT_DOWNLOADS = 4

@ContributesTo(AppScope::class)
interface DownloadSideEffects {

    /** Instant feedback: an episode reads as queued the moment it's requested, even
     * while earlier downloads hold all the download slots below. */
    @Provides @IntoSet fun enqueueDownloads(): SideEffect<AppState> = sideEffect {
        actions.filterIsInstance<DownloadEpisode>()
            .map { SetEpisodeDownloadStatus(it.episodeGuid, EpisodeDownloadStatus.Queued) }
    }

    /** Download queue running up to [MAX_CONCURRENT_DOWNLOADS] episodes at once
     * (podcast-puller-2's maxDownloadThreads pattern); further requests stay Queued
     * until a slot frees. Collects tacita's download flow per episode, mapping its
     * states to in-flight status actions; the persisted downloaded flag lands in Room
     * on completion and the ad-diff reference is cleaned up. A failed episode reports
     * Failure without blocking the rest of the queue. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun downloadEpisodes(
        tacita: Tacita,
        episodeRepository: EpisodeRepository,
        downloadsRepository: DownloadsRepository,
    ): SideEffect<AppState> = sideEffect {
        actions.filterIsInstance<DownloadEpisode>()
            // At full concurrency flatMapMerge suspends its upstream collector until a
            // slot frees, which would stall the middleware's zero-buffer action relay
            // (and with it every side effect — taps beyond the first queued episode
            // went dead). Park pending requests in an unbounded buffer instead so the
            // collect path always accepts actions immediately.
            .buffer(Channel.UNLIMITED)
            .flatMapMerge(concurrency = MAX_CONCURRENT_DOWNLOADS) { action ->
                flow { downloadEpisode(action.episodeGuid, tacita, episodeRepository, downloadsRepository) }
            }
    }

    /**
     * Tells the platform when the download queue transitions between empty and
     * non-empty so it can keep the process alive while downloads run (see
     * [DownloadScheduler]). Failure entries linger in the downloads map awaiting a
     * retry, so they don't count as active work.
     */
    @Provides @IntoSet fun scheduleBackgroundDownloads(
        scheduler: DownloadScheduler,
    ): SideEffect<AppState> = sideEffect {
        var active = false
        actions.transform { action ->
            when {
                action is DownloadEpisode && !active -> {
                    active = true
                    scheduler.onQueueActive()
                }
                action is SetEpisodeDownloadStatus && active -> {
                    // actions reach side effects post-reduction, so this state is fresh
                    val idle = currentState().downloads.values.all { it is EpisodeDownloadStatus.Failure }
                    if (idle) {
                        active = false
                        scheduler.onQueueIdle()
                    }
                }
            }
        }
    }

    /** Completion handshake for [downloadEpisodes]: a finished download leaves a
     * Finishing entry (its Room downloaded flag is written but still in flight through
     * Room's flow → SetEpisodes → [AppState.episodesByFeed]). Clear each entry only once
     * the store's episode reads Downloaded, so the UI goes progress → play with no
     * Download-button flash in between. Reacting to [SetEpisodeDownloadStatus] as well as
     * [SetEpisodes] covers a re-download of an already-Downloaded episode, where the flag
     * never changes and Room may never re-emit. An episode missing from the store (feed
     * pruned mid-download) can never settle, so its entry is dropped too. */
    @Provides @IntoSet fun clearFinishedDownloads(): SideEffect<AppState> = sideEffect {
        actions.transform { action ->
            if (action !is SetEpisodes && action !is SetEpisodeDownloadStatus) return@transform
            // actions reach side effects post-reduction, so this state is fresh
            val state = currentState()
            state.downloads.forEach { (guid, status) ->
                if (status != EpisodeDownloadStatus.Finishing) return@forEach
                val downloadState = state.episode(guid)?.downloadState
                if (downloadState == null || downloadState == PersistedDownloadState.Downloaded) {
                    emit(SetEpisodeDownloadStatus(guid, null))
                }
            }
        }
    }

    /** File deletion runs inside flatMapMerge rather than inline in the collect path,
     * for the same relay-stalling reason as [downloadEpisodes] above. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun deleteDownloads(
        downloadsRepository: DownloadsRepository,
    ): SideEffect<AppState> = sideEffect {
        actions.filterIsInstance<DeleteDownload>()
            .flatMapMerge { action ->
                flow {
                    val result = runCatching { downloadsRepository.deleteDownload(action.episodeGuid) }
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    // clear any lingering in-flight entry (e.g. an old Failure)
                    emit(SetEpisodeDownloadStatus(action.episodeGuid, null))
                }
            }
    }
}

private suspend fun FlowCollector<Action>.downloadEpisode(
    episodeGuid: String,
    tacita: Tacita,
    episodeRepository: EpisodeRepository,
    downloadsRepository: DownloadsRepository,
) {
    val episode = episodeRepository.episode(episodeGuid)
    val audioUrl = episode?.audioUrl
    if (audioUrl == null) {
        emit(SetEpisodeDownloadStatus(episodeGuid, EpisodeDownloadStatus.Failure("episode has no audio url")))
        return
    }
    // Tacita reports progress once per 8KB chunk (thousands of emissions per episode);
    // dispatching each one floods the store's side-effect relay and stalls delivery of
    // every other action (e.g. a newly tapped DownloadEpisode). Quantizing to whole
    // percents + dropping repeats keeps it to ~100 actions per download.
    var lastStatus: EpisodeDownloadStatus? = null
    suspend fun emitStatus(status: EpisodeDownloadStatus) {
        if (status == lastStatus) return
        lastStatus = status
        emit(SetEpisodeDownloadStatus(episodeGuid, status))
    }
    emitStatus(EpisodeDownloadStatus.Starting)
    val outputFile = downloadsRepository.downloadFilePath(episodeGuid)
    downloadsRepository.prepareForDownload(episodeGuid)
    val result = runCatching {
        tacita.downloadPodcast(
            url = audioUrl,
            outputFile = outputFile,
            referenceFile = downloadsRepository.referenceFilePath(episodeGuid),
            // a leftover file (crash, or an explicit re-download) gets promoted to the
            // ad-diff reference by tacita rather than blocking the download
            overwrite = downloadsRepository.downloadedFileExists(episodeGuid),
            cutAds = true,
            // feed-declared size/duration let tacita verify + serve a clean copy directly
            // when the host injects sticky fill on every tier (blinding the ad-diff)
            declaredEnclosureBytes = episode.enclosureBytes,
            expectedDurationSeconds = episode.duration?.inWholeSeconds,
        ).collect { state ->
            when (state) {
                is DownloadState.Downloading -> emitStatus(
                    // the second (reference) copy downloading is part of the ad-cut
                    // pass as far as the user is concerned
                    if (state.file == outputFile) {
                        EpisodeDownloadStatus.Downloading(state.percentComplete.quantizedToWholePercent())
                    } else {
                        EpisodeDownloadStatus.CuttingAds
                    },
                )
                DownloadState.CuttingAds -> emitStatus(EpisodeDownloadStatus.CuttingAds)
                is DownloadState.Complete -> {
                    // persisted before the downloaded flag so the flag never appears
                    // without its candidates
                    downloadsRepository.saveAdBoundaryCandidates(
                        episodeGuid = episodeGuid,
                        candidates = state.adBoundaryCandidates.map { it.toDomain() },
                    )
                    downloadsRepository.markDownloaded(episodeGuid, downloaded = true)
                    downloadsRepository.deleteReferenceFile(episodeGuid)
                    // not null: clearing now would flash the Download button until Room's
                    // flow delivers the downloaded flag (see clearFinishedDownloads)
                    emitStatus(EpisodeDownloadStatus.Finishing)
                }
            }
        }
    }
    result.exceptionOrNull()?.let {
        if (it is CancellationException) throw it
        emit(
            SetEpisodeDownloadStatus(
                episodeGuid = episodeGuid,
                status = EpisodeDownloadStatus.Failure(it.message ?: it::class.simpleName ?: "download failed"),
            )
        )
    }
}

private fun Float.quantizedToWholePercent(): Float = (this * 100).toInt() / 100f
