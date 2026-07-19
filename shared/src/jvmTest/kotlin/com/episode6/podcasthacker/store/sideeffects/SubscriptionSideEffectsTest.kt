package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.repo.FeedRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.FeedSyncState
import com.episode6.podcasthacker.store.RefreshAllFeeds
import com.episode6.podcasthacker.store.RefreshFeed
import com.episode6.podcasthacker.store.SetFeedSyncError
import com.episode6.podcasthacker.store.SetFeedSyncing
import com.episode6.podcasthacker.store.SetSubscriptions
import com.episode6.podcasthacker.store.SubscribeToPodcast
import com.episode6.podcasthacker.store.UnsubscribeFromPodcast
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SubscriptionSideEffectsTest {

    private val feedUrl = "https://example.com/feed.xml"
    private val sideEffects = object : SubscriptionSideEffects {}

    @Test
    fun observeSubscriptions_feedsDbIntoState() = runTest {
        val podcasts = listOf(Podcast(feedUrl = feedUrl, title = "Test Podcast"))
        val repo = mockk<SubscriptionRepository> {
            every { observeSubscriptions() } returns flowOf(podcasts)
        }

        val actions = sideEffects.observeSubscriptions(repo).output().toList()

        assertThat(actions).containsExactly(SetSubscriptions(podcasts))
    }

    @Test
    fun subscribe_bracketsWithSyncingActions() = runTest {
        val repo = mockk<SubscriptionRepository> {
            coEvery { subscribe(feedUrl) } returns Unit
        }

        val actions = sideEffects.subscribe(repo).output(SubscribeToPodcast(feedUrl)).toList()

        assertThat(actions).containsExactly(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
        )
        coVerify(exactly = 1) { repo.subscribe(feedUrl) }
    }

    @Test
    fun subscribe_emitsErrorOnFailure() = runTest {
        val repo = mockk<SubscriptionRepository> {
            coEvery { subscribe(feedUrl) } throws RuntimeException("boom")
        }

        val actions = sideEffects.subscribe(repo).output(SubscribeToPodcast(feedUrl)).toList()

        assertThat(actions).containsExactly(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
            SetFeedSyncError("Failed to subscribe: boom"),
        )
    }

    @Test
    fun unsubscribe_callsRepo() = runTest {
        val repo = mockk<SubscriptionRepository> {
            coEvery { unsubscribe(feedUrl) } returns Unit
        }

        val actions = sideEffects.unsubscribe(repo).output(UnsubscribeFromPodcast(feedUrl)).toList()

        assertThat(actions).containsExactly(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
        )
        coVerify(exactly = 1) { repo.unsubscribe(feedUrl) }
    }

    @Test
    fun refreshFeed_syncsViaFeedRepository() = runTest {
        val repo = mockk<FeedRepository> {
            coEvery { sync(feedUrl) } returns Unit
        }

        val actions = sideEffects.refreshFeed(repo).output(RefreshFeed(feedUrl)).toList()

        assertThat(actions).containsExactly(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
        )
        coVerify(exactly = 1) { repo.sync(feedUrl) }
    }

    /** A slow sync must not hold up the action-collection path: syncs run in
     * parallel via flatMapMerge instead of queueing behind each other. */
    @Test
    fun refreshFeed_slowSync_doesNotBlockOtherRefreshes() = runTest {
        val otherFeed = "https://example.com/other.xml"
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val repo = mockk<FeedRepository> {
            coEvery { sync(any()) } coAnswers {
                val url = firstArg<String>()
                started += url
                if (url == feedUrl) gate.await()
            }
        }

        val collector = launch {
            sideEffects.refreshFeed(repo).output(RefreshFeed(feedUrl), RefreshFeed(otherFeed)).toList()
        }
        runCurrent()
        assertThat(started).containsExactly(feedUrl, otherFeed)

        gate.complete(Unit)
        collector.join()
    }

    @Test
    fun refreshFeed_skipsFeedAlreadySyncing() = runTest {
        val repo = mockk<FeedRepository>()

        val actions = sideEffects.refreshFeed(repo)
            .output(RefreshFeed(feedUrl), state = AppState(feedSync = FeedSyncState(syncing = setOf(feedUrl))))
            .toList()

        assertThat(actions).isEmpty()
        coVerify(exactly = 0) { repo.sync(any()) }
    }

    @Test
    fun refreshAllFeeds_syncsEverySubscription() = runTest {
        val otherFeed = "https://example.com/other.xml"
        val state = AppState(
            subscriptions = listOf(
                Podcast(feedUrl = feedUrl, title = "One"),
                Podcast(feedUrl = otherFeed, title = "Two"),
            ),
        )
        val repo = mockk<FeedRepository> {
            coEvery { sync(any()) } returns Unit
        }

        val actions = sideEffects.refreshAllFeeds(repo).output(RefreshAllFeeds, state = state).toList()

        // the per-feed syncs run concurrently, so only per-feed ordering is guaranteed
        assertThat(actions.filter { (it as SetFeedSyncing).feedUrl == feedUrl }).containsExactly(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
        )
        assertThat(actions.filter { (it as SetFeedSyncing).feedUrl == otherFeed }).containsExactly(
            SetFeedSyncing(otherFeed, true),
            SetFeedSyncing(otherFeed, false),
        )
        coVerify(exactly = 1) { repo.sync(feedUrl) }
        coVerify(exactly = 1) { repo.sync(otherFeed) }
    }

    @Test
    fun refreshAllFeeds_skipsFeedsAlreadySyncing() = runTest {
        val otherFeed = "https://example.com/other.xml"
        val state = AppState(
            subscriptions = listOf(
                Podcast(feedUrl = feedUrl, title = "One"),
                Podcast(feedUrl = otherFeed, title = "Two"),
            ),
            feedSync = FeedSyncState(syncing = setOf(feedUrl)),
        )
        val repo = mockk<FeedRepository> {
            coEvery { sync(any()) } returns Unit
        }

        val actions = sideEffects.refreshAllFeeds(repo).output(RefreshAllFeeds, state = state).toList()

        assertThat(actions).containsExactly(
            SetFeedSyncing(otherFeed, true),
            SetFeedSyncing(otherFeed, false),
        )
        coVerify(exactly = 0) { repo.sync(feedUrl) }
        coVerify(exactly = 1) { repo.sync(otherFeed) }
    }

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
