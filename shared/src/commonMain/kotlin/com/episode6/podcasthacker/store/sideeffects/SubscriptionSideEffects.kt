package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.data.repo.FeedRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.RefreshFeed
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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transform

@ContributesTo(AppScope::class)
interface SubscriptionSideEffects {

    /** Room is the source of truth: subscriptions flow into AppState from the db. */
    @Provides @IntoSet fun observeSubscriptions(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            repo.observeSubscriptions().mapActions { listOf(SetSubscriptions(it)) }
        }

    @Provides @IntoSet fun subscribe(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<SubscribeToPodcast>().transform { action ->
                syncingFeed(action.feedUrl, "Failed to subscribe") { repo.subscribe(action.feedUrl) }
            }
        }

    @Provides @IntoSet fun unsubscribe(repo: SubscriptionRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<UnsubscribeFromPodcast>().transform { action ->
                syncingFeed(action.feedUrl, "Failed to unsubscribe") { repo.unsubscribe(action.feedUrl) }
            }
        }

    @Provides @IntoSet fun refreshFeed(repo: FeedRepository): SideEffect<AppState> =
        sideEffect {
            actions.filterIsInstance<RefreshFeed>().transform { action ->
                syncingFeed(action.feedUrl, "Failed to refresh feed") { repo.sync(action.feedUrl) }
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
