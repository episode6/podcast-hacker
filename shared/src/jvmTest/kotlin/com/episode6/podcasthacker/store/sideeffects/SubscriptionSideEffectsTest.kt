package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactly
import com.episode6.podcasthacker.data.model.Podcast
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
import com.episode6.redux.sideeffects.SideEffectContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
