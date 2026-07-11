package com.episode6.podcasthacker.data.model

import kotlin.time.Duration
import kotlin.time.Instant

data class Podcast(
    val feedUrl: String,
    val title: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val author: String? = null,
)

data class Episode(
    val guid: String,
    val feedUrl: String,
    val title: String,
    val notes: String? = null,
    val audioUrl: String? = null,
    val pubDate: Instant? = null,
    val duration: Duration? = null,
    /** The feed's declared `enclosure length` — lets tacita verify a clean serving. */
    val enclosureBytes: Long? = null,
    val downloadState: DownloadState = DownloadState.NotDownloaded,
    val playbackPosition: Duration = Duration.ZERO,
    /** Null until the episode is first played; drives the Recently Played screen. */
    val lastPlayed: Instant? = null,
)

enum class DownloadState { NotDownloaded, Downloading, Downloaded }

/** A freshly fetched + parsed feed, not yet persisted. */
data class PodcastFeed(
    val podcast: Podcast,
    val episodes: List<Episode>,
)
