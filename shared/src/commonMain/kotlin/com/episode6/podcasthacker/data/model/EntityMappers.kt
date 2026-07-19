package com.episode6.podcasthacker.data.model

import com.episode6.podcasthacker.data.db.AdBoundaryCandidateEntity
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
    lastPlayedEpochMillis = lastPlayed?.toEpochMilliseconds(),
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
    lastPlayed = lastPlayedEpochMillis?.let { Instant.fromEpochMilliseconds(it) },
)

// ad boundary source/role are stored as tacita's SCREAMING_SNAKE enum names (the wire
// format); unrecognized names from a future tacita map to Unknown rather than crashing

internal fun AdBoundary.toEntity(episodeGuid: String): AdBoundaryCandidateEntity = AdBoundaryCandidateEntity(
    episodeGuid = episodeGuid,
    timeMs = position.inWholeMilliseconds,
    confidence = confidence,
    source = when (source) {
        AdBoundary.Source.SegmentBoundary -> "SEGMENT_BOUNDARY"
        AdBoundary.Source.DiffCut -> "DIFF_CUT"
        AdBoundary.Source.DaiSlot -> "DAI_SLOT"
        AdBoundary.Source.Id3Chapter -> "ID3_CHAPTER"
        AdBoundary.Source.Fingerprint -> "FINGERPRINT"
        AdBoundary.Source.Unknown -> "UNKNOWN"
    },
    role = when (role) {
        AdBoundary.Role.Start -> "START"
        AdBoundary.Role.End -> "END"
        AdBoundary.Role.Join -> "JOIN"
        AdBoundary.Role.Unknown -> "UNKNOWN"
    },
)

internal fun AdBoundaryCandidateEntity.toDomain(): AdBoundary = AdBoundary(
    position = timeMs.milliseconds,
    confidence = confidence.coerceIn(0f, 1f),
    source = when (source) {
        "SEGMENT_BOUNDARY" -> AdBoundary.Source.SegmentBoundary
        "DIFF_CUT" -> AdBoundary.Source.DiffCut
        "DAI_SLOT" -> AdBoundary.Source.DaiSlot
        "ID3_CHAPTER" -> AdBoundary.Source.Id3Chapter
        "FINGERPRINT" -> AdBoundary.Source.Fingerprint
        else -> AdBoundary.Source.Unknown
    },
    role = when (role) {
        "START" -> AdBoundary.Role.Start
        "END" -> AdBoundary.Role.End
        "JOIN" -> AdBoundary.Role.Join
        else -> AdBoundary.Role.Unknown
    },
)
