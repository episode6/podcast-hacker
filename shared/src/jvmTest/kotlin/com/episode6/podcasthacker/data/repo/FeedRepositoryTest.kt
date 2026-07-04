package com.episode6.podcasthacker.data.repo

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.episode6.podcasthacker.data.TEST_FEED_XML
import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.network.PodcastFeedFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class FeedRepositoryTest {

    private val feedUrl = "https://example.com/feed.xml"

    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val fetcher = PodcastFeedFetcher(
        HttpClient(MockEngine { _ ->
            respond(
                content = TEST_FEED_XML,
                headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
            )
        })
    )

    private val repo = FeedRepository(db, fetcher)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun sync_persistsPodcastAndEpisodes() = runTest {
        repo.sync(feedUrl)

        val podcast = db.podcastDao().get(feedUrl)
        assertThat(podcast).isNotNull()
        assertThat(podcast!!.title).isEqualTo("Test Podcast")

        val episodes = db.episodeDao().getAllForPodcast(feedUrl)
        assertThat(episodes).hasSize(2)
        assertThat(episodes).extracting { it.downloadState }
            .isEqualTo(listOf(DownloadState.NotDownloaded.name, DownloadState.NotDownloaded.name))
    }

    @Test
    fun resync_preservesPerEpisodeUserState() = runTest {
        repo.sync(feedUrl)
        db.episodeDao().setPlaybackPosition("ep-2", 5_000L)

        repo.sync(feedUrl)

        val episode = db.episodeDao().get("ep-2")
        assertThat(episode!!.playbackPositionMillis).isEqualTo(5_000L)
    }

    @Test
    fun observeForPodcast_ordersByPubDateDescending() = runTest {
        repo.sync(feedUrl)

        val episodes = db.episodeDao().observeForPodcast(feedUrl).first()

        assertThat(episodes).extracting { it.guid }
            .isEqualTo(listOf("ep-2", "https://example.com/ep1.mp3"))
    }
}
