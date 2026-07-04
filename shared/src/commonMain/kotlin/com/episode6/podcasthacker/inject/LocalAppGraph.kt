package com.episode6.podcasthacker.inject

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("LocalAppGraph has not been provided")
}
