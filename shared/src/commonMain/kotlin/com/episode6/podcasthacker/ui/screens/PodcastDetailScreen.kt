package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.PlayEpisode
import com.episode6.podcasthacker.store.RefreshFeed
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.episodeSubtitle
import com.episode6.podcasthacker.ui.util.stateOf

@Composable
internal fun PodcastDetailScreen(navController: NavController, route: PodcastDetailRoute) {
    val graph = LocalAppGraph.current
    val store = graph.appStore
    val podcast by store.stateOf { subscriptions.firstOrNull { it.feedUrl == route.feedUrl } }
    val syncing by store.stateOf { route.feedUrl in feedSync.syncing }
    val episodes by store.stateOf { episodesByFeed[route.feedUrl].orEmpty() }

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
                val downloadStatus by store.stateOf { downloads[episode.guid] }
                EpisodeRow(
                    episode = episode,
                    downloadStatus = downloadStatus,
                    onClick = {
                        navController.navigate(
                            EpisodeDetailRoute(feedUrl = route.feedUrl, episodeGuid = episode.guid)
                        )
                    },
                    onPlay = { store.dispatch(PlayEpisode(episode.guid)) },
                    onDownload = { store.dispatch(DownloadEpisode(episode.guid)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    downloadStatus: EpisodeDownloadStatus?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        Spacer(Modifier.width(8.dp))
        EpisodeRowAction(
            episode = episode,
            downloadStatus = downloadStatus,
            onPlay = onPlay,
            onDownload = onDownload,
        )
    }
}

/**
 * Trailing control for an episode row: play when the episode is downloaded, download
 * when it isn't (including after a failure, where it acts as retry), an inert clock
 * icon while the episode waits for a download slot, and a circular progress bar while
 * a download is in flight — determinate with byte progress, indeterminate while
 * starting and while tacita is cutting ads.
 */
@Composable
private fun EpisodeRowAction(
    episode: Episode,
    downloadStatus: EpisodeDownloadStatus?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    when (downloadStatus) {
        EpisodeDownloadStatus.Queued -> EpisodeRowQueuedIcon()
        EpisodeDownloadStatus.Starting,
        EpisodeDownloadStatus.CuttingAds -> EpisodeRowProgress()
        is EpisodeDownloadStatus.Downloading -> EpisodeRowProgress(downloadStatus.percentComplete)
        is EpisodeDownloadStatus.Failure, null ->
            if (episode.downloadState == DownloadState.Downloaded) {
                EpisodeRowIconButton(AppIcons.Play, contentDescription = "Play", onClick = onPlay)
            } else {
                EpisodeRowIconButton(AppIcons.Download, contentDescription = "Download", onClick = onDownload)
            }
    }
}

@Composable
private fun EpisodeRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EPISODE_ROW_ICON_SIZE),
        )
    }
}

/** Deliberately not a button: a queued episode has no action yet, the icon just marks
 * it as waiting for one of the download slots. Sized like the buttons so rows don't
 * jump as states change. */
@Composable
private fun EpisodeRowQueuedIcon() {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = AppIcons.Schedule,
            contentDescription = "Queued",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EPISODE_ROW_ICON_SIZE),
        )
    }
}

private val EPISODE_ROW_ICON_SIZE = 32.dp

@Composable
private fun EpisodeRowProgress(percentComplete: Float? = null) {
    // matches the buttons' min touch-target height so rows don't jump as states change
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (percentComplete == null) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            CircularProgressIndicator(
                progress = { percentComplete },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
