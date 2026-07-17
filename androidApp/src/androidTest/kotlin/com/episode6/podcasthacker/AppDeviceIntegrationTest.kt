package com.episode6.podcasthacker

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Rule
import java.io.File
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

// mirrors shared/src/commonTest TestFeedFixture (internal to that module); the enclosure
// urls end in .mp3 so the MockEngine serves the real asset audio for them
private val FEED_XML = """
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

/**
 * Drives the real app UI on device: production Metro graph, real store/reducer/side
 * effects, real Room on a temp dir, and — unlike the desktop AppUiIntegrationTest — the
 * real playback stack (MediaControllerPodcastPlayer → PlaybackService → ExoPlayer) and
 * the real DownloadScheduler (UIDT job / dataSync service). Only the ktor engine is
 * faked; downloads serve a real 30s mp3 asset so ExoPlayer can actually play the file
 * tacita produces.
 */
@OptIn(ExperimentalTestApi::class)
class AppDeviceIntegrationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun testGraph(): AppGraph {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // identical bytes for every request, so tacita's ad-diff finds nothing to cut and
        // the downloaded file stays a valid mp3 for the real player
        val mp3Bytes = instrumentation.context.assets.open("episode.mp3").use { it.readBytes() }
        val tempDir = File(instrumentation.targetContext.cacheDir, "device-test-${System.nanoTime()}")
            .apply { mkdirs() }
            .toOkioPath()
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.host == "itunes.apple.com" -> respond(
                    content = SEARCH_JSON,
                    headers = headersOf(HttpHeaders.ContentType, "text/javascript"),
                )
                request.url.toString() == FEED_URL -> respond(
                    content = FEED_XML,
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
            context = PlatformContext(instrumentation.targetContext),
            overrides = AppGraphOverrides(
                httpClient = client,
                appDirs = AppDirs(dataDir = tempDir / "data", cacheDir = tempDir / "cache"),
                // no podcastPlayer override: commands run through the real media stack
            ),
        )
    }

    @Test
    fun subscribeViaSearch_browseToEpisode() {
        compose.setContent { App(testGraph()) }

        compose.onNodeWithText("No subscriptions yet").assertExists()

        // grid → add podcast → search
        compose.onNodeWithText("Add Podcast", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("test")
        compose.waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 5_000)

        // tapping the result subscribes + pops back to the grid, where the tile appears
        compose.onNodeWithText("Test Podcast").performClick()
        compose.waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)

        // podcast detail: header + episodes from the synced feed
        compose.onNodeWithText("Test Podcast").performClick()
        compose.waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        compose.onNodeWithText("Test Author").assertExists()

        // episode detail: show notes rendered from the feed html
        compose.onNodeWithText("Episode Two").performClick()
        compose.waitForExactlyOne(hasText("notes two"), timeoutMillis = 5_000)
    }

    @Test
    fun downloadEpisode_throughRealTacita_playThroughRealMediaService() {
        compose.setContent { App(testGraph()) }

        // subscribe + navigate to an episode
        compose.onNodeWithText("Add Podcast", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(FEED_URL)
        compose.waitForExactlyOne(hasText("Subscribe to RSS url"), timeoutMillis = 5_000)
        compose.onNodeWithText("Subscribe to RSS url").performClick()
        compose.waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)
        compose.onNodeWithText("Test Podcast").performClick()
        compose.waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        compose.onNodeWithText("Episode Two").performClick()
        compose.waitForExactlyOne(hasText("Download"), timeoutMillis = 5_000)

        // download runs through the real tacita pipeline (and the real UIDT job /
        // dataSync service pins the process) against MockEngine's mp3 asset bytes
        compose.onNodeWithText("Download").performClick()
        compose.waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 60_000)

        // play the download → NowPlaying driven by the real MediaController session.
        // matchers are scoped by tag (the icon's contentDescription merges into the
        // button node): the mini player bar can briefly coexist with the NowPlaying
        // screen during navigation animations, so a bare Pause/Play description can
        // transiently match two nodes
        val playingOnNowPlaying = hasTestTag("playPauseButton") and hasContentDescription("Pause")
        val pausedOnNowPlaying = hasTestTag("playPauseButton") and hasContentDescription("Play")
        val pausedOnMiniBar = hasTestTag("miniPlayerPlayPause") and hasContentDescription("Play")
        compose.onNodeWithText("Play").performClick()
        // covers MediaController connect + ExoPlayer prepare + actual audio start
        compose.waitForExactlyOne(playingOnNowPlaying, timeoutMillis = 30_000)
        compose.onNode(hasContentDescription("Back 15 seconds")).assertExists()
        compose.onNode(playingOnNowPlaying).performClick() // pause
        compose.waitForExactlyOne(pausedOnNowPlaying, timeoutMillis = 10_000)

        // back to episode detail: the mini player bar carries the paused state; tapping
        // it returns to NowPlaying, where Stop clears playback + hides the bar
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitForExactlyOne(pausedOnMiniBar, timeoutMillis = 10_000)
        compose.onNodeWithTag("miniPlayerBar").performClick()
        compose.waitForExactlyOne(hasText("Stop"), timeoutMillis = 10_000)
        // NowPlaying is a scrollable column; on short screens Stop composes below the
        // fold, where a bare performClick taps thin air
        compose.onNodeWithText("Stop").performScrollTo().performClick()
        compose.waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 10_000)

        // delete resets to downloadable
        compose.onNodeWithText("Delete Download").performClick()
        compose.waitForExactlyOne(hasText("Download"), timeoutMillis = 10_000)
    }

    @Test
    fun subscribeViaPastedUrl_unsubscribeViaLongPress() {
        compose.setContent { App(testGraph()) }

        // paste a feed url → direct subscribe row instead of search results
        compose.onNodeWithText("Add Podcast", substring = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(FEED_URL)
        compose.waitForExactlyOne(hasText("Subscribe to RSS url"), timeoutMillis = 5_000)
        compose.onNodeWithText("Subscribe to RSS url").performClick()
        compose.waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)

        // long-press the tile → dropdown → unsubscribe empties the grid
        compose.onNodeWithText("Test Podcast").performTouchInput { longClick() }
        compose.waitForExactlyOne(hasText("Unsubscribe"), timeoutMillis = 5_000)
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.waitForExactlyOne(hasText("No subscriptions yet"), timeoutMillis = 10_000)
    }

    /** [waitUntilExactlyOneExists] but the failure says what the ui actually showed. */
    private fun ComposeTestRule.waitForExactlyOne(matcher: SemanticsMatcher, timeoutMillis: Long = 10_000) {
        try {
            waitUntilExactlyOneExists(matcher, timeoutMillis)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for exactly one: ${matcher.description}\n--- semantics tree ---\n" +
                    runCatching { onRoot().printToString() }.getOrElse { "unavailable: $it" },
                e,
            )
        }
    }
}
