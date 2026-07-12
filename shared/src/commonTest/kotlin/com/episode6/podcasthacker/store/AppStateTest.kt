package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import kotlin.test.Test
import kotlin.time.Instant

class AppStateTest {

    private fun podcast(feedUrl: String) = Podcast(feedUrl = feedUrl, title = feedUrl)

    private fun episode(feedUrl: String, guid: String, pubDate: Instant?) =
        Episode(guid = guid, feedUrl = feedUrl, title = guid, pubDate = pubDate)

    private fun day(n: Int): Instant = Instant.fromEpochMilliseconds(n * 86_400_000L)

    @Test
    fun subscriptionsByLatestEpisode_freshestReleaseFirst() {
        val state = AppState(
            subscriptions = listOf(podcast("a"), podcast("b"), podcast("c")),
            episodesByFeed = mapOf(
                "a" to listOf(episode("a", "a1", day(1))),
                "b" to listOf(episode("b", "b1", day(3)), episode("b", "b2", day(2))),
                "c" to listOf(episode("c", "c1", day(2))),
            ),
        )

        assertThat(state.subscriptionsByLatestEpisode().map { it.feedUrl })
            .isEqualTo(listOf("b", "c", "a"))
    }

    /** The db orders each feed's episodes pubDate desc, but the sort must not depend on
     * that: the newest release wins even when it isn't the first list entry. */
    @Test
    fun subscriptionsByLatestEpisode_usesNewestEpisodeRegardlessOfListOrder() {
        val state = AppState(
            subscriptions = listOf(podcast("a"), podcast("b")),
            episodesByFeed = mapOf(
                "a" to listOf(episode("a", "a1", day(2))),
                "b" to listOf(episode("b", "b1", null), episode("b", "b2", day(5))),
            ),
        )

        assertThat(state.subscriptionsByLatestEpisode().map { it.feedUrl })
            .isEqualTo(listOf("b", "a"))
    }

    @Test
    fun subscriptionsByLatestEpisode_feedsWithoutDatedEpisodesSortLastInStableOrder() {
        val state = AppState(
            subscriptions = listOf(podcast("no-episodes"), podcast("undated"), podcast("dated")),
            episodesByFeed = mapOf(
                "undated" to listOf(episode("undated", "u1", null)),
                "dated" to listOf(episode("dated", "d1", day(1))),
            ),
        )

        assertThat(state.subscriptionsByLatestEpisode().map { it.feedUrl })
            .isEqualTo(listOf("dated", "no-episodes", "undated"))
    }
}
