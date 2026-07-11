package com.episode6.podcasthacker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.episode6.podcasthacker.inject.AppGraph
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.ui.RootUi
import com.episode6.podcasthacker.ui.theme.PodcastHackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * [openNowPlayingRequests] lets the platform host push the UI to the Now Playing screen
 * (android: tapping the media notification); emissions navigate there on arrival.
 */
@Composable
fun App(appGraph: AppGraph, openNowPlayingRequests: Flow<Unit> = emptyFlow()) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(appGraph.httpClient)) }
            .build()
    }
    PodcastHackerTheme {
        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            RootUi(openNowPlayingRequests)
        }
    }
}
