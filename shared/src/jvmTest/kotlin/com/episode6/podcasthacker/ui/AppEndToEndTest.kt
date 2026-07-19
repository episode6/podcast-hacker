package com.episode6.podcasthacker.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import assertk.assertThat
import assertk.assertions.isTrue
import com.episode6.podcasthacker.App
import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.data.TEST_FEED_XML
import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.inject.AppGraphOverrides
import com.episode6.podcasthacker.inject.createAppGraph
import com.sun.net.httpserver.HttpServer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test

/**
 * The one true end-to-end test: a JDK HttpServer serves the feed over real sockets and
 * the app runs with its unmodified production http client (okhttp) — only AppDirs is
 * pointed at a temp dir so the test can't touch the real database.
 */
@OptIn(ExperimentalTestApi::class)
class AppEndToEndTest {

    @Test
    fun subscribeOverRealSockets_persistsToDisk() = runComposeUiTest {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/feed.xml") { exchange ->
            val bytes = TEST_FEED_XML.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/rss+xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val feedUrl = "http://${server.address.hostString}:${server.address.port}/feed.xml"
            val tempDir = Files.createTempDirectory("podcasthacker-e2e").toOkioPath()
            val dataDir = tempDir / "data"
            val graph = createAppGraph(
                context = PlatformContext(),
                overrides = AppGraphOverrides(appDirs = AppDirs(dataDir = dataDir, cacheDir = tempDir / "cache")),
            )

            setContent { App(graph) }

            onNodeWithText("Add Podcast", substring = true).performClick()
            onNode(hasSetTextAction()).performTextInput(feedUrl)
            // 30s floor for every wait, matching AppUiIntegrationTest.waitForExactlyOne:
            // cpu starvation on CI runners has blown through shorter waits
            waitUntilExactlyOneExists(hasText("Subscribe to RSS url"), timeoutMillis = 30_000)
            onNodeWithText("Subscribe to RSS url").performClick()

            // tile appears once the feed round-trips through real sockets + Room
            waitUntilExactlyOneExists(hasText("Test Podcast"), timeoutMillis = 30_000)

            // and the database landed on disk in the temp dir
            assertThat(FileSystem.SYSTEM.exists(dataDir / AppDatabase.FILE_NAME)).isTrue()
        } finally {
            server.stop(0)
        }
    }
}
