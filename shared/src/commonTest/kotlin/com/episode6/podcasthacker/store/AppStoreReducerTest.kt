package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
}
