package com.episode6.podcasthacker.data.model

import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.datetime.format.DateTimeComponents
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun RssChannel.toPodcastFeed(feedUrl: String): PodcastFeed {
    val podcast = Podcast(
        feedUrl = feedUrl,
        title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: feedUrl,
        description = description?.trim() ?: itunesChannelData?.summary?.trim(),
        artworkUrl = image?.url ?: itunesChannelData?.image,
        author = itunesChannelData?.author?.trim(),
    )
    return PodcastFeed(
        podcast = podcast,
        episodes = items.mapNotNull { it.toEpisode(feedUrl) },
    )
}

private fun RssItem.toEpisode(feedUrl: String): Episode? {
    val audioUrl = audio ?: rawEnclosure?.url
    val guid = guid?.trim().takeUnless { it.isNullOrEmpty() } ?: audioUrl ?: return null
    return Episode(
        guid = guid,
        feedUrl = feedUrl,
        title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: guid,
        notes = content ?: description,
        audioUrl = audioUrl,
        pubDate = parseRssDate(pubDate),
        duration = parseItunesDuration(itunesItemData?.duration),
        enclosureBytes = rawEnclosure?.length?.takeIf { it > 0 },
    )
}

/** RSS pubDates are RFC 1123 formatted (e.g. `Mon, 28 Apr 2025 12:00:00 GMT`). */
internal fun parseRssDate(raw: String?): Instant? {
    val trimmed = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return runCatching {
        DateTimeComponents.Formats.RFC_1123.parse(trimmed).toInstantUsingOffset()
    }.getOrNull()
}

/** itunes:duration is either plain seconds, `MM:SS` or `HH:MM:SS`. */
internal fun parseItunesDuration(raw: String?): Duration? {
    val parts = raw?.trim().takeUnless { it.isNullOrEmpty() }?.split(":") ?: return null
    return runCatching {
        when (parts.size) {
            1 -> parts[0].toLong().seconds
            2 -> parts[0].toLong().minutes + parts[1].toLong().seconds
            3 -> parts[0].toLong().hours + parts[1].toLong().minutes + parts[2].toLong().seconds
            else -> null
        }
    }.getOrNull()
}
