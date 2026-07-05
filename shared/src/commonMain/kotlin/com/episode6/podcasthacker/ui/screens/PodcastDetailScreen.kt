package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.RefreshFeed
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.util.episodeSubtitle
import com.episode6.podcasthacker.ui.util.stateOf

@Composable
internal fun PodcastDetailScreen(navController: NavController, route: PodcastDetailRoute) {
    val graph = LocalAppGraph.current
    val store = graph.appStore
    val podcast by store.stateOf { subscriptions.firstOrNull { it.feedUrl == route.feedUrl } }
    val syncing by store.stateOf { route.feedUrl in feedSync.syncing }
    val episodes by remember(route.feedUrl) { graph.episodeRepository.observeEpisodes(route.feedUrl) }
        .collectAsState(emptyList())

    LaunchedEffect(route.feedUrl) { store.dispatch(RefreshFeed(route.feedUrl)) }

    ScreenScaffold(
        title = podcast?.title ?: "Podcast",
        navController = navController,
        actions = {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                TextButton(onClick = { store.dispatch(RefreshFeed(route.feedUrl)) }) { Text("Refresh") }
            }
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = podcast?.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        podcast?.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        podcast?.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
            }
            items(episodes, key = { it.guid }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onClick = {
                        navController.navigate(
                            EpisodeDetailRoute(feedUrl = route.feedUrl, episodeGuid = episode.guid)
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            episode.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = listOfNotNull(
            episodeSubtitle(episode.pubDate, episode.duration),
            "↓ downloaded".takeIf { episode.downloadState == DownloadState.Downloaded },
        ).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
