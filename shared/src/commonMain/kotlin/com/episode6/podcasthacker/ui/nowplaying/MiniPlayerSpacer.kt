package com.episode6.podcasthacker.ui.nowplaying

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * Trailing spacer for a screen's scrollable content, reserving room to scroll the last
 * element clear of the mini player (which overlays the nav content). Matches the Now
 * Playing sheet's visibility: [MINI_PLAYER_HEIGHT] tall while something is playing,
 * composes nothing otherwise.
 */
@Composable
internal fun MiniPlayerSpacer() {
    val store = LocalAppGraph.current.appStore
    val miniPlayerVisible by store.stateOf { nowPlaying != null }
    if (miniPlayerVisible) Spacer(Modifier.height(MINI_PLAYER_HEIGHT))
}
