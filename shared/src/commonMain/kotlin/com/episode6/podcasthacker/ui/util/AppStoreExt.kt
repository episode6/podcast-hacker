package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.redux.compose.collectAsState
import com.episode6.redux.mapStore

/**
 * Subscribe to a mapped slice of the store's state as a compose state (requires
 * redux >= 1.1.7, where collectAsState seeds with the store's current state;
 * StateOfTest pins the first-frame behavior).
 *
 * The mapper is captured once per remember scope, NOT re-captured on recomposition —
 * so any value it closes over must be passed as a [keys] entry whenever the call site
 * can outlive it (e.g. the mini player watching `downloads[guid]` while playback moves
 * between episodes). Without a key the mapper keeps reading the first composition's
 * captures forever. Call sites whose captures share the enclosing composition's
 * lifetime (screen route params, lazy-list items keyed on the captured id) don't need
 * keys.
 */
@Composable fun <O> AppStore.stateOf(vararg keys: Any?, mapper: AppState.() -> O): State<O> =
    remember(*keys) { mapStore { it.mapper() } }.collectAsState()
