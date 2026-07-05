package com.episode6.podcasthacker.data.model

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.podcasthacker.data.TEST_FEED_XML
import com.prof18.rssparser.RssParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FeedMappersTest {

    private val feedUrl = "https://example.com/feed.xml"

    private suspend fun parseFixture(): PodcastFeed =
        RssParser().parse(TEST_FEED_XML).toPodcastFeed(feedUrl)

    @Test
    fun mapsChannelMetadata() = runTest {
        val feed = parseFixture()

        assertThat(feed.podcast).isEqualTo(
            Podcast(
                feedUrl = feedUrl,
                title = "Test Podcast",
                description = "A test feed",
                artworkUrl = "https://example.com/art.png",
                author = "Test Author",
            )
        )
    }

    @Test
    fun mapsEpisodes() = runTest {
        val feed = parseFixture()

        assertThat(feed.episodes).hasSize(2)
        assertThat(feed.episodes[0]).isEqualTo(
            Episode(
                guid = "ep-2",
                feedUrl = feedUrl,
                title = "Episode Two",
                notes = "notes two",
                audioUrl = "https://example.com/ep2.mp3",
                pubDate = Instant.parse("2026-06-02T10:30:00Z"),
                duration = 1.hours + 2.minutes + 3.seconds,
                enclosureBytes = 123,
            )
        )
    }

    @Test
    fun episodeWithoutGuid_fallsBackToAudioUrl() = runTest {
        val feed = parseFixture()

        assertThat(feed.episodes[1].guid).isEqualTo("https://example.com/ep1.mp3")
        assertThat(feed.episodes[1].duration).isEqualTo(30.minutes)
    }

    @Test
    fun parseRssDate_handlesRfc1123AndGarbage() {
        assertThat(parseRssDate("Mon, 01 Jun 2026 09:00:00 GMT"))
            .isEqualTo(Instant.parse("2026-06-01T09:00:00Z"))
        assertThat(parseRssDate("not a date")).isNull()
        assertThat(parseRssDate(null)).isNull()
        assertThat(parseRssDate("")).isNull()
    }

    @Test
    fun parseItunesDuration_handlesAllFormats() {
        assertThat(parseItunesDuration("90")).isEqualTo(90.seconds)
        assertThat(parseItunesDuration("12:34")).isEqualTo(12.minutes + 34.seconds)
        assertThat(parseItunesDuration("01:02:03")).isEqualTo(1.hours + 2.minutes + 3.seconds)
        assertThat(parseItunesDuration("bogus")).isNull()
        assertThat(parseItunesDuration(null)).isNull()
        assertThat(parseItunesDuration("")).isNull()
    }
}
