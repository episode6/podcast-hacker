package com.episode6.podcasthacker.ui.addpodcast

import com.episode6.podcasthacker.data.network.ItunesPodcastResult
import com.episode6.podcasthacker.data.network.ItunesSearchClient
import com.episode6.redux.Action
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias AddPodcastStore = StoreFlow<AddPodcastState>

data class AddPodcastState(
    val query: String = "",
    val results: List<PodcastSearchResult> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
) {
    /** A pasted feed url gets a direct subscribe row instead of a search. */
    val queryIsUrl: Boolean get() = query.isFeedUrl()
}

private fun String.isFeedUrl(): Boolean = startsWith("http://") || startsWith("https://")

data class PodcastSearchResult(
    val title: String,
    val author: String?,
    val feedUrl: String,
    val artworkUrl: String?,
)

sealed interface AddPodcastAction : Action
data class SetQuery(val query: String) : AddPodcastAction
data class SetSearching(val searching: Boolean) : AddPodcastAction
data class SetResults(val results: List<PodcastSearchResult>) : AddPodcastAction
data class SetSearchError(val message: String?) : AddPodcastAction

internal fun AddPodcastState.reduce(action: Action): AddPodcastState = when (action) {
    is SetQuery       -> copy(query = action.query)
    is SetSearching   -> copy(searching = action.searching)
    is SetResults     -> copy(results = action.results)
    is SetSearchError -> copy(error = action.message)
    else              -> this
}

private val SEARCH_DEBOUNCE: Duration = 400.milliseconds
private const val MIN_QUERY_LENGTH = 2

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun searchSideEffect(searchClient: ItunesSearchClient): SideEffect<AddPodcastState> =
    SideEffect {
        actions.filterIsInstance<SetQuery>()
            .debounce(SEARCH_DEBOUNCE)
            .transformLatest { action ->
                val query = action.query.trim()
                if (query.length < MIN_QUERY_LENGTH || query.isFeedUrl()) {
                    emit(SetResults(emptyList()))
                    emit(SetSearching(false))
                    return@transformLatest
                }
                emit(SetSearching(true))
                emit(SetSearchError(null))
                val result = runCatching { searchClient.searchPodcasts(query) }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                result.fold(
                    onSuccess = { found ->
                        // itunes can return multiple entries pointing at the same feed;
                        // feedUrl is the results list's compose key, so dedupe here
                        emit(SetResults(found.mapNotNull { it.toSearchResult() }.distinctBy { it.feedUrl }))
                    },
                    onFailure = {
                        emit(SetSearchError("Search failed: ${it.message ?: it::class.simpleName}"))
                    },
                )
                emit(SetSearching(false))
            }
    }

private fun ItunesPodcastResult.toSearchResult(): PodcastSearchResult? =
    PodcastSearchResult(
        title = collectionName ?: return null,
        author = artistName,
        feedUrl = feedUrl ?: return null,
        artworkUrl = artworkUrl600 ?: artworkUrl100,
    )
