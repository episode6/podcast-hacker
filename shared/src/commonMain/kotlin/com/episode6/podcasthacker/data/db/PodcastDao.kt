package com.episode6.podcasthacker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun get(feedUrl: String): PodcastEntity?

    @Upsert
    suspend fun upsert(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun delete(feedUrl: String)
}
