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
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute

@Composable
internal fun PodcastDetailScreen(navController: NavController, route: PodcastDetailRoute) {
    PlaceholderScreen(title = "Podcast Detail", navController = navController) {
        Text(
            "feedUrl: ${route.feedUrl}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                navController.navigate(
                    EpisodeDetailRoute(feedUrl = route.feedUrl, episodeGuid = "placeholder-episode")
                )
            },
        ) {
            Text("Episode Detail (placeholder)")
        }
    }
}
