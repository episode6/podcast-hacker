package com.episode6.podcasthacker.ui.addpodcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.episode6.podcasthacker.data.network.ItunesSearchClient
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffectMiddleware

/**
 * Hosts the AddPodcast screen's store so it survives config changes on Android; the
 * store's scope dies with the ViewModel when the destination is popped.
 */
class AddPodcastViewModel(searchClient: ItunesSearchClient) : ViewModel() {
    val store: AddPodcastStore = StoreFlow(
        scope = viewModelScope,
        initialValue = AddPodcastState(),
        reducer = AddPodcastState::reduce,
        middlewares = listOf(SideEffectMiddleware(setOf(searchSideEffect(searchClient)))),
    )
}
