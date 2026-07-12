package com.episode6.podcasthacker.data.backup

import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.opml.OpmlFeed
import com.episode6.podcasthacker.data.opml.parseOpmlFeeds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * The app's own full-library json format: everything OPML carries (subscriptions) plus
 * episode listening state, so one file restores a whole device. OPML remains the
 * interop format for other podcast apps; this is the richer app-specific one.
 * [parseLibraryImport] accepts either and normalizes to this shape.
 */
@Serializable
data class LibraryBackup(
    val version: Int = 1,
    val podcasts: List<PodcastBackup> = emptyList(),
    val episodes: List<EpisodeProgress> = emptyList(),
)

@Serializable
data class PodcastBackup(val feedUrl: String, val title: String? = null)

/**
 * One episode's listening state. Episodes are matched on [guid] at import time;
 * [feedUrl] is included for readability and as a fallback disambiguator should it
 * ever be needed.
 */
@Serializable
data class EpisodeProgress(
    val feedUrl: String,
    val guid: String,
    val positionMs: Long = 0,
    val lastPlayedAtMs: Long? = null,
)

/** Ignores unknown keys so future versions can add fields without breaking old builds. */
private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Renders [podcasts] plus the listening state of every episode in [episodes] that has any. */
fun libraryBackupDocument(podcasts: List<Podcast>, episodes: List<Episode>): String =
    json.encodeToString(
        LibraryBackup(
            podcasts = podcasts.map { PodcastBackup(feedUrl = it.feedUrl, title = it.title) },
            episodes = episodes
                .filter { it.playbackPosition > Duration.ZERO || it.lastPlayed != null }
                .map {
                    EpisodeProgress(
                        feedUrl = it.feedUrl,
                        guid = it.guid,
                        positionMs = it.playbackPosition.inWholeMilliseconds,
                        lastPlayedAtMs = it.lastPlayed?.toEpochMilliseconds(),
                    )
                },
        ),
    )

/**
 * Sniffs an imported file: a json library backup (including progress-only documents
 * from older exports) is decoded directly, anything else falls back to OPML. Content
 * neither format recognizes just yields an empty backup — nothing to import.
 */
fun parseLibraryImport(text: String): LibraryBackup =
    runCatching { json.decodeFromString<LibraryBackup>(text) }
        .getOrElse {
            LibraryBackup(podcasts = parseOpmlFeeds(text).map { it.toPodcastBackup() })
        }

private fun OpmlFeed.toPodcastBackup() = PodcastBackup(feedUrl = feedUrl, title = title)
