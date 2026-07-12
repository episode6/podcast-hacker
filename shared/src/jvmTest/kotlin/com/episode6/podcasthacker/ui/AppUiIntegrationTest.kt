package com.episode6.podcasthacker.ui

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.episode6.podcasthacker.App
import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.data.TEST_FEED_XML
import com.episode6.podcasthacker.inject.AppGraph
import com.episode6.podcasthacker.inject.AppGraphOverrides
import com.episode6.podcasthacker.inject.createAppGraph
import com.episode6.podcasthacker.playback.PlaybackMetadata
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.podcasthacker.playback.PodcastPlayer
import io.mockk.every
import io.mockk.mockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

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

    /**
     * Stateful mockk player: commands mutate a PlayerState flow the way a real media
     * engine would, so the full redux loop (side effects, reducer merge, ui state) runs
     * without initializing a real playback engine (JavaFX needs a display). Duration args
     * are never read inside the answers — mockk hands over a value class's raw Long.
     */
    private fun fakePlayer(): PodcastPlayer {
        val state = MutableStateFlow(PlayerState())
        return mockk(relaxUnitFun = true) {
            every { this@mockk.state } returns state
            every { load(any(), any(), any(), any()) } answers {
                val metadata = arg<PlaybackMetadata>(1)
                state.value = PlayerState(
                    episodeGuid = metadata.episodeGuid,
                    status = PlayerStatus.Playing,
                    duration = 30.minutes,
                    speed = state.value.speed,
                )
            }
            every { play() } answers { state.update { it.copy(status = PlayerStatus.Playing) } }
            every { pause() } answers { state.update { it.copy(status = PlayerStatus.Paused) } }
            every { setSpeed(any()) } answers { state.update { it.copy(speed = arg(0)) } }
            every { stop() } answers { state.update { it.copy(status = PlayerStatus.Idle) } }
        }
    }

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
                podcastPlayer = fakePlayer(),
            ),
        )
    }

    @Test
    fun subscribeViaSearch_browseToEpisode() = runComposeUiTest {
        setContent { App(testGraph()) }

        onNodeWithText("No subscriptions yet").assertExists()

        // grid → add podcast → search
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput("test")
        waitForExactlyOne(hasText("Test Podcast"))

        // tapping the result subscribes + pops back to the grid, where the tile appears
        onNodeWithText("Test Podcast").performClick()
        waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)

        // podcast detail: header + episodes from the synced feed
        onNodeWithText("Test Podcast").performClick()
        waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        onNodeWithText("Test Author").assertExists()

        // episode detail: show notes rendered from the feed html
        onNodeWithText("Episode Two").performClick()
        waitForExactlyOne(hasText("notes two"))
    }

    @Test
    fun downloadEpisode_throughRealTacita_playThenDelete() = runComposeUiTest {
        setContent { App(testGraph()) }

        // subscribe + navigate to an episode
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitForExactlyOne(hasText("Subscribe to RSS url"))
        onNodeWithText("Subscribe to RSS url").performClick()
        waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)
        onNodeWithText("Test Podcast").performClick()
        waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        onNodeWithText("Episode Two").performClick()
        waitForExactlyOne(hasText("Download"))

        // download runs through the real tacita pipeline against MockEngine bytes
        onNodeWithText("Download").performClick()
        waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 30_000)

        // play the downloaded episode → NowPlaying with live transport controls.
        // matchers are scoped by tag (text merges into the button node): the mini player
        // bar can briefly coexist with the NowPlaying screen during navigation
        // animations, so bare ❚❚/▶ text can transiently match two nodes
        val playingOnNowPlaying = hasTestTag("playPauseButton") and hasText("❚❚")
        val pausedOnNowPlaying = hasTestTag("playPauseButton") and hasText("▶")
        val pausedOnMiniBar = hasTestTag("miniPlayerPlayPause") and hasText("▶")
        onNodeWithText("Play").performClick()
        waitForExactlyOne(playingOnNowPlaying, timeoutMillis = 10_000)
        onNodeWithText("↺ 15").assertExists()
        onNode(playingOnNowPlaying).performClick() // pause
        waitForExactlyOne(pausedOnNowPlaying, timeoutMillis = 10_000)

        // back to episode detail: the mini player bar carries the paused state; tapping
        // it returns to NowPlaying, where Stop clears playback + hides the bar
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(pausedOnMiniBar, timeoutMillis = 10_000)
        onNodeWithTag("miniPlayerBar").performClick()
        waitForExactlyOne(hasText("Stop"), timeoutMillis = 10_000)
        // the boundary-filter slider pushed Stop below the fold at this window size
        onNodeWithText("Stop").performScrollTo().performClick()
        waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 10_000)

        // the episode row now carries the downloaded marker
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(hasText("downloaded", substring = true))

        // delete resets to downloadable
        onNodeWithText("Episode Two").performClick()
        waitForExactlyOne(hasText("Delete Download"))
        onNodeWithText("Delete Download").performClick()
        waitForExactlyOne(hasText("Download"), timeoutMillis = 10_000)
    }

    @Test
    fun recentlyPlayed_listsPlayedEpisode_trashDeletesFileButKeepsEntry() = runComposeUiTest {
        setContent { App(testGraph()) }

        // empty state before anything has played
        onNodeWithText("Recently Played", substring = true).performClick()
        waitForExactlyOne(hasText("Nothing played yet"))
        onNode(hasContentDescription("Back")).performClick()

        // subscribe, download an episode, and play it
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitForExactlyOne(hasText("Subscribe to RSS url"))
        onNodeWithText("Subscribe to RSS url").performClick()
        waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)
        onNodeWithText("Test Podcast").performClick()
        waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        onNodeWithText("Episode Two").performClick()
        waitForExactlyOne(hasText("Download"))
        onNodeWithText("Download").performClick()
        waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 30_000)
        onNodeWithText("Play").performClick()
        waitForExactlyOne(hasTestTag("playPauseButton") and hasText("❚❚"), timeoutMillis = 10_000)
        // stopping pops NowPlaying back to episode detail
        onNodeWithText("Stop").performScrollTo().performClick()
        waitForExactlyOne(hasText("Delete Download"), timeoutMillis = 10_000)

        // back on the grid, Recently Played now lists the episode with live actions
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(hasText("Recently Played", substring = true), timeoutMillis = 10_000)
        onNodeWithText("Recently Played", substring = true).performClick()
        waitForExactlyOne(hasText("Episode Two"), timeoutMillis = 10_000)

        // trash deletes the file: the row stays (play history kept) but both actions
        // grey out until a re-download
        onNode(hasContentDescription("Delete file")).performClick()
        waitForExactlyOne(hasContentDescription("Delete file") and isNotEnabled(), timeoutMillis = 10_000)
        onNode(hasContentDescription("Resume") and isNotEnabled()).assertExists()
        onNodeWithText("Episode Two").assertExists()
    }

    @Test
    fun subscribeViaPastedUrl_unsubscribeViaLongPress() = runComposeUiTest {
        setContent { App(testGraph()) }

        // paste a feed url → direct subscribe row instead of search results
        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitForExactlyOne(hasText("Subscribe to RSS url"))
        onNodeWithText("Subscribe to RSS url").performClick()
        waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)

        // long-press the tile → dropdown → unsubscribe empties the grid
        onNodeWithText("Test Podcast").performTouchInput { longClick() }
        waitForExactlyOne(hasText("Unsubscribe"))
        onNodeWithText("Unsubscribe").performClick()
        waitForExactlyOne(hasText("No subscriptions yet"), timeoutMillis = 10_000)
    }

    /** Regression: the root grid used to keep a (dead) back button after popping back
     * to it — previousBackStackEntry was read mid-pop, while the popped entry was still
     * on the stack, and nothing ever re-read it. */
    @Test
    fun backButton_disappearsAfterPoppingBackToGrid() = runComposeUiTest {
        setContent { App(testGraph()) }

        // the root grid never shows a back button
        onNode(hasContentDescription("Back")).assertDoesNotExist()

        onNodeWithText("Recently Played", substring = true).performClick()
        waitForExactlyOne(hasText("Nothing played yet"))
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(hasText("No subscriptions yet"))
        waitUntilDoesNotExist(hasContentDescription("Back"), timeoutMillis = 10_000)
    }

    // the import/export menu tests never click Import or a format item: that would
    // open a real (blocking) AWT FileDialog, which a headless test can't drive

    @Test
    fun gridOverflowMenu_emptyLibrary_offersImportButNotExport() = runComposeUiTest {
        setContent { App(testGraph()) }

        onNode(hasContentDescription("More options")).performClick()
        waitForExactlyOne(hasText("Import") and isEnabled())
        onNode(hasText("Export") and isNotEnabled()).assertExists() // nothing to export yet
    }

    @Test
    fun gridOverflowMenu_withSubscriptions_exportOpensFormatSubmenu() = runComposeUiTest {
        setContent { App(testGraph()) }

        onNodeWithText("Add Podcast", substring = true).performClick()
        onNode(hasSetTextAction()).performTextInput(FEED_URL)
        waitForExactlyOne(hasText("Subscribe to RSS url"))
        onNodeWithText("Subscribe to RSS url").performClick()
        waitForExactlyOne(hasText("Test Podcast"), timeoutMillis = 10_000)

        onNode(hasContentDescription("More options")).performClick()
        waitForExactlyOne(hasText("Export") and isEnabled())
        onNodeWithText("Import").assertExists()

        // choosing Export swaps the menu content for the format picker
        onNodeWithText("Export").performClick()
        waitForExactlyOne(hasText("OPML"))
        onNodeWithText("JSON").assertExists()
        onNodeWithText("Import").assertDoesNotExist()
    }

    @Test
    fun gridOverflowMenu_opensLicenseNotices() = runComposeUiTest {
        setContent { App(testGraph()) }

        onNode(hasContentDescription("More options")).performClick()
        waitForExactlyOne(hasText("Third-party license notices"))
        onNodeWithText("Third-party license notices").performClick()

        // the licenses screen renders the embedded THIRD_PARTY_LICENSES.md
        waitForExactlyOne(hasText("License notices"))
        onNodeWithText("libvlc (VLC media engine)", substring = true).assertExists()
        onNode(hasContentDescription("Back")).performClick()
        waitForExactlyOne(hasText("No subscriptions yet"))
    }

    /**
     * [waitUntilExactlyOneExists] but the failure says what the ui actually showed.
     * The 10s default is the floor for every wait: CI runs these tests concurrently with
     * installer packaging on 2-core runners, and the resulting cpu starvation blew
     * through a 5s wait at least once (flaked on windows-latest, 2026-07-11).
     */
    private fun ComposeUiTest.waitForExactlyOne(matcher: SemanticsMatcher, timeoutMillis: Long = 10_000) {
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
