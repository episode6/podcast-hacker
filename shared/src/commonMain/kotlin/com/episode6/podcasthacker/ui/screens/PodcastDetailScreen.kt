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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.ui.nowplaying.MiniPlayerSpacer
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.episodeSubtitle
import com.episode6.podcasthacker.ui.util.platformUsesPullToRefresh
import com.episode6.podcasthacker.ui.util.stateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastDetailScreen(navController: NavController, route: PodcastDetailRoute) {
    val graph = LocalAppGraph.current
    val store = graph.appStore
    val podcast by store.stateOf { subscriptions.firstOrNull { it.feedUrl == route.feedUrl } }
    val syncing by store.stateOf { route.feedUrl in feedSync.syncing }

    LaunchedEffect(route.feedUrl) { store.dispatch(RefreshFeed(route.feedUrl)) }

    ScreenScaffold(
        title = podcast?.title ?: "Podcast",
        navController = navController,
        actions = {
            // touch platforms refresh by pulling the list instead of a toolbar control
            if (!platformUsesPullToRefresh) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                } else {
                    IconButton(onClick = { store.dispatch(RefreshFeed(route.feedUrl)) }) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
    ) {
        if (platformUsesPullToRefresh) {
            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = { store.dispatch(RefreshFeed(route.feedUrl)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                EpisodeList(navController, route, podcast)
            }
        } else {
            EpisodeList(navController, route, podcast)
        }
    }
}

@Composable
private fun EpisodeList(
    navController: NavController,
    route: PodcastDetailRoute,
    podcast: Podcast?,
) {
    val store = LocalAppGraph.current.appStore
    val episodes by store.stateOf { episodesByFeed[route.feedUrl].orEmpty() }
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
            val isPlaying by store.stateOf {
                nowPlaying?.episodeGuid == episode.guid && nowPlaying?.isPlaying == true
            }
            EpisodeRow(
                episode = episode,
                downloadStatus = downloadStatus,
                isPlaying = isPlaying,
                onClick = {
                    navController.navigate(
                        EpisodeDetailRoute(feedUrl = route.feedUrl, episodeGuid = episode.guid)
                    )
                },
                onPlay = { store.dispatch(PlayEpisode(episode.guid)) },
                onPause = { store.dispatch(TogglePlayPause) },
                onDownload = { store.dispatch(DownloadEpisode(episode.guid)) },
            )
            HorizontalDivider()
        }
        item(key = "mini-player-spacer") { MiniPlayerSpacer() }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    downloadStatus: EpisodeDownloadStatus?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
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
            isPlaying = isPlaying,
            onPlay = onPlay,
            onPause = onPause,
            onDownload = onDownload,
        )
    }
}

/**
 * Trailing control for an episode row: play when the episode is downloaded (pause when
 * it's the one currently playing), download when it isn't (including after a failure,
 * where it acts as retry), an inert clock icon while the episode waits for a download
 * slot, and a circular progress bar while a download is in flight — determinate with
 * byte progress, indeterminate while starting and while tacita is cutting ads.
 */
@Composable
private fun EpisodeRowAction(
    episode: Episode,
    downloadStatus: EpisodeDownloadStatus?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDownload: () -> Unit,
) {
    when (downloadStatus) {
        EpisodeDownloadStatus.Queued -> EpisodeRowQueuedIcon()
        EpisodeDownloadStatus.Starting,
        EpisodeDownloadStatus.CuttingAds -> EpisodeRowProgress()
        is EpisodeDownloadStatus.Downloading -> EpisodeRowProgress(downloadStatus.percentComplete)
        is EpisodeDownloadStatus.Failure, null ->
            if (isPlaying) {
                EpisodeRowIconButton(AppIcons.Pause, contentDescription = "Pause", onClick = onPause)
            } else if (episode.downloadState == DownloadState.Downloaded) {
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

