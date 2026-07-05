package com.episode6.podcasthacker.data.model

import com.episode6.podcasthacker.data.db.EpisodeEntity
import com.episode6.podcasthacker.data.db.PodcastEntity
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun Podcast.toEntity(): PodcastEntity = PodcastEntity(
    feedUrl = feedUrl,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    author = author,
)

internal fun PodcastEntity.toDomain(): Podcast = Podcast(
    feedUrl = feedUrl,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    author = author,
)

internal fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    guid = guid,
    feedUrl = feedUrl,
    title = title,
    notes = notes,
    audioUrl = audioUrl,
    pubDateEpochMillis = pubDate?.toEpochMilliseconds(),
    durationSeconds = duration?.inWholeSeconds,
    enclosureBytes = enclosureBytes,
    downloadState = downloadState.name,
    playbackPositionMillis = playbackPosition.inWholeMilliseconds,
)

internal fun EpisodeEntity.toDomain(): Episode = Episode(
    guid = guid,
    feedUrl = feedUrl,
    title = title,
    notes = notes,
    audioUrl = audioUrl,
    pubDate = pubDateEpochMillis?.let { Instant.fromEpochMilliseconds(it) },
    duration = durationSeconds?.seconds,
    enclosureBytes = enclosureBytes,
    downloadState = DownloadState.entries.firstOrNull { it.name == downloadState }
        ?: DownloadState.NotDownloaded,
    playbackPosition = playbackPositionMillis.milliseconds,
)
