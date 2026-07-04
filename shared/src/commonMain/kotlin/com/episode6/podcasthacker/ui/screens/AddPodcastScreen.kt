package com.episode6.podcasthacker.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
internal fun AddPodcastScreen(navController: NavController) {
    PlaceholderScreen(title = "Add Podcast", navController = navController) {
        Text(
            "Search + paste-RSS-URL coming in Stage 4",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
