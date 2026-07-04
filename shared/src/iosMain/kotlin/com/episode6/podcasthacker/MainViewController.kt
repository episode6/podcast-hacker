package com.episode6.podcasthacker

import androidx.compose.ui.window.ComposeUIViewController
import com.episode6.podcasthacker.inject.createAppGraph

private val appGraph by lazy { createAppGraph(PlatformContext()) }

fun MainViewController() = ComposeUIViewController { App(appGraph) }
