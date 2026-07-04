package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.redux.Action
import kotlin.test.Test

class AppStoreReducerTest {

    private val playing = NowPlayingState(episodeTitle = "test episode", isPlaying = true)

    @Test
    fun setNowPlaying_updatesState() {
        val result = AppState().reduce(SetNowPlaying(playing))

        assertThat(result.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun setNowPlayingNull_clearsState() {
        val result = AppState(nowPlaying = playing).reduce(SetNowPlaying(null))

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun unknownAction_leavesStateUntouched() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(object : Action {})

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun setSubscriptions_replacesList() {
        val podcasts = listOf(Podcast(feedUrl = "https://example.com/feed.xml", title = "Test"))

        val result = AppState().reduce(SetSubscriptions(podcasts))

        assertThat(result.subscriptions).isEqualTo(podcasts)
    }

    @Test
    fun setFeedSyncing_addsAndRemovesFromSet() {
        val feedUrl = "https://example.com/feed.xml"

        val syncing = AppState().reduce(SetFeedSyncing(feedUrl, true))
        assertThat(syncing.feedSync.syncing).isEqualTo(setOf(feedUrl))

        val done = syncing.reduce(SetFeedSyncing(feedUrl, false))
        assertThat(done.feedSync.syncing).isEmpty()
    }

    @Test
    fun setFeedSyncError_setsAndClears() {
        val withError = AppState().reduce(SetFeedSyncError("boom"))
        assertThat(withError.feedSync.lastError).isEqualTo("boom")

        val cleared = withError.reduce(SetFeedSyncError(null))
        assertThat(cleared.feedSync.lastError).isNull()
    }
}
