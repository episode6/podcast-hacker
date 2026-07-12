package com.episode6.podcasthacker.data.backup

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LibraryBackupTest {

    private fun podcast(n: Int) = Podcast(feedUrl = "https://example.com/feed$n.xml", title = "Podcast $n")

    private fun episode(
        guid: String,
        positionMinutes: Int = 0,
        lastPlayedMs: Long? = null,
    ) = Episode(
        guid = guid,
        feedUrl = "https://example.com/feed1.xml",
        title = "Ep $guid",
        playbackPosition = positionMinutes.minutes,
        lastPlayed = lastPlayedMs?.let(Instant::fromEpochMilliseconds),
    )

    @Test
    fun document_includesAllPodcastsButOnlyEpisodesWithProgress() {
        val doc = libraryBackupDocument(
            podcasts = listOf(podcast(1), podcast(2)),
            episodes = listOf(
                episode("unplayed"),
                episode("resumed", positionMinutes = 5),
                episode("finished", lastPlayedMs = 1_700_000_000_000),
            ),
        )

        val parsed = parseLibraryImport(doc)
        assertThat(parsed.podcasts).containsExactly(
            PodcastBackup(feedUrl = "https://example.com/feed1.xml", title = "Podcast 1"),
            PodcastBackup(feedUrl = "https://example.com/feed2.xml", title = "Podcast 2"),
        )
        assertThat(parsed.episodes).containsExactly(
            EpisodeProgress(
                feedUrl = "https://example.com/feed1.xml",
                guid = "resumed",
                positionMs = 5.minutes.inWholeMilliseconds,
            ),
            EpisodeProgress(
                feedUrl = "https://example.com/feed1.xml",
                guid = "finished",
                lastPlayedAtMs = 1_700_000_000_000,
            ),
        )
    }

    @Test
    fun roundTrip_preservesPositionAndLastPlayed() {
        val doc = libraryBackupDocument(
            podcasts = listOf(podcast(1)),
            episodes = listOf(episode("both", positionMinutes = 12, lastPlayedMs = 1_700_000_123_456)),
        )

        assertThat(parseLibraryImport(doc).episodes).containsExactly(
            EpisodeProgress(
                feedUrl = "https://example.com/feed1.xml",
                guid = "both",
                positionMs = 12.minutes.inWholeMilliseconds,
                lastPlayedAtMs = 1_700_000_123_456,
            ),
        )
    }

    @Test
    fun import_sniffsOpml_intoPodcastsWithNoProgress() {
        val opml = """<opml version="2.0"><body>
            <outline text="Folder">
              <outline type="rss" text="Feed One" xmlUrl="https://example.com/one.xml"/>
            </outline>
        </body></opml>"""

        val parsed = parseLibraryImport(opml)

        assertThat(parsed.podcasts)
            .containsExactly(PodcastBackup(feedUrl = "https://example.com/one.xml", title = "Feed One"))
        assertThat(parsed.episodes).isEmpty()
    }

    @Test
    fun import_acceptsProgressOnlyDocuments_fromOlderExports() {
        val doc = """{"version": 1, "episodes": [{"feedUrl": "f", "guid": "g", "positionMs": 100}]}"""

        val parsed = parseLibraryImport(doc)

        assertThat(parsed.podcasts).isEmpty()
        assertThat(parsed.episodes).containsExactly(EpisodeProgress(feedUrl = "f", guid = "g", positionMs = 100))
    }

    @Test
    fun import_toleratesUnknownFields() {
        val doc = """
            {"version": 2, "someFutureField": true,
             "podcasts": [{"feedUrl": "f", "title": "t", "newField": 1}],
             "episodes": [{"feedUrl": "f", "guid": "g", "positionMs": 100, "newField": "x"}]}
        """.trimIndent()

        val parsed = parseLibraryImport(doc)

        assertThat(parsed.podcasts).containsExactly(PodcastBackup(feedUrl = "f", title = "t"))
        assertThat(parsed.episodes).containsExactly(EpisodeProgress(feedUrl = "f", guid = "g", positionMs = 100))
    }

    @Test
    fun import_garbageInput_yieldsEmptyBackup() {
        assertThat(parseLibraryImport("not any format")).isEqualTo(LibraryBackup())
        assertThat(parseLibraryImport("")).isEqualTo(LibraryBackup())
        // valid json that isn't a backup: unknown keys ignored, nothing to import
        assertThat(parseLibraryImport("""{"unrelated": "json"}""")).isEqualTo(LibraryBackup())
    }
}
