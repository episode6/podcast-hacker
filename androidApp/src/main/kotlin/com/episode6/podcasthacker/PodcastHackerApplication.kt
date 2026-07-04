package com.episode6.podcasthacker

import android.app.Application
import com.episode6.podcasthacker.inject.AppGraph
import com.episode6.podcasthacker.inject.createAppGraph

class PodcastHackerApplication : Application() {
    val appGraph: AppGraph by lazy { createAppGraph(PlatformContext(this)) }
}
