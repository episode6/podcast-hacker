package com.episode6.podcasthacker.ui.addpodcast

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.episode6.podcasthacker.data.network.ItunesSearchClient
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffectContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private val SEARCH_JSON = """
{
  "results": [
    {
      "collectionName": "Test Podcast",
      "artistName": "Test Author",
      "feedUrl": "https://example.com/feed.xml",
      "artworkUrl600": "https://example.com/art600.png"
    },
    {
      "collectionName": "No Feed Url — dropped",
      "artistName": "Someone"
    }
  ]
}
""".trimIndent()

class AddPodcastStoreTest {

    private val expectedResult = PodcastSearchResult(
        title = "Test Podcast",
        author = "Test Author",
        feedUrl = "https://example.com/feed.xml",
        artworkUrl = "https://example.com/art600.png",
    )

    private fun searchClient(json: String = SEARCH_JSON): ItunesSearchClient = ItunesSearchClient(
        HttpClient(MockEngine { _ ->
            respond(content = json, headers = headersOf(HttpHeaders.ContentType, "text/javascript"))
        })
    )

    @Test
    fun reduce_appliesActions() {
        var state = AddPodcastState()

        state = state.reduce(SetQuery("swift"))
        state = state.reduce(SetSearching(true))
        state = state.reduce(SetResults(listOf(expectedResult)))
        state = state.reduce(SetSearchError("nope"))

        assertThat(state).isEqualTo(
            AddPodcastState(
                query = "swift",
                searching = true,
                results = listOf(expectedResult),
                error = "nope",
            )
        )
    }

    @Test
    fun queryIsUrl_detectsPastedFeeds() {
        assertThat(AddPodcastState(query = "https://example.com/feed.xml").queryIsUrl).isEqualTo(true)
        assertThat(AddPodcastState(query = "http://example.com/feed.xml").queryIsUrl).isEqualTo(true)
        assertThat(AddPodcastState(query = "some show").queryIsUrl).isEqualTo(false)
    }

    @Test
    fun search_emitsResults_droppingEntriesWithoutFeedUrl() = runTest {
        val actions = searchSideEffect(searchClient()).output(SetQuery("test")).toList()

        assertThat(actions).containsExactly(
            SetSearching(true),
            SetSearchError(null),
            SetResults(listOf(expectedResult)),
            SetSearching(false),
        )
    }

    @Test
    fun shortQuery_clearsResultsWithoutSearching() = runTest {
        val actions = searchSideEffect(searchClient()).output(SetQuery("a")).toList()

        assertThat(actions).containsExactly(SetResults(emptyList()), SetSearching(false))
    }

    @Test
    fun urlQuery_skipsSearch() = runTest {
        val actions = searchSideEffect(searchClient())
            .output(SetQuery("https://example.com/feed.xml"))
            .toList()

        assertThat(actions).containsExactly(SetResults(emptyList()), SetSearching(false))
    }

    @Test
    fun search_dedupesResultsSharingAFeedUrl() = runTest {
        // itunes really does this (e.g. "planet money" returns several entries with the
        // same npr feed); feedUrl is the compose list key so duplicates crash the UI
        val json = """
        {
          "results": [
            {"collectionName": "Planet Money", "feedUrl": "https://feeds.npr.org/510289/podcast.xml"},
            {"collectionName": "Planet Money+", "feedUrl": "https://feeds.npr.org/510289/podcast.xml"}
          ]
        }
        """.trimIndent()

        val actions = searchSideEffect(searchClient(json = json)).output(SetQuery("planet money")).toList()

        val results = actions.filterIsInstance<SetResults>().single().results
        assertThat(results.map { it.feedUrl }).containsExactly("https://feeds.npr.org/510289/podcast.xml")
    }

    @Test
    fun searchFailure_emitsError() = runTest {
        val actions = searchSideEffect(searchClient(json = "not json")).output(SetQuery("test")).toList()

        assertThat(actions[0]).isEqualTo(SetSearching(true))
        assertThat(actions[1]).isEqualTo(SetSearchError(null))
        assertThat(actions[2]).isInstanceOf<SetSearchError>()
        assertThat(actions[3]).isEqualTo(SetSearching(false))
    }

    @Test
    fun rapidQueries_onlyLastOneSearches() = runTest {
        val actions = searchSideEffect(searchClient())
            .output(SetQuery("t"), SetQuery("te"), SetQuery("test"))
            .toList()

        assertThat(actions).containsExactly(
            SetSearching(true),
            SetSearchError(null),
            SetResults(listOf(expectedResult)),
            SetSearching(false),
        )
    }

    private fun com.episode6.redux.sideeffects.SideEffect<AddPodcastState>.output(
        vararg input: Action,
        state: AddPodcastState = AddPodcastState(),
    ): Flow<Action> = with(FakeSideEffectContext(state, input.toList())) { act() }

    private class FakeSideEffectContext(
        private val state: AddPodcastState,
        private val input: List<Action>,
    ) : SideEffectContext<AddPodcastState> {
        override val actions: Flow<Action> get() = input.asFlow()
        override suspend fun currentState(): AddPodcastState = state
    }
}
