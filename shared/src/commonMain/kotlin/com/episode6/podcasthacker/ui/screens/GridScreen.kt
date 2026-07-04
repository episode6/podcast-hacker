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
import com.episode6.podcasthacker.getPlatform
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute

@Composable
internal fun GridScreen(navController: NavController) {
    PlaceholderScreen(title = "Podcasts", navController = navController) {
        Text(
            "Running on ${getPlatform().name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { navController.navigate(AddPodcastRoute) }) {
            Text("Add Podcast")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { navController.navigate(PodcastDetailRoute(feedUrl = "placeholder://feed")) }) {
            Text("Podcast Detail (placeholder)")
        }
    }
}
