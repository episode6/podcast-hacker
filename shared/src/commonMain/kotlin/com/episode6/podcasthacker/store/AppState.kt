package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Podcast

data class AppState(
    val nowPlaying: NowPlayingState? = null,
    val subscriptions: List<Podcast> = emptyList(),
    val feedSync: FeedSyncState = FeedSyncState(),
)

data class NowPlayingState(
    val episodeTitle: String,
    val isPlaying: Boolean = false,
)

data class FeedSyncState(
    val syncing: Set<String> = emptySet(),
    val lastError: String? = null,
) {
    val isSyncing: Boolean get() = syncing.isNotEmpty()
}
