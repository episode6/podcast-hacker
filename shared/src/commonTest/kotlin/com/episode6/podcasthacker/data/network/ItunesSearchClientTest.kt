package com.episode6.podcasthacker.data.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private val FIXTURE_JSON = """
{
  "resultCount": 1,
  "results": [
    {
      "wrapperType": "track",
      "kind": "podcast",
      "collectionName": "Test Podcast",
      "artistName": "Test Author",
      "feedUrl": "https://example.com/feed.xml",
      "artworkUrl100": "https://example.com/art100.png",
      "artworkUrl600": "https://example.com/art600.png",
      "trackCount": 42
    }
  ]
}
""".trimIndent()

class ItunesSearchClientTest {

    @Test
    fun searchPodcasts_parsesResults_despiteTextContentType() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = FIXTURE_JSON,
                headers = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"),
            )
        }
        val client = ItunesSearchClient(HttpClient(engine))

        val results = client.searchPodcasts("test")

        assertThat(results).containsExactly(
            ItunesPodcastResult(
                collectionName = "Test Podcast",
                artistName = "Test Author",
                feedUrl = "https://example.com/feed.xml",
                artworkUrl100 = "https://example.com/art100.png",
                artworkUrl600 = "https://example.com/art600.png",
            )
        )
    }

    @Test
    fun searchPodcasts_sendsExpectedQuery() = runTest {
        val engine = MockEngine { _ ->
            respond(content = """{"results":[]}""", headers = headersOf(HttpHeaders.ContentType, "text/javascript"))
        }
        val client = ItunesSearchClient(HttpClient(engine))

        client.searchPodcasts("some show", limit = 7)

        val url = engine.requestHistory.single().url
        assertThat(url.host).isEqualTo("itunes.apple.com")
        assertThat(url.encodedPath).isEqualTo("/search")
        assertThat(url.parameters["media"]).isEqualTo("podcast")
        assertThat(url.parameters["entity"]).isEqualTo("podcast")
        assertThat(url.parameters["term"]).isEqualTo("some show")
        assertThat(url.parameters["limit"]).isEqualTo("7")
    }
}
