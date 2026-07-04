package com.episode6.podcasthacker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.episode6.podcasthacker.inject.AppGraph
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.ui.RootUi
import com.episode6.podcasthacker.ui.theme.PodcastHackerTheme

@Composable
fun App(appGraph: AppGraph) {
    PodcastHackerTheme {
        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            RootUi()
        }
    }
}
