package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.repo.FeedRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.sideeffects.LoggingSideEffects
import com.episode6.podcasthacker.store.sideeffects.SubscriptionSideEffects
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffectMiddleware
import com.episode6.redux.testsupport.runStoreTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test

/**
 * Builds the app store with the full production side-effect set (mocked repos) and
 * asserts dispatched actions actually reach the side effects. Regression test for the
 * SideEffectMiddleware subscription contract: the middleware only starts relaying
 * actions once EVERY side effect has subscribed to context.actions, so a side effect
 * that never touches the actions flow silently blocks all of them.
 */
class AppStoreSideEffectsIntegrationTest {

    private val feedUrl = "https://example.com/feed.xml"

    private val subscriptionRepo = mockk<SubscriptionRepository> {
        every { observeSubscriptions() } returns flowOf(listOf(Podcast(feedUrl = feedUrl, title = "Test")))
        coEvery { subscribe(any()) } returns Unit
    }
    private val feedRepo = mockk<FeedRepository> {
        coEvery { sync(any()) } returns Unit
    }

    private fun CoroutineScope.productionStore(): AppStore {
        val logging = object : LoggingSideEffects {}
        val subscriptions = object : SubscriptionSideEffects {}
        return StoreFlow(
            scope = this,
            initialValue = AppState(),
            reducer = AppState::reduce,
            middlewares = listOf(
                SideEffectMiddleware(
                    setOf(
                        logging.loggingSideEffect(),
                        subscriptions.observeSubscriptions(subscriptionRepo),
                        subscriptions.subscribe(subscriptionRepo),
                        subscriptions.unsubscribe(subscriptionRepo),
                        subscriptions.refreshFeed(feedRepo),
                    )
                )
            ),
        )
    }

    @Test
    fun subscribeAction_reachesTheSideEffects() = runStoreTest(storeBuilder = { productionStore() }) { store ->
        store.dispatch(SubscribeToPodcast(feedUrl))

        coVerify(exactly = 1) { subscriptionRepo.subscribe(feedUrl) }
    }

    @Test
    fun observedSubscriptions_flowIntoState() = runStoreTest(storeBuilder = { productionStore() }) { store ->
        store.first { it.subscriptions.isNotEmpty() }
    }

    @Test
    fun refreshAction_reachesTheSideEffects() = runStoreTest(storeBuilder = { productionStore() }) { store ->
        store.dispatch(RefreshFeed(feedUrl))

        coVerify(exactly = 1) { feedRepo.sync(feedUrl) }
    }
}
