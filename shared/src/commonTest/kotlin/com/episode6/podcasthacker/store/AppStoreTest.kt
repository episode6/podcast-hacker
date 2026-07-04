package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.redux.StoreFlow
import com.episode6.redux.testsupport.runStoreTest
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test

class AppStoreTest {

    private fun CoroutineScope.testStore(): AppStore = StoreFlow(
        scope = this,
        initialValue = AppState(),
        reducer = AppState::reduce,
    )

    @Test
    fun dispatchSetNowPlaying_updatesStoreState() = runStoreTest(storeBuilder = { testStore() }) { store ->
        val playing = NowPlayingState(episodeTitle = "test episode", isPlaying = true)

        store.dispatch(SetNowPlaying(playing))

        assertThat(store.state.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun dispatchSetNowPlayingNull_clearsStoreState() = runStoreTest(storeBuilder = { testStore() }) { store ->
        store.dispatch(SetNowPlaying(NowPlayingState(episodeTitle = "test episode")))
        store.dispatch(SetNowPlaying(null))

        assertThat(store.state.nowPlaying).isNull()
    }
}
