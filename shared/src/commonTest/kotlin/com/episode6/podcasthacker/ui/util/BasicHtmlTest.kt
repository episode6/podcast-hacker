package com.episode6.podcasthacker.ui.util

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test

class BasicHtmlTest {

    @Test
    fun plainText_passesThrough() {
        assertThat(basicHtmlToAnnotatedString("hello world").text).isEqualTo("hello world")
    }

    @Test
    fun paragraphsAndBreaks_becomeNewlines() {
        val result = basicHtmlToAnnotatedString("<p>one</p><p>two</p>three<br>four")

        assertThat(result.text).isEqualTo("one\n\ntwo\n\nthree\nfour")
    }

    @Test
    fun leadingAndTrailingBreaks_areTrimmed() {
        val result = basicHtmlToAnnotatedString("<p></p><br><p>content</p><br><br>")

        assertThat(result.text).isEqualTo("content")
    }

    @Test
    fun whitespaceRuns_collapse() {
        val result = basicHtmlToAnnotatedString("one\n   two\t three")

        assertThat(result.text).isEqualTo("one two three")
    }

    @Test
    fun boldAndItalic_produceSpans() {
        val result = basicHtmlToAnnotatedString("plain <b>bold</b> and <em>italic</em>")

        assertThat(result.text).isEqualTo("plain bold and italic")
        val bold = result.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertThat(result.text.substring(bold.start, bold.end)).isEqualTo("bold")
        val italic = result.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertThat(result.text.substring(italic.start, italic.end)).isEqualTo("italic")
    }

    @Test
    fun links_becomeUrlAnnotations() {
        val result = basicHtmlToAnnotatedString("""see <a href="https://example.com">this</a>""")

        assertThat(result.text).isEqualTo("see this")
        val link = result.getLinkAnnotations(0, result.text.length).single()
        assertThat((link.item as LinkAnnotation.Url).url).isEqualTo("https://example.com")
        assertThat(result.text.substring(link.start, link.end)).isEqualTo("this")
    }

    @Test
    fun listItems_becomeBullets() {
        val result = basicHtmlToAnnotatedString("intro<ul><li>one</li><li>two</li></ul>")

        assertThat(result.text).isEqualTo("intro\n• one\n• two")
    }

    @Test
    fun entities_decode() {
        val result = basicHtmlToAnnotatedString("a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39; &#x41;")

        assertThat(result.text).isEqualTo("a & b <c> \"d\" 'e' A")
    }

    @Test
    fun unknownTagsAndScripts_areStripped() {
        val result = basicHtmlToAnnotatedString(
            """<span class="x">text</span><script>alert("nope")</script><img src="y"> end"""
        )

        assertThat(result.text).isEqualTo("text end")
    }

    @Test
    fun malformedHtml_neverThrows() {
        val inputs = listOf(
            "<b>unclosed bold",
            "</i>stray close",
            "<a href=>empty</a>",
            "truncated <b",
            "<p><i>mis</p>nested</i>",
            "&unknown; &#xZZ; &",
        )
        inputs.forEach { basicHtmlToAnnotatedString(it) }
    }

    @Test
    fun misnestedTags_stillCloseStyles() {
        val result = basicHtmlToAnnotatedString("<b><i>both</b>after</i>")

        assertThat(result.text).isEqualTo("bothafter")
        val bold = result.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertThat(result.text.substring(bold.start, bold.end)).isEqualTo("both")
    }

    @Test
    fun realisticShowNotes_renderReasonably() {
        val result = basicHtmlToAnnotatedString(
            """
            <p>In this episode we discuss <strong>testing</strong>.</p>
            <p>Links:</p>
            <ul>
              <li><a href="https://example.com/1">First link</a></li>
              <li><a href="https://example.com/2">Second link</a></li>
            </ul>
            """.trimIndent()
        )

        assertThat(result.text).isEqualTo(
            "In this episode we discuss testing.\n\nLinks:\n\n• First link\n• Second link"
        )
        assertThat(
            result.getLinkAnnotations(0, result.text.length)
                .map { (it.item as LinkAnnotation.Url).url }
        ).containsExactly("https://example.com/1", "https://example.com/2")
    }
}
