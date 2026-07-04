package com.episode6.podcasthacker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * Stub mini-player pinned to the bottom of the root layout. Hidden while idle (nothing
 * "playing") and while the NowPlaying screen is visible.
 */
@Composable
internal fun MiniPlayerBar(navController: NavController, modifier: Modifier = Modifier) {
    val store = LocalAppGraph.current.appStore
    val nowPlaying by store.stateOf { nowPlaying }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onNowPlayingScreen = backStackEntry?.destination?.hasRoute(NowPlayingRoute::class) == true
    val current = nowPlaying
    AnimatedVisibility(
        visible = current != null && !onNowPlayingScreen,
        modifier = modifier,
    ) {
        Surface(
            onClick = { navController.navigate(NowPlayingRoute) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = current?.episodeTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (current?.isPlaying == true) "❚❚" else "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
