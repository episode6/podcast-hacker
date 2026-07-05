package com.episode6.podcasthacker.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.episode6.podcasthacker.App
import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.data.TEST_FEED_XML
import com.episode6.podcasthacker.inject.AppGraph
import com.episode6.podcasthacker.inject.AppGraphOverrides
import com.episode6.podcasthacker.inject.createAppGraph
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test

private const val FEED_URL = "https://example.com/feed.xml"

private val SEARCH_JSON = """
{
  "results": [
    {
      "collectionName": "Test Podcast",
      "artistName": "Test Author",
      "feedUrl": "$FEED_URL",
      "artworkUrl600": "https://example.com/art600.png"
    }
  ]
}
""".trimIndent()

/**
 * Drives the real app UI (production Metro graph, real store/reducer/side effects, real
 * Room on a temp dir) on an offscreen skiko surface, with only the ktor engine faked.
 */
@OptIn(ExperimentalTestApi::class)
class AppUiIntegrationTest {

    private fun testGraph(): AppGraph {
        val tempDir = Files.createTempDirectory("podcasthacker-uitest").toOkioPath()
        // identical bytes for every request, so tacita's ad-diff finds nothing to cut
        val mp3Bytes = ByteArray(16 * 1024) { it.toByte() }
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.host == "itunes.apple.com" -> respond(
                    content = SEARCH_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "text/javascript"),
                )
                request.url.toString() == FEED_URL -> respond(
                    content = TEST_FEED_XML,
                    headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
                )
                request.url.encodedPath.endsWith(".mp3") -> respond(
                    content = mp3Bytes,
                    headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"),
                )
                else -> respondError(HttpStatusCode.NotFound) // artwork etc.
            }
        })
        return createAppGraph(
            context = PlatformContext(),
            overrides = AppGraphOverrides(
                httpClient = client,
                appDirs = AppDirs(dataDir = tempDir / "data", cacheDir = tempDir / "cache"),
            ),
        )
    }

    @Test
    fun subscribeViaSearch_browseToEpisode_playStub() = runComposeUiTest {
        setContent { App(testGraph()) }

        onNodeWithText("No subscriptions yet").assertExists()

        // grid → add podcast → search
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput("test")
        waitUntilExactlyOneExists(hasText("Test Podcast"), timeoutMillis = 5_000)

        // tapping the result subscribes + pops back to the grid, where the tile appears
        onNodeWithText("Test Podcast").performClick()
        waitUntilExactlyOneExists(hasText("Test Podcast"), timeoutMillis = 10_000)

        // podcast detail: header + episodes from the synced feed
        onNodeWithText("Test Podcast").performClick()
        waitUntilExactlyOneExists(hasText("Episode Two"), timeoutMillis = 10_000)
        onNodeWithText("Test Author").assertExists()

        // episode detail: show notes rendered from the feed html
        onNodeWithText("Episode Two").performClick()
        waitUntilExactlyOneExists(hasText("notes two"), timeoutMillis = 5_000)

        // play stub → now playing → back → mini player bar
        onNodeWithText("Play").performClick()
        waitUntilExactlyOneExists(hasText("Pause"), timeoutMillis = 5_000)
        onNodeWithText("← Back").performClick()
        waitUntilExactlyOneExists(hasText("❚❚"), timeoutMillis = 5_000)
    }

    @Test
    fun downloadEpisode_throughRealTacita_thenDelete() = runComposeUiTest {
        setContent { App(testGraph()) }

        // subscribe + navigate to an episode
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitUntilExactlyOneExists(hasText("Subscribe to RSS url"), timeoutMillis = 5_000)
        onNodeWithText("Subscribe to RSS url").performClick()
        waitUntilExactlyOneExists(hasText("Test Podcast"), timeoutMillis = 10_000)
        onNodeWithText("Test Podcast").performClick()
        waitUntilExactlyOneExists(hasText("Episode Two"), timeoutMillis = 10_000)
        onNodeWithText("Episode Two").performClick()
        waitUntilExactlyOneExists(hasText("Download"), timeoutMillis = 5_000)

        // download runs through the real tacita pipeline against MockEngine bytes
        onNodeWithText("Download").performClick()
        waitUntilExactlyOneExists(hasText("Delete Download"), timeoutMillis = 15_000)

        // the episode row now carries the downloaded marker
        onNodeWithText("← Back").performClick()
        waitUntilExactlyOneExists(hasText("downloaded", substring = true), timeoutMillis = 5_000)

        // delete resets to downloadable
        onNodeWithText("Episode Two").performClick()
        waitUntilExactlyOneExists(hasText("Delete Download"), timeoutMillis = 5_000)
        onNodeWithText("Delete Download").performClick()
        waitUntilExactlyOneExists(hasText("Download"), timeoutMillis = 10_000)
    }

    @Test
    fun subscribeViaPastedUrl_unsubscribeViaLongPress() = runComposeUiTest {
        setContent { App(testGraph()) }

        // paste a feed url → direct subscribe row instead of search results
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitUntilExactlyOneExists(hasText("Subscribe to RSS url"), timeoutMillis = 5_000)
        onNodeWithText("Subscribe to RSS url").performClick()
        waitUntilExactlyOneExists(hasText("Test Podcast"), timeoutMillis = 10_000)

        // long-press the tile → dropdown → unsubscribe empties the grid
        onNodeWithText("Test Podcast").performTouchInput { longClick() }
        waitUntilExactlyOneExists(hasText("Unsubscribe"), timeoutMillis = 5_000)
        onNodeWithText("Unsubscribe").performClick()
        waitUntilExactlyOneExists(hasText("No subscriptions yet"), timeoutMillis = 10_000)
    }
}
