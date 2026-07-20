package com.episode6.podcasthacker.data.db

import androidx.room.ColumnInfo
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
    /** Null until the episode is first played; drives the Recently Played screen. */
    val lastPlayedEpochMillis: Long? = null,
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
    // rows written before confidence existed migrate to the mid value
    @ColumnInfo(defaultValue = "0.5") val confidence: Float = 0.5f,
)

/**
 * One tacita log line from an episode's most recent download, kept only on snapshot
 * builds and shown at the bottom of Now Playing — the ear-verification aid for tacita's
 * fingerprint/acoustic matches, whose evidence (matched spans, match-pass timing) exists
 * only in these lines.
 */
@Entity(
    tableName = "download_log_lines",
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
data class DownloadLogLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeGuid: String,
    /** Emission order within the download. */
    val seq: Int,
    val line: String,
)
