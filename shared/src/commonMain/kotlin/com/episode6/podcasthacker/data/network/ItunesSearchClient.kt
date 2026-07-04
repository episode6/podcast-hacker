package com.episode6.podcasthacker.data.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Searches the iTunes Search API for podcasts. The response is decoded from text manually
 * because apple serves JSON with a `text/javascript` content type.
 */
@Inject
@SingleIn(AppScope::class)
class ItunesSearchClient(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchPodcasts(term: String, limit: Int = 25): List<ItunesPodcastResult> {
        val body = httpClient.get("https://itunes.apple.com/search") {
            parameter("media", "podcast")
            parameter("entity", "podcast")
            parameter("term", term)
            parameter("limit", limit)
        }.bodyAsText()
        return json.decodeFromString<ItunesSearchResponse>(body).results
    }
}

@Serializable
data class ItunesSearchResponse(
    val results: List<ItunesPodcastResult> = emptyList(),
)

@Serializable
data class ItunesPodcastResult(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl100: String? = null,
    val artworkUrl600: String? = null,
)
