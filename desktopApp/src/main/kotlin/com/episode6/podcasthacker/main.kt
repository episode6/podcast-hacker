package com.episode6.podcasthacker

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PodcastHacker",
    ) {
        App()
    }
}