package com.episode6.podcasthacker.data.progress

import com.episode6.podcasthacker.data.model.Episode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * One episode's listening state in a progress export. OPML only carries feed urls, so
 * resume positions and play history travel in this separate app-specific json document.
 * Episodes are matched on [guid] at import time; [feedUrl] is included for readability
 * and as a fallback disambiguator should it ever be needed.
 */
@Serializable
data class EpisodeProgress(
    val feedUrl: String,
    val guid: String,
    val positionMs: Long = 0,
    val lastPlayedAtMs: Long? = null,
)

@Serializable
private data class EpisodeProgressDocument(
    val version: Int = 1,
    val episodes: List<EpisodeProgress> = emptyList(),
)

/** Ignores unknown keys so future versions can add fields without breaking old builds. */
private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/** Renders the listening state of every episode in [episodes] that has any. */
fun episodeProgressDocument(episodes: List<Episode>): String =
    json.encodeToString(
        EpisodeProgressDocument(
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

/** The entries of a progress document, or empty when the input isn't one. */
fun parseEpisodeProgress(jsonText: String): List<EpisodeProgress> =
    runCatching { json.decodeFromString<EpisodeProgressDocument>(jsonText).episodes }
        .getOrDefault(emptyList())
