package com.episode6.podcasthacker.inject

/**
 * Implemented by the hosting android Application so system-instantiated components
 * (e.g. [com.episode6.podcasthacker.playback.PlaybackService]) can reach the app graph.
 */
interface AppGraphOwner {
    val appGraph: AppGraph
}
