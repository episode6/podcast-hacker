package com.episode6.podcasthacker.data.network

import com.episode6.podcasthacker.data.model.PodcastFeed
import com.episode6.podcasthacker.data.model.toPodcastFeed
import com.prof18.rssparser.RssParser
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Fetches feed XML over our own [HttpClient] (mockable in tests) and hands it to
 * [RssParser] for parsing, mapping the result to domain models.
 */
@Inject
@SingleIn(AppScope::class)
class PodcastFeedFetcher(private val httpClient: HttpClient) {
    private val parser = RssParser()

    suspend fun fetch(feedUrl: String): PodcastFeed {
        val xml = httpClient.get(feedUrl).bodyAsText()
        return parser.parse(xml).toPodcastFeed(feedUrl)
    }
}
