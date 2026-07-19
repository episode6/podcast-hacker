package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactly
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.downloads.DownloadScheduler
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.DeleteDownload
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.SetEpisodeDownloadStatus
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import com.episode6.tacita.AdBoundaryCandidate
import com.episode6.tacita.DownloadState
import com.episode6.tacita.Tacita
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DownloadSideEffectsTest {

    private val guid = "episode-guid"
    private val audioUrl = "https://example.com/ep.mp3"
    private val outputFile: Path = "/downloads/hash.mp3".toPath()
    private val referenceFile: Path = "/cache/hash.adref".toPath()

    private val sideEffects = object : DownloadSideEffects {}

    private val enclosureBytes = 59_568_000L
    private val duration = 3723.seconds

    private val episodeRepo = mockk<EpisodeRepository> {
        coEvery { episode(guid) } returns Episode(
            guid = guid,
            feedUrl = "feed",
            title = "Ep",
            audioUrl = audioUrl,
            duration = duration,
            enclosureBytes = enclosureBytes,
        )
    }
    private val downloadsRepo = mockk<DownloadsRepository>(relaxUnitFun = true) {
        every { downloadFilePath(guid) } returns outputFile
        every { referenceFilePath(guid) } returns referenceFile
        every { downloadedFileExists(guid) } returns false
        coEvery { markDownloaded(any(), any()) } returns Unit
        coEvery { deleteDownload(any()) } returns Unit
    }

    @Test
    fun downloadRequest_readsAsQueuedImmediately() = runTest {
        val actions = sideEffects.enqueueDownloads().output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Queued),
        )
    }

    @Test
    fun mapsTacitaStates_toEpisodeStatuses() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds)
            } returns flowOf(
                DownloadState.Downloading(outputFile, 0.25f),
                DownloadState.Downloading(outputFile, 0.75f),
                DownloadState.Downloading(referenceFile, 0.5f), // reference copy
                DownloadState.CuttingAds,
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.25f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.75f)),
            // reference-copy Downloading and CuttingAds collapse into one status
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.CuttingAds),
            SetEpisodeDownloadStatus(guid, null),
        )
        coVerify(exactly = 1) { downloadsRepo.markDownloaded(guid, true) }
        coVerify(exactly = 1) { downloadsRepo.deleteReferenceFile(guid) }
        coVerify(exactly = 1) { downloadsRepo.prepareForDownload(guid) }
    }

    /** Tacita emits progress per 8KB chunk; unthrottled, those actions flood the store's
     * side-effect relay and stall every other action behind them. */
    @Test
    fun progressUpdates_quantizedToWholePercents_andDeduped() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds)
            } returns flowOf(
                DownloadState.Downloading(outputFile, 0.0f),
                DownloadState.Downloading(outputFile, 0.001f),
                DownloadState.Downloading(outputFile, 0.011f),
                DownloadState.Downloading(outputFile, 0.014f),
                DownloadState.Downloading(outputFile, 0.019f),
                DownloadState.Downloading(outputFile, 0.021f),
                DownloadState.Downloading(referenceFile, 0.5f), // reference copy
                DownloadState.Downloading(referenceFile, 0.9f), // reference copy
                DownloadState.CuttingAds,
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.0f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.01f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.02f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.CuttingAds),
            SetEpisodeDownloadStatus(guid, null),
        )
    }

    @Test
    fun completedDownload_persistsAdBoundaryCandidates_beforeDownloadedFlag() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds)
            } returns flowOf(
                DownloadState.Complete(
                    listOf(
                        AdBoundaryCandidate(60_000L, AdBoundaryCandidate.Source.DIFF_CUT, AdBoundaryCandidate.Role.START, confidence = 0.65f),
                        AdBoundaryCandidate(90_000L, AdBoundaryCandidate.Source.DAI_SLOT, AdBoundaryCandidate.Role.JOIN, confidence = 0.8f),
                    ),
                ),
            )
        }

        sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        coVerifyOrder {
            downloadsRepo.saveAdBoundaryCandidates(
                guid,
                listOf(
                    AdBoundary(60.seconds, AdBoundary.Source.DiffCut, AdBoundary.Role.Start, confidence = 0.65f),
                    AdBoundary(90.seconds, AdBoundary.Source.DaiSlot, AdBoundary.Role.Join, confidence = 0.8f),
                ),
            )
            downloadsRepo.markDownloaded(guid, true)
        }
    }

    @Test
    fun existingFile_downloadsWithOverwrite() = runTest {
        every { downloadsRepo.downloadedFileExists(guid) } returns true
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, true, true, enclosureBytes, duration.inWholeSeconds)
            } returns flowOf(
                DownloadState.Complete(),
            )
        }

        sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        coVerify(exactly = 1) {
            tacita.downloadPodcast(audioUrl, outputFile, referenceFile, true, true, enclosureBytes, duration.inWholeSeconds)
        }
    }

    @Test
    fun failedDownload_reportsFailure_withoutBlockingTheQueue() = runTest {
        val otherGuid = "other-guid"
        val otherOutput = "/downloads/other.mp3".toPath()
        coEvery { episodeRepo.episode(otherGuid) } returns
            Episode(guid = otherGuid, feedUrl = "feed", title = "Other", audioUrl = audioUrl)
        every { downloadsRepo.downloadFilePath(otherGuid) } returns otherOutput
        every { downloadsRepo.referenceFilePath(otherGuid) } returns referenceFile
        every { downloadsRepo.downloadedFileExists(otherGuid) } returns false

        val tacita = mockk<Tacita> {
            every { downloadPodcast(audioUrl, outputFile, any(), any(), any(), any(), any()) } returns flow {
                throw RuntimeException("boom")
            }
            every { downloadPodcast(audioUrl, otherOutput, any(), any(), any(), any(), any()) } returns flowOf(
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid), DownloadEpisode(otherGuid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Failure("boom")),
            SetEpisodeDownloadStatus(otherGuid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(otherGuid, null),
        )
        coVerify(exactly = 1) { downloadsRepo.markDownloaded(otherGuid, true) }
        coVerify(exactly = 0) { downloadsRepo.markDownloaded(guid, any()) }
    }

    @Test
    fun downloads_runConcurrently_cappedAtFour() = runTest {
        val guids = (1..5).map { "guid-$it" }
        val gates = guids.associateWith { CompletableDeferred<Unit>() }
        val started = mutableListOf<String>()
        val tacita = mockk<Tacita>()
        guids.forEach { g ->
            val output = "/downloads/$g.mp3".toPath()
            coEvery { episodeRepo.episode(g) } returns
                Episode(guid = g, feedUrl = "feed", title = g, audioUrl = audioUrl)
            every { downloadsRepo.downloadFilePath(g) } returns output
            every { downloadsRepo.referenceFilePath(g) } returns "/cache/$g.adref".toPath()
            every { downloadsRepo.downloadedFileExists(g) } returns false
            every { tacita.downloadPodcast(audioUrl, output, any(), any(), any(), any(), any()) } returns flow {
                started += g
                gates.getValue(g).await()
                emit(DownloadState.Complete())
            }
        }

        val collector = launch {
            sideEffects.downloadEpisodes(tacita, episodeRepo, downloadsRepo)
                .output(*guids.map { DownloadEpisode(it) }.toTypedArray())
                .toList()
        }
        runCurrent()
        assertThat(started).containsExactly("guid-1", "guid-2", "guid-3", "guid-4")

        gates.getValue("guid-1").complete(Unit)
        runCurrent()
        assertThat(started).containsExactly("guid-1", "guid-2", "guid-3", "guid-4", "guid-5")

        guids.forEach { gates.getValue(it).complete(Unit) }
        collector.join()
    }

    @Test
    fun episodeWithoutAudioUrl_reportsFailure() = runTest {
        coEvery { episodeRepo.episode(guid) } returns
            Episode(guid = guid, feedUrl = "feed", title = "Ep", audioUrl = null)

        val actions = sideEffects.downloadEpisodes(mockk(), episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Failure("episode has no audio url")),
        )
    }

    @Test
    fun deleteDownload_deletesAndClearsStatus() = runTest {
        val actions = sideEffects.deleteDownloads(downloadsRepo).output(DeleteDownload(guid)).toList()

        assertThat(actions).containsExactly(SetEpisodeDownloadStatus(guid, null))
        coVerify(exactly = 1) { downloadsRepo.deleteDownload(guid) }
    }

    /** A slow file delete must not hold up the action-collection path: deletes run
     * in parallel via flatMapMerge instead of queueing behind each other. */
    @Test
    fun deleteDownload_slowDelete_doesNotBlockOtherDeletes() = runTest {
        val otherGuid = "other-guid"
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        coEvery { downloadsRepo.deleteDownload(any()) } coAnswers {
            val g = firstArg<String>()
            started += g
            if (g == guid) gate.await()
        }

        val collector = launch {
            sideEffects.deleteDownloads(downloadsRepo).output(DeleteDownload(guid), DeleteDownload(otherGuid)).toList()
        }
        runCurrent()
        assertThat(started).containsExactly(guid, otherGuid)

        gate.complete(Unit)
        collector.join()
    }

    @Test
    fun scheduler_activatesOnFirstDownload_idlesWhenQueueEmpties() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(
                DownloadEpisode("a"),
                DownloadEpisode("b"), // second enqueue while active: no re-activation
                SetEpisodeDownloadStatus("a", null),
                SetEpisodeDownloadStatus("b", null),
            )
            coEvery { currentState() } returnsMany listOf(
                AppState(downloads = mapOf("b" to EpisodeDownloadStatus.Queued)),
                AppState(),
            )
        }

        val effect = sideEffects.scheduleBackgroundDownloads(scheduler)
        with(effect) { context.act() }.toList()

        verifyOrder {
            scheduler.onQueueActive()
            scheduler.onQueueIdle()
        }
        verify(exactly = 1) { scheduler.onQueueActive() }
        verify(exactly = 1) { scheduler.onQueueIdle() }
    }

    @Test
    fun scheduler_treatsLingeringFailuresAsIdle() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)
        val failedState = AppState(downloads = mapOf("a" to EpisodeDownloadStatus.Failure("boom")))
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(
                DownloadEpisode("a"),
                SetEpisodeDownloadStatus("a", EpisodeDownloadStatus.Failure("boom")),
            )
            coEvery { currentState() } returns failedState
        }

        val effect = sideEffects.scheduleBackgroundDownloads(scheduler)
        with(effect) { context.act() }.toList()

        verify(exactly = 1) { scheduler.onQueueIdle() }
    }

    @Test
    fun scheduler_ignoresStatusChangesWhileInactive() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)

        sideEffects.scheduleBackgroundDownloads(scheduler)
            .output(SetEpisodeDownloadStatus("a", null))
            .toList()

        verify(exactly = 0) { scheduler.onQueueActive() }
        verify(exactly = 0) { scheduler.onQueueIdle() }
    }

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
