package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.redux.compose.collectAsState

// subscribe to a mapped slice of the store's state as a compose state
// (requires redux >= 1.1.7, where collectAsState seeds with the store's current state;
// StateOfTest pins the first-frame behavior)
@Composable fun <O> AppStore.stateOf(mapper: AppState.() -> O): State<O> = collectAsState { it.mapper() }
