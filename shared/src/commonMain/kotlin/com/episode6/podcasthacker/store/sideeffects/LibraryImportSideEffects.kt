package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.data.backup.EpisodeProgress
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.ImportLibrary
import com.episode6.podcasthacker.store.RestoreNowPlaying
import com.episode6.redux.sideeffects.SideEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@ContributesTo(AppScope::class)
interface LibraryImportSideEffects {

    /**
     * One-shot library import (an OPML or json backup file): subscribes to every feed
     * in the backup that isn't already subscribed — concurrently, each bracketed by the
     * same syncing actions as a manual subscribe so the grid's sync indicator works —
     * and only once **every** subscribe has succeeded or failed applies the backup's
     * listening state to the episodes that now exist. Ordering matters: progress can
     * only attach to episodes the feed syncs create. Entries that still have no
     * matching episode (failed sync, episode gone from its feed, feed not in the
     * backup) are dropped. Positions overwrite unconditionally — an import is an
     * explicit "make this device look like that one".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun importLibrary(
        subscriptionRepository: SubscriptionRepository,
        episodeRepository: EpisodeRepository,
    ): SideEffect<AppState> = sideEffect {
        actions.filterIsInstance<ImportLibrary>().flatMapMerge { action ->
            flow {
                val subscribed = currentState().subscriptions.map { it.feedUrl }.toSet()
                val newFeeds = action.backup.podcasts.map { it.feedUrl }.distinct().filter { it !in subscribed }
                // emitAll completes when every inner subscribe flow does, success or not
                emitAll(
                    newFeeds.asFlow().flatMapMerge { feedUrl ->
                        flow {
                            syncingFeed(feedUrl, "Failed to subscribe") { subscriptionRepository.subscribe(feedUrl) }
                        }
                    },
                )
                var importedPlayed = false
                action.backup.episodes.forEach { entry ->
                    val applied = episodeRepository.applyProgress(entry)
                    if (applied && entry.lastPlayedAtMs != null) importedPlayed = true
                }
                // the backup carried play history that landed: pop the now-playing bar
                // like it would have on the source device (no-op if something's playing)
                if (importedPlayed) emit(RestoreNowPlaying)
            }
        }
    }
}

/** True when [entry]'s episode exists and its listening state was applied. */
private suspend fun EpisodeRepository.applyProgress(entry: EpisodeProgress): Boolean {
    episode(entry.guid) ?: return false
    if (entry.positionMs > 0) setPlaybackPosition(entry.guid, entry.positionMs.milliseconds)
    entry.lastPlayedAtMs?.let { markPlayed(entry.guid, Instant.fromEpochMilliseconds(it)) }
    return true
}
