package com.episode6.podcasthacker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE feedUrl = :feedUrl ORDER BY pubDateEpochMillis DESC")
    fun observeForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE feedUrl = :feedUrl")
    suspend fun getAllForPodcast(feedUrl: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun get(guid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    fun observe(guid: String): Flow<EpisodeEntity?>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET playbackPositionMillis = :positionMillis WHERE guid = :guid")
    suspend fun setPlaybackPosition(guid: String, positionMillis: Long)

    @Query("UPDATE episodes SET downloadState = :state WHERE guid = :guid")
    suspend fun setDownloadState(guid: String, state: String)

    @Query("DELETE FROM episodes WHERE feedUrl = :feedUrl")
    suspend fun deleteForPodcast(feedUrl: String)
}
