package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /**
     * Pins the keyed overload: the mapper is only captured once per remember scope, so a
     * call site whose captured value changes in place (the mini player's episode guid
     * when playback moves to another episode) must pass that value as a key to get a
     * fresh mapper. Regression test for the mini bar showing the previous (deleted)
     * episode's download button after switching episodes.
     */
    @Test
    fun keyChange_remapsWithFreshCapture() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store: AppStore = StoreFlow(
                scope = scope,
                initialValue = AppState(),
                reducer = AppState::reduce,
            )
            store.dispatch(
                SetNowPlaying(NowPlayingState(episodeGuid = "guid-1", episodeTitle = "first"))
            )

            var guid by mutableStateOf("guid-1")
            var lastSeen: String? = null
            setContent {
                // mimics production call sites: the mapper closes over an immutable
                // local, and the key is what invalidates the capture
                val g = guid
                val title by store.stateOf(g) {
                    nowPlaying?.takeIf { it.episodeGuid == g }?.episodeTitle
                }
                lastSeen = title
            }
            waitForIdle()
            assertThat(lastSeen).isEqualTo("first")

            store.dispatch(
                SetNowPlaying(NowPlayingState(episodeGuid = "guid-2", episodeTitle = "second"))
            )
            guid = "guid-2"
            waitForIdle()

            assertThat(lastSeen).isEqualTo("second")
        } finally {
            scope.cancel()
        }
    }
}
