package com.episode6.podcasthacker.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Tolerant conversion of the basic HTML found in podcast show notes to an
 * [AnnotatedString]. Supports p/div/br paragraph breaks, b/strong, i/em, u, li bullets,
 * clickable a-href links, and common entities; every other tag is stripped. Never throws
 * on malformed input.
 */
internal fun basicHtmlToAnnotatedString(html: String, linkColor: Color = Color.Unspecified): AnnotatedString {
    val out = buildAnnotatedString {
        val openTags = ArrayDeque<String>()
        var trailingNewlines = 2 // suppress leading breaks
        var pendingSpace = false
        var i = 0

        fun flushPendingSpace() {
            if (pendingSpace && trailingNewlines == 0) append(' ')
            pendingSpace = false
        }

        fun appendText(c: Char) {
            flushPendingSpace()
            append(c)
            trailingNewlines = 0
        }

        fun appendBreaks(count: Int) {
            pendingSpace = false
            while (trailingNewlines < count) {
                append('\n')
                trailingNewlines++
            }
        }

        fun openTag(name: String, style: SpanStyle) {
            flushPendingSpace() // keep the preceding space outside the span
            pushStyle(style)
            openTags.addLast(name)
        }

        fun closeTag(name: String) {
            // tolerate mis-nesting: pop everything above the matching tag too
            if (name !in openTags) return
            while (openTags.isNotEmpty()) {
                val popped = openTags.removeLast()
                pop()
                if (popped == name) break
            }
        }

        while (i < html.length) {
            val c = html[i]
            when {
                c == '<' -> {
                    val end = html.indexOf('>', startIndex = i)
                    if (end == -1) break // truncated tag: drop the rest
                    val raw = html.substring(i + 1, end).trim()
                    i = end + 1
                    val closing = raw.startsWith("/")
                    val name = raw.removePrefix("/").substringBefore(' ').trimEnd('/').lowercase()
                    when (name) {
                        "b", "strong" ->
                            if (closing) closeTag(name) else openTag(name, SpanStyle(fontWeight = FontWeight.Bold))
                        "i", "em" ->
                            if (closing) closeTag(name) else openTag(name, SpanStyle(fontStyle = FontStyle.Italic))
                        "u" ->
                            if (closing) closeTag(name) else openTag(name, SpanStyle(textDecoration = TextDecoration.Underline))
                        "a" -> if (closing) {
                            if ("a" in openTags) {
                                closeTag("a")
                                pop() // the link annotation under the styles
                            }
                        } else {
                            val href = Regex("""href\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
                                .find(raw)?.groupValues?.get(1).orEmpty()
                            flushPendingSpace()
                            pushLink(LinkAnnotation.Url(href))
                            openTag("a", SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        }
                        "p", "div" -> if (closing) appendBreaks(2) else appendBreaks(if (name == "p") 2 else 1)
                        "br" -> appendBreaks(1)
                        "li" -> if (!closing) {
                            appendBreaks(1)
                            append("• ")
                        }
                        "ul", "ol" -> appendBreaks(1)
                        "script", "style" -> if (!closing) {
                            // skip embedded code entirely
                            val close = html.indexOf("</$name", startIndex = i, ignoreCase = true)
                            i = if (close == -1) html.length else html.indexOf('>', startIndex = close) + 1
                            if (i == 0) i = html.length
                        }
                        // every other tag is stripped
                    }
                }
                c == '&' -> {
                    val semi = html.indexOf(';', startIndex = i)
                    val entity = if (semi != -1 && semi - i <= 8) html.substring(i + 1, semi) else null
                    val decoded = when {
                        entity == null -> null
                        entity == "amp" -> '&'
                        entity == "lt" -> '<'
                        entity == "gt" -> '>'
                        entity == "quot" -> '"'
                        entity == "apos" -> '\''
                        entity == "nbsp" -> ' '
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.drop(2).toIntOrNull(16)?.toChar()
                        entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()
                        else -> null
                    }
                    if (decoded != null) {
                        if (decoded == ' ') pendingSpace = true else appendText(decoded)
                        i = semi + 1
                    } else {
                        appendText('&')
                        i++
                    }
                }
                c.isWhitespace() -> {
                    pendingSpace = true
                    i++
                }
                else -> {
                    appendText(c)
                    i++
                }
            }
        }
    }
    val trimmedLength = out.text.trimEnd('\n').length
    return if (trimmedLength == out.text.length) out else out.subSequence(0, trimmedLength)
}
