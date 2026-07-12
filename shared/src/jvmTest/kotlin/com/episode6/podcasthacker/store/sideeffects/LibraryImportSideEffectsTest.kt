package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import com.episode6.podcasthacker.data.backup.EpisodeProgress
import com.episode6.podcasthacker.data.backup.LibraryBackup
import com.episode6.podcasthacker.data.backup.PodcastBackup
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.ImportLibrary
import com.episode6.podcasthacker.store.SetFeedSyncError
import com.episode6.podcasthacker.store.SetFeedSyncing
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LibraryImportSideEffectsTest {

    private val feedUrl = "https://example.com/feed.xml"
    private val otherFeed = "https://example.com/other.xml"
    private val sideEffects = object : LibraryImportSideEffects {}

    private fun testEpisode(guid: String, feed: String = feedUrl) =
        Episode(guid = guid, feedUrl = feed, title = "Ep $guid")

    private fun backup(vararg feeds: String, episodes: List<EpisodeProgress> = emptyList()) =
        LibraryBackup(podcasts = feeds.map { PodcastBackup(feedUrl = it) }, episodes = episodes)

    @Test
    fun import_appliesProgressOnlyAfterEverySubscribeFinishes() = runTest {
        // the episodes only "exist" once their feed's subscribe has completed, so the
        // positions can only be applied if the import truly awaits both subscribes —
        // including the slow one
        var slowDone = false
        var fastDone = false
        val subscriptionRepo = mockk<SubscriptionRepository> {
            coEvery { subscribe(feedUrl) } coAnswers {
                delay(30.seconds) // longer than any fixed retry budget would allow
                slowDone = true
            }
            coEvery { subscribe(otherFeed) } coAnswers { fastDone = true }
        }
        val episodeRepo = mockk<EpisodeRepository>(relaxUnitFun = true) {
            coEvery { episode("slow-ep") } answers { if (slowDone) testEpisode("slow-ep") else null }
            coEvery { episode("fast-ep") } answers { if (fastDone) testEpisode("fast-ep", otherFeed) else null }
        }

        val actions = sideEffects.importLibrary(subscriptionRepo, episodeRepo)
            .output(
                ImportLibrary(
                    backup(
                        feedUrl, otherFeed,
                        episodes = listOf(
                            EpisodeProgress(feedUrl, "slow-ep", positionMs = 1_000),
                            EpisodeProgress(otherFeed, "fast-ep", positionMs = 2_000),
                        ),
                    ),
                ),
            )
            .toList()

        assertThat(actions).containsExactlyInAnyOrder(
            SetFeedSyncing(feedUrl, true),
            SetFeedSyncing(feedUrl, false),
            SetFeedSyncing(otherFeed, true),
            SetFeedSyncing(otherFeed, false),
        )
        coVerify(exactly = 1) {
            episodeRepo.setPlaybackPosition("slow-ep", 1.seconds)
            episodeRepo.setPlaybackPosition("fast-ep", 2.seconds)
        }
    }

    @Test
    fun import_skipsAlreadySubscribedFeeds_butStillAppliesTheirProgress() = runTest {
        val subscriptionRepo = mockk<SubscriptionRepository>()
        val episodeRepo = mockk<EpisodeRepository>(relaxUnitFun = true) {
            coEvery { episode("existing-ep") } returns testEpisode("existing-ep")
        }
        val state = AppState(subscriptions = listOf(Podcast(feedUrl = feedUrl, title = "already")))

        val actions = sideEffects.importLibrary(subscriptionRepo, episodeRepo)
            .output(
                ImportLibrary(
                    backup(
                        feedUrl,
                        episodes = listOf(
                            EpisodeProgress(feedUrl, "existing-ep", positionMs = 500, lastPlayedAtMs = 42),
                        ),
                    ),
                ),
                state = state,
            )
            .toList()

        assertThat(actions).isEmpty()
        coVerify(exactly = 0) { subscriptionRepo.subscribe(any()) }
        coVerify(exactly = 1) {
            episodeRepo.setPlaybackPosition("existing-ep", 500.milliseconds)
            episodeRepo.markPlayed("existing-ep", Instant.fromEpochMilliseconds(42))
        }
    }

    @Test
    fun import_failedSubscribe_emitsErrorAndDropsItsProgress_othersStillApply() = runTest {
        var subscribed = false
        val subscriptionRepo = mockk<SubscriptionRepository> {
            coEvery { subscribe(feedUrl) } throws RuntimeException("boom")
            coEvery { subscribe(otherFeed) } coAnswers { subscribed = true }
        }
        val episodeRepo = mockk<EpisodeRepository>(relaxUnitFun = true) {
            coEvery { episode("failed-ep") } returns null
            coEvery { episode("good-ep") } answers { if (subscribed) testEpisode("good-ep", otherFeed) else null }
        }

        val actions = sideEffects.importLibrary(subscriptionRepo, episodeRepo)
            .output(
                ImportLibrary(
                    backup(
                        feedUrl, otherFeed,
                        episodes = listOf(
                            EpisodeProgress(feedUrl, "failed-ep", positionMs = 1_000),
                            EpisodeProgress(otherFeed, "good-ep", positionMs = 2_000),
                        ),
                    ),
                ),
            )
            .toList()

        assertThat(actions.filterIsInstance<SetFeedSyncError>()).hasSize(1)
        coVerify(exactly = 1) { episodeRepo.setPlaybackPosition("good-ep", 2.seconds) }
        coVerify(exactly = 0) { episodeRepo.setPlaybackPosition("failed-ep", any()) }
    }

    @Test
    fun import_progressOnlyBackup_appliesImmediatelyWithoutSubscribing() = runTest {
        val subscriptionRepo = mockk<SubscriptionRepository>()
        val episodeRepo = mockk<EpisodeRepository>(relaxUnitFun = true) {
            coEvery { episode("ep") } returns testEpisode("ep")
        }

        val actions = sideEffects.importLibrary(subscriptionRepo, episodeRepo)
            .output(ImportLibrary(LibraryBackup(episodes = listOf(EpisodeProgress(feedUrl, "ep", positionMs = 9_000)))))
            .toList()

        assertThat(actions).isEmpty()
        coVerify(exactly = 0) { subscriptionRepo.subscribe(any()) }
        coVerify(exactly = 1) { episodeRepo.setPlaybackPosition("ep", 9.seconds) }
    }

    @Test
    fun import_duplicateFeedsInBackup_subscribeOnce() = runTest {
        val subscriptionRepo = mockk<SubscriptionRepository> {
            coEvery { subscribe(feedUrl) } returns Unit
        }
        val episodeRepo = mockk<EpisodeRepository>(relaxUnitFun = true)

        sideEffects.importLibrary(subscriptionRepo, episodeRepo)
            .output(ImportLibrary(backup(feedUrl, feedUrl)))
            .toList()

        coVerify(exactly = 1) { subscriptionRepo.subscribe(feedUrl) }
    }

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
