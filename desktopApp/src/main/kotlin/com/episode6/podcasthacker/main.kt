package com.episode6.podcasthacker

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.episode6.podcasthacker.inject.createAppGraph

fun main() {
    val appGraph = createAppGraph(PlatformContext())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PodcastHacker",
        ) {
            App(appGraph)
        }
    }
}
