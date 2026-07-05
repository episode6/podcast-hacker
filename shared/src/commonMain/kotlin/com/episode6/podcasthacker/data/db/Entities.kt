package com.episode6.podcasthacker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
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
    val enclosureBytes: Long?,
    val downloadState: String,
    val playbackPositionMillis: Long,
)

@Entity(
    tableName = "ad_boundary_candidates",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["guid"],
            childColumns = ["episodeGuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("episodeGuid")],
)
data class AdBoundaryCandidateEntity(
    // autogen id rather than a composite key: two candidates can share time+role with
    // different sources
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeGuid: String,
    val timeMs: Long,
    val source: String,
    val role: String,
)
