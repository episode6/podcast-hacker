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

@Composable
fun App(appGraph: AppGraph) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(appGraph.httpClient)) }
            .build()
    }
    PodcastHackerTheme {
        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            RootUi()
        }
    }
}
