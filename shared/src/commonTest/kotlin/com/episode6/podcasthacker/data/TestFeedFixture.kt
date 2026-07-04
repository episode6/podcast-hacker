package com.episode6.podcasthacker.data

// Small hand-rolled feed exercising: itunes channel metadata, an episode with a guid +
// HH:MM:SS duration, and an episode with no guid (falls back to the enclosure url) +
// plain-seconds duration. Kept as a string constant because native targets have no
// classpath resources.
internal val TEST_FEED_XML = """
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
  <channel>
    <title>Test Podcast</title>
    <description>A test feed</description>
    <itunes:author>Test Author</itunes:author>
    <itunes:image href="https://example.com/art.png"/>
    <item>
      <title>Episode Two</title>
      <guid>ep-2</guid>
      <description>notes two</description>
      <enclosure url="https://example.com/ep2.mp3" type="audio/mpeg" length="123"/>
      <pubDate>Tue, 02 Jun 2026 10:30:00 GMT</pubDate>
      <itunes:duration>01:02:03</itunes:duration>
    </item>
    <item>
      <title>Episode One</title>
      <description>notes one</description>
      <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="123"/>
      <pubDate>Mon, 01 Jun 2026 09:00:00 GMT</pubDate>
      <itunes:duration>1800</itunes:duration>
    </item>
  </channel>
</rss>
""".trimIndent()
