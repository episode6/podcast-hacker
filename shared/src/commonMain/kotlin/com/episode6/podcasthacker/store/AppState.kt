package com.episode6.podcasthacker.store

data class AppState(
    val nowPlaying: NowPlayingState? = null,
)

data class NowPlayingState(
    val episodeTitle: String,
    val isPlaying: Boolean = false,
)
