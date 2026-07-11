package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.redux.mapStore

/**
 * Subscribe to a mapped slice of the store's state as a compose state.
 *
 * Seeds compose with the store's *current* state, not redux-compose's collectAsState,
 * which seeds with StoreFlow.initialState — the construction-time default. The app store
 * is a singleton that outlives navigation, so with that seed every screen rendered its
 * first frame from an empty AppState and snapped to the real state one frame later. When
 * that flicker changes a lazy layout's item count mid-transition, measure can run against
 * the stale item list and crash (IndexOutOfBoundsException in LazyGrid measure).
 */
@Composable fun <O> AppStore.stateOf(mapper: AppState.() -> O): State<O> {
    val mapped = remember { mapStore { it.mapper() } }
    return mapped.collectAsState(initial = mapped.state)
}
