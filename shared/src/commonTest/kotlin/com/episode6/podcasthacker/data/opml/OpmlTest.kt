package com.episode6.podcasthacker.data.opml

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

class OpmlTest {

    @Test
    fun parse_flattensCategoriesAndReadsBothQuoteStyles() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>subs</title></head>
              <body>
                <outline text="News">
                  <outline type="rss" text="Feed One" xmlUrl="https://example.com/one.xml"/>
                </outline>
                <outline type='rss' title='Feed Two' xmlUrl='https://example.com/two.xml'/>
              </body>
            </opml>
        """.trimIndent()

        assertThat(parseOpmlFeeds(opml)).containsExactly(
            OpmlFeed(feedUrl = "https://example.com/one.xml", title = "Feed One"),
            OpmlFeed(feedUrl = "https://example.com/two.xml", title = "Feed Two"),
        )
    }

    @Test
    fun parse_ignoresOutlinesWithoutAFeedUrl_andDedupes() {
        val opml = """
            <opml version="1.0"><body>
              <outline text="just a folder"/>
              <outline text="blank url" xmlUrl=""/>
              <outline text="Feed" xmlUrl="https://example.com/feed.xml"/>
              <outline text="Feed again" xmlUrl="https://example.com/feed.xml"/>
            </body></opml>
        """.trimIndent()

        assertThat(parseOpmlFeeds(opml)).containsExactly(
            OpmlFeed(feedUrl = "https://example.com/feed.xml", title = "Feed"),
        )
    }

    @Test
    fun parse_unescapesEntitiesInAttributes() {
        val opml = """<opml><body>
            <outline text="Tom &amp; Jerry&#39;s &quot;Show&quot;" xmlUrl="https://example.com/feed?a=1&amp;b=2"/>
        </body></opml>"""

        assertThat(parseOpmlFeeds(opml)).containsExactly(
            OpmlFeed(feedUrl = "https://example.com/feed?a=1&b=2", title = """Tom & Jerry's "Show""""),
        )
    }

    @Test
    fun parse_prefersTitleOverText_fallsBackToNull() {
        val opml = """<opml><body>
            <outline text="text name" title="title name" xmlUrl="https://example.com/a.xml"/>
            <outline xmlUrl="https://example.com/b.xml"/>
        </body></opml>"""

        assertThat(parseOpmlFeeds(opml)).containsExactly(
            OpmlFeed(feedUrl = "https://example.com/a.xml", title = "title name"),
            OpmlFeed(feedUrl = "https://example.com/b.xml", title = null),
        )
    }

    @Test
    fun parse_garbageInput_yieldsNoFeeds() {
        assertThat(parseOpmlFeeds("not xml at all")).isEmpty()
        assertThat(parseOpmlFeeds("")).isEmpty()
    }

    @Test
    fun document_escapesTitlesAndUrls() {
        val doc = opmlDocument(
            listOf(OpmlFeed(feedUrl = "https://example.com/feed?a=1&b=2", title = """Tom & Jerry's "Show"""")),
        )

        assertThat(doc).contains("""xmlUrl="https://example.com/feed?a=1&amp;b=2"""")
        assertThat(doc).contains("Tom &amp; Jerry&apos;s &quot;Show&quot;")
    }

    @Test
    fun document_untitledFeed_usesUrlAsTitle() {
        val doc = opmlDocument(listOf(OpmlFeed(feedUrl = "https://example.com/feed.xml", title = null)))

        assertThat(doc).contains("""title="https://example.com/feed.xml"""")
    }

    @Test
    fun roundTrip_survivesExportThenImport() {
        val feeds = listOf(
            OpmlFeed(feedUrl = "https://example.com/one.xml", title = "Feed One"),
            OpmlFeed(feedUrl = "https://example.com/two.xml?x=<&>", title = "Feed & Two"),
        )

        assertThat(parseOpmlFeeds(opmlDocument(feeds))).isEqualTo(feeds)
    }
}
