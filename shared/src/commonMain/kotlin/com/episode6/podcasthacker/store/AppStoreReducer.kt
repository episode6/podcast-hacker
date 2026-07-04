package com.episode6.podcasthacker.store

import com.episode6.redux.Action
import com.episode6.redux.StoreFlow

typealias AppStore = StoreFlow<AppState>

internal fun AppState.reduce(action: Action): AppState = when (action) {
    is UpdateStateAction -> reduceUpdateStateAction(action)
    else                 -> this
}

private fun AppState.reduceUpdateStateAction(action: UpdateStateAction): AppState = when (action) {
    is SetNowPlaying -> copy(nowPlaying = action.nowPlaying)
}
