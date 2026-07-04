package com.episode6.podcasthacker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val description: String?,
    val artworkUrl: String?,
    val author: String?,
)

@Entity(
    tableName = "episodes",
    indices = [Index("feedUrl")],
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,
    val feedUrl: String,
    val title: String,
    val notes: String?,
    val audioUrl: String?,
    val pubDateEpochMillis: Long?,
    val durationSeconds: Long?,
    val downloadState: String,
    val playbackPositionMillis: Long,
)
