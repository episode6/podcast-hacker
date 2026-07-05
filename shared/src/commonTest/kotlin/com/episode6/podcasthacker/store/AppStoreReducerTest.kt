package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.redux.Action
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppStoreReducerTest {

    private val playing = NowPlayingState(episodeGuid = "guid-1", episodeTitle = "test episode", isPlaying = true)

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
    fun setEpisodes_replacesMap_andEpisodeLookupFindsByGuid() {
        val episode = Episode(guid = "g1", feedUrl = "feed", title = "Ep One")

        val result = AppState().reduce(SetEpisodes(mapOf("feed" to listOf(episode))))

        assertThat(result.episodesByFeed).isEqualTo(mapOf("feed" to listOf(episode)))
        assertThat(result.episode("g1")).isEqualTo(episode)
        assertThat(result.episode("missing")).isNull()
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
    fun setPlayerState_mergesMatchingEpisode() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(
                PlayerState(
                    episodeGuid = "guid-1",
                    status = PlayerStatus.Paused,
                    position = 90.seconds,
                    duration = 30.minutes,
                    speed = 1.5f,
                )
            )
        )

        assertThat(result.nowPlaying).isEqualTo(
            playing.copy(
                isPlaying = false,
                position = 90.seconds,
                duration = 30.minutes,
                speed = 1.5f,
            )
        )
    }

    @Test
    fun setPlayerState_preservesAdBoundaries() {
        val boundaries = listOf(AdBoundary(60.seconds, AdBoundary.Source.DaiSlot, AdBoundary.Role.Join))
        val state = AppState(nowPlaying = playing.copy(adBoundaries = boundaries))

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying?.adBoundaries).isEqualTo(boundaries)
    }

    @Test
    fun setPlayerState_ignoresOtherEpisodes() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "other-guid", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun setPlayerState_ignoredWhileNothingPlaying() {
        val result = AppState().reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing))
        )

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun setPlayerState_surfacesErrors() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Error("boom")))
        )

        assertThat(result.nowPlaying?.error).isEqualTo("boom")
    }

    @Test
    fun setFeedSyncError_setsAndClears() {
        val withError = AppState().reduce(SetFeedSyncError("boom"))
        assertThat(withError.feedSync.lastError).isEqualTo("boom")

        val cleared = withError.reduce(SetFeedSyncError(null))
        assertThat(cleared.feedSync.lastError).isNull()
    }
}
