package com.episode6.podcasthacker.store

import com.episode6.redux.Action

/**
 * Actions that directly mutate [AppState] in the reducer. All other action types are
 * handled by side effects.
 */
sealed interface UpdateStateAction : Action

data class SetNowPlaying(val nowPlaying: NowPlayingState?) : UpdateStateAction
