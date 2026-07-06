package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.data.repo.FeedRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.RefreshFeed
import com.episode6.podcasthacker.store.SetEpisodes
import com.episode6.podcasthacker.store.SetFeedSyncError
import com.episode6.podcasthacker.store.SetFeedSyncing
import com.episode6.podcasthacker.store.SetSubscriptions
import com.episode6.podcasthacker.store.SubscribeToPodcast
import com.episode6.podcasthacker.store.UnsubscribeFromPodcast
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * The repo work below runs inside flatMapMerge (not inline in the action-collection
 * path via transform): SideEffectMiddleware relays actions through a zero-buffer shared
 * flow, so a side effect that suspends between emissions stalls action delivery to
 * *every* side effect until it finishes — a feed sync used to freeze download taps.
 */
@ContributesTo(AppScope::class)
interface SubscriptionSideEffects {

    /**
     * Room is the source of truth: subscriptions flow into AppState from the db.
     *
     * SideEffectMiddleware doesn't relay any actions until every side effect has
     * subscribed to [com.episode6.redux.sideeffects.SideEffectContext.actions], so this
     * observe-only effect must still subscribe (with an empty filter) or it silently
     * starves all the other side effects of their input.
     */
    @Provides @IntoSet fun observeSubscriptions(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            merge(
                actions.filter { false },
                repo.observeSubscriptions().mapActions { listOf(SetSubscriptions(it)) },
            )
        }

    /** All episodes flow into AppState through one app-lifetime observer: per-screen
     * Room flow collections proved unreliable (TODO.md Risk 10). */
    @Provides @IntoSet fun observeEpisodes(repo: EpisodeRepository): SideEffect<AppState> =
        sideEffect {
            merge(
                actions.filter { false },
                repo.observeAllEpisodes().mapActions { episodes ->
                    listOf(SetEpisodes(episodes.groupBy { it.feedUrl }))
                },
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun subscribe(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<SubscribeToPodcast>().flatMapMerge { action ->
                flow { syncingFeed(action.feedUrl, "Failed to subscribe") { repo.subscribe(action.feedUrl) } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun unsubscribe(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<UnsubscribeFromPodcast>().flatMapMerge { action ->
                flow { syncingFeed(action.feedUrl, "Failed to unsubscribe") { repo.unsubscribe(action.feedUrl) } }
            }
        }

    /** A feed already mid-sync skips the refresh instead of re-syncing concurrently:
     * screens dispatch [RefreshFeed] on every open, and now that refreshes run in
     * parallel a second dispatch would race the first on the same feed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides @IntoSet fun refreshFeed(repo: FeedRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<RefreshFeed>().flatMapMerge { action ->
                flow {
                    if (action.feedUrl in currentState().feedSync.syncing) return@flow
                    syncingFeed(action.feedUrl, "Failed to refresh feed") { repo.sync(action.feedUrl) }
                }
            }
        }
}

/** Brackets [block] with syncing-state actions, emitting an error action on failure. */
private suspend fun FlowCollector<Action>.syncingFeed(
    feedUrl: String,
    errorPrefix: String,
    block: suspend () -> Unit,
) {
    emit(SetFeedSyncing(feedUrl, true))
    val result = runCatching { block() }
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    emit(SetFeedSyncing(feedUrl, false))
    result.exceptionOrNull()?.let { emit(SetFeedSyncError("$errorPrefix: ${it.message ?: it::class.simpleName}")) }
}
