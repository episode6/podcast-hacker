package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import kotlin.time.Duration

data class AppState(
    val nowPlaying: NowPlayingState? = null,
    val subscriptions: List<Podcast> = emptyList(),
    /** All episodes grouped by feedUrl (pubDate desc), fed from Room by one app-lifetime
     * observer — per-screen Room flows proved unreliable (TODO.md Risk 10). */
    val episodesByFeed: Map<String, List<Episode>> = emptyMap(),
    val feedSync: FeedSyncState = FeedSyncState(),
    /** In-flight download status by episode guid; completed/deleted entries are removed
     * (Room's persisted downloadState is the source of truth once a download settles). */
    val downloads: Map<String, EpisodeDownloadStatus> = emptyMap(),
) {
    fun episode(guid: String): Episode? =
        episodesByFeed.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.guid == guid } }
}

sealed interface EpisodeDownloadStatus {
    data object Queued : EpisodeDownloadStatus
    data object Starting : EpisodeDownloadStatus
    data class Downloading(val percentComplete: Float) : EpisodeDownloadStatus
    data object CuttingAds : EpisodeDownloadStatus
    data class Failure(val message: String) : EpisodeDownloadStatus
}

data class NowPlayingState(
    val episodeGuid: String,
    val episodeTitle: String,
    val podcastTitle: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: Duration = Duration.ZERO,
    /** Player-reported duration, falling back to the feed-supplied one until loaded. */
    val duration: Duration? = null,
    val speed: Float = 1f,
    val error: String? = null,
)

data class FeedSyncState(
    val syncing: Set<String> = emptySet(),
    val lastError: String? = null,
) {
    val isSyncing: Boolean get() = syncing.isNotEmpty()
}
