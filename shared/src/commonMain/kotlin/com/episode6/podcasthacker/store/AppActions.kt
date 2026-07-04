package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.redux.Action

/**
 * Actions that directly mutate [AppState] in the reducer. All other action types are
 * handled by side effects.
 */
sealed interface UpdateStateAction : Action

data class SetNowPlaying(val nowPlaying: NowPlayingState?) : UpdateStateAction
data class SetSubscriptions(val subscriptions: List<Podcast>) : UpdateStateAction
data class SetFeedSyncing(val feedUrl: String, val syncing: Boolean) : UpdateStateAction
data class SetFeedSyncError(val message: String?) : UpdateStateAction

/**
 * Requests handled by side effects (repo call → result actions).
 */
sealed interface AsyncAction : Action

data class SubscribeToPodcast(val feedUrl: String) : AsyncAction
data class UnsubscribeFromPodcast(val feedUrl: String) : AsyncAction
data class RefreshFeed(val feedUrl: String) : AsyncAction
