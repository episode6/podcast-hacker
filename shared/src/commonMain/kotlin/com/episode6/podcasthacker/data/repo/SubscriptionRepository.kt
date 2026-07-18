package com.episode6.podcasthacker.data.repo

import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.model.toDomain
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
@SingleIn(AppScope::class)
class SubscriptionRepository(
    private val db: AppDatabase,
    private val feedRepository: FeedRepository,
) {
    fun observeSubscriptions(): Flow<List<Podcast>> =
        db.podcastDao().observeAll().map { podcasts -> podcasts.map { it.toDomain() } }

    suspend fun podcast(feedUrl: String): Podcast? = db.podcastDao().get(feedUrl)?.toDomain()

    suspend fun subscribe(feedUrl: String) = feedRepository.sync(feedUrl)

    suspend fun unsubscribe(feedUrl: String) {
        db.episodeDao().deleteForPodcast(feedUrl)
        db.podcastDao().delete(feedUrl)
    }
}
