package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute
import com.episode6.podcasthacker.ui.util.basicHtmlToAnnotatedString
import com.episode6.podcasthacker.ui.util.episodeSubtitle
import com.episode6.podcasthacker.ui.util.stateOf

@Composable
internal fun EpisodeDetailScreen(navController: NavController, route: EpisodeDetailRoute) {
    val graph = LocalAppGraph.current
    val store = graph.appStore
    val podcast by store.stateOf { subscriptions.firstOrNull { it.feedUrl == route.feedUrl } }
    val episode by remember(route.episodeGuid) { graph.episodeRepository.observeEpisode(route.episodeGuid) }
        .collectAsState(null)

    ScreenScaffold(
        title = podcast?.title ?: "Episode",
        navController = navController,
    ) {
        val current = episode
        if (current == null) {
            Text(
                "Episode not found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = podcast?.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                    episodeSubtitle(current.pubDate, current.duration)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                // playback lands in Stage 7; for now Play just drives the nowPlaying
                // state so the mini player + NowPlaying screen can be exercised
                Button(
                    onClick = {
                        store.dispatch(SetNowPlaying(NowPlayingState(episodeTitle = current.title, isPlaying = true)))
                        navController.navigate(NowPlayingRoute)
                    },
                ) {
                    Text("Play")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Download (Stage 6)")
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            current.notes?.let { notes ->
                Text(
                    basicHtmlToAnnotatedString(notes, linkColor = MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: Text(
                "No show notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
