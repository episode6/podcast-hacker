package com.episode6.podcasthacker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * Mini player pinned to the bottom of the root layout: artwork, title, playback progress,
 * and a play/pause toggle; tapping it opens NowPlaying. Hidden while idle (nothing
 * playing) and while the NowPlaying screen is visible.
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
            modifier = Modifier.fillMaxWidth().height(64.dp).testTag("miniPlayerBar"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
        ) {
            Column {
                val progress = current?.duration
                    ?.takeIf { it.isPositive() }
                    ?.let { ((current.position / it).toFloat()).coerceIn(0f, 1f) }
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = current?.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current?.episodeTitle.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        current?.podcastTitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = { store.dispatch(TogglePlayPause) },
                        modifier = Modifier.testTag("miniPlayerPlayPause"),
                    ) {
                        Text(
                            text = if (current?.isPlaying == true) "❚❚" else "▶",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
