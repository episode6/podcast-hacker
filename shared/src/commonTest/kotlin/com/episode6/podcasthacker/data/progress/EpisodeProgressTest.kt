package com.episode6.podcasthacker.data.progress

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.episode6.podcasthacker.data.model.Episode
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EpisodeProgressTest {

    private fun episode(
        guid: String,
        positionMinutes: Int = 0,
        lastPlayedMs: Long? = null,
    ) = Episode(
        guid = guid,
        feedUrl = "https://example.com/feed.xml",
        title = "Ep $guid",
        playbackPosition = positionMinutes.minutes,
        lastPlayed = lastPlayedMs?.let(Instant::fromEpochMilliseconds),
    )

    @Test
    fun document_onlyIncludesEpisodesWithProgress() {
        val doc = episodeProgressDocument(
            listOf(
                episode("unplayed"),
                episode("resumed", positionMinutes = 5),
                episode("finished", lastPlayedMs = 1_700_000_000_000),
            ),
        )

        assertThat(parseEpisodeProgress(doc)).containsExactly(
            EpisodeProgress(
                feedUrl = "https://example.com/feed.xml",
                guid = "resumed",
                positionMs = 5.minutes.inWholeMilliseconds,
            ),
            EpisodeProgress(
                feedUrl = "https://example.com/feed.xml",
                guid = "finished",
                lastPlayedAtMs = 1_700_000_000_000,
            ),
        )
    }

    @Test
    fun roundTrip_preservesPositionAndLastPlayed() {
        val doc = episodeProgressDocument(
            listOf(episode("both", positionMinutes = 12, lastPlayedMs = 1_700_000_123_456)),
        )

        assertThat(parseEpisodeProgress(doc)).containsExactly(
            EpisodeProgress(
                feedUrl = "https://example.com/feed.xml",
                guid = "both",
                positionMs = 12.minutes.inWholeMilliseconds,
                lastPlayedAtMs = 1_700_000_123_456,
            ),
        )
    }

    @Test
    fun parse_toleratesUnknownFields() {
        val doc = """
            {"version": 2, "someFutureField": true,
             "episodes": [{"feedUrl": "f", "guid": "g", "positionMs": 100, "newField": "x"}]}
        """.trimIndent()

        assertThat(parseEpisodeProgress(doc))
            .containsExactly(EpisodeProgress(feedUrl = "f", guid = "g", positionMs = 100))
    }

    @Test
    fun parse_garbageInput_yieldsNoEntries() {
        assertThat(parseEpisodeProgress("not json")).isEmpty()
        assertThat(parseEpisodeProgress("")).isEmpty()
        assertThat(parseEpisodeProgress("""{"unrelated": "json"}""")).isEmpty()
        // an opml file fed to the wrong importer parses as nothing, not an error
        assertThat(parseEpisodeProgress("<opml version=\"2.0\"></opml>")).isEmpty()
    }
}
