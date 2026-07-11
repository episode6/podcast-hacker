package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.store.reduce
import com.episode6.redux.StoreFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test

/**
 * Pins [stateOf]'s first-frame behavior: compose must be seeded with the store's *current*
 * state, never StoreFlow.initialState (the construction-time default). Before redux 1.1.7,
 * redux-compose's collectAsState seeded with initialState, so every screen's first frame
 * rendered from an empty AppState before snapping to the real state. That flicker changes
 * lazy layouts' item counts mid-navigation and can crash measure (flaky
 * IndexOutOfBoundsException in AppUiIntegrationTest's grid navigation).
 */
@OptIn(ExperimentalTestApi::class)
class StateOfTest {

    @Test
    fun firstFrame_reflectsCurrentStoreState_notInitialState() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store: AppStore = StoreFlow(
                scope = scope,
                initialValue = AppState(),
                reducer = AppState::reduce,
            )
            val playing = NowPlayingState(episodeGuid = "guid-1", episodeTitle = "test episode")
            store.dispatch(SetNowPlaying(playing))
            check(store.state.nowPlaying == playing) { "dispatch didn't apply synchronously" }

            val framesSeen = mutableListOf<NowPlayingState?>()
            setContent {
                val nowPlaying by store.stateOf { nowPlaying }
                framesSeen += nowPlaying
            }
            waitForIdle()

            assertThat(framesSeen.first()).isEqualTo(playing)
        } finally {
            scope.cancel()
        }
    }
}
