package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.ui.util.stateOf

@Composable
internal fun NowPlayingScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    val nowPlaying by store.stateOf { nowPlaying }
    ScreenScaffold(title = "Now Playing", navController = navController) {
        val current = nowPlaying
        if (current == null) {
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(current.episodeTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Row {
                Button(onClick = { store.dispatch(SetNowPlaying(current.copy(isPlaying = !current.isPlaying))) }) {
                    Text(if (current.isPlaying) "Pause" else "Play")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        store.dispatch(SetNowPlaying(null))
                        navController.popBackStack()
                    },
                ) {
                    Text("Stop")
                }
            }
        }
    }
}
