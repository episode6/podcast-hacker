package com.episode6.podcasthacker.data.repo

import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.toDomain
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

@Inject
@SingleIn(AppScope::class)
class EpisodeRepository(private val db: AppDatabase) {
    fun observeEpisodes(feedUrl: String): Flow<List<Episode>> =
        db.episodeDao().observeForPodcast(feedUrl).map { episodes -> episodes.map { it.toDomain() } }

    suspend fun episode(guid: String): Episode? = db.episodeDao().get(guid)?.toDomain()

    fun observeEpisode(guid: String): Flow<Episode?> =
        db.episodeDao().observe(guid).map { it?.toDomain() }

    suspend fun setPlaybackPosition(guid: String, position: Duration) =
        db.episodeDao().setPlaybackPosition(guid, position.inWholeMilliseconds)
}
