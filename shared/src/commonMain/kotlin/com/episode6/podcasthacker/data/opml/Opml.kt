package com.episode6.podcasthacker.data.opml

/** One subscribable feed from an OPML document. */
data class OpmlFeed(val feedUrl: String, val title: String?)

/**
 * Pulls every feed (any `<outline>` carrying an `xmlUrl`) out of an OPML document,
 * ignoring structure — category folders just nest outlines, and flattening them is the
 * standard import behavior. Outlines are attribute-only tags, so this scans them
 * directly instead of pulling in an XML parser dependency; anything malformed simply
 * yields no feeds.
 */
fun parseOpmlFeeds(opmlXml: String): List<OpmlFeed> =
    OUTLINE_TAG.findAll(opmlXml)
        .map { outline ->
            ATTRIBUTE.findAll(outline.value).associate { attr ->
                attr.groupValues[1] to unescapeXml(attr.groupValues[2].ifEmpty { attr.groupValues[3] })
            }
        }
        .mapNotNull { attrs ->
            attrs["xmlUrl"]?.takeIf { it.isNotBlank() }?.let { url ->
                OpmlFeed(feedUrl = url, title = attrs["title"] ?: attrs["text"])
            }
        }
        .distinctBy { it.feedUrl }
        .toList()

/** Renders [feeds] as an OPML 2.0 document (the standard podcast-subscription format). */
fun opmlDocument(feeds: List<OpmlFeed>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<opml version=\"2.0\">\n")
    append("  <head><title>Podcast Hacker subscriptions</title></head>\n")
    append("  <body>\n")
    feeds.forEach { feed ->
        val title = escapeXml(feed.title ?: feed.feedUrl)
        append("    <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"${escapeXml(feed.feedUrl)}\"/>\n")
    }
    append("  </body>\n")
    append("</opml>\n")
}

private val OUTLINE_TAG = Regex("""<outline\b[^>]*>""", RegexOption.IGNORE_CASE)

/** name="double quoted" or name='single quoted' (group 2 or 3 respectively). */
private val ATTRIBUTE = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

private fun escapeXml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun unescapeXml(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        when (val body = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> body
                .removePrefix("#")
                .let { if (it.startsWith("x") || it.startsWith("X")) it.drop(1).toIntOrNull(16) else it.toIntOrNull() }
                ?.let { code -> runCatching { code.toChar().toString() }.getOrNull() }
                ?: match.value // unknown entity: leave it alone
        }
    }
}

private val ENTITY = Regex("""&(#?\w+);""")
