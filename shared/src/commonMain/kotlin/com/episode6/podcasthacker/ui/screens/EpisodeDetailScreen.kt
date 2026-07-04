package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute

@Composable
internal fun EpisodeDetailScreen(navController: NavController, route: EpisodeDetailRoute) {
    val store = LocalAppGraph.current.appStore
    PlaceholderScreen(title = "Episode Detail", navController = navController) {
        Text(
            "episodeGuid: ${route.episodeGuid}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                store.dispatch(
                    SetNowPlaying(NowPlayingState(episodeTitle = route.episodeGuid, isPlaying = true))
                )
                navController.navigate(NowPlayingRoute)
            },
        ) {
            Text("Play (placeholder)")
        }
    }
}
