package com.episode6.podcasthacker.data.repo

import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.model.toEntity
import com.episode6.podcasthacker.data.network.PodcastFeedFetcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
class FeedRepository(
    private val db: AppDatabase,
    private val feedFetcher: PodcastFeedFetcher,
) {
    /**
     * Fetches the feed and upserts the podcast + episodes, preserving per-episode user
     * state (downloadState, playbackPosition) across re-syncs.
     */
    suspend fun sync(feedUrl: String) {
        val feed = feedFetcher.fetch(feedUrl)
        db.podcastDao().upsert(feed.podcast.toEntity())
        val existing = db.episodeDao().getAllForPodcast(feedUrl).associateBy { it.guid }
        db.episodeDao().upsertAll(
            feed.episodes.map { episode ->
                val current = existing[episode.guid]
                episode.toEntity().copy(
                    downloadState = current?.downloadState ?: DownloadState.NotDownloaded.name,
                    playbackPositionMillis = current?.playbackPositionMillis ?: 0L,
                )
            }
        )
    }
}
