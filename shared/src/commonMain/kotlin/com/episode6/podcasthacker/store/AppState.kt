package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Podcast

data class AppState(
    val nowPlaying: NowPlayingState? = null,
    val subscriptions: List<Podcast> = emptyList(),
    val feedSync: FeedSyncState = FeedSyncState(),
    /** In-flight download status by episode guid; completed/deleted entries are removed
     * (Room's persisted downloadState is the source of truth once a download settles). */
    val downloads: Map<String, EpisodeDownloadStatus> = emptyMap(),
)

sealed interface EpisodeDownloadStatus {
    data object Queued : EpisodeDownloadStatus
    data object Starting : EpisodeDownloadStatus
    data class Downloading(val percentComplete: Float) : EpisodeDownloadStatus
    data object CuttingAds : EpisodeDownloadStatus
    data class Failure(val message: String) : EpisodeDownloadStatus
}

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
