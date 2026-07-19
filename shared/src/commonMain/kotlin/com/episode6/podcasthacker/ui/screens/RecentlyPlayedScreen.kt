package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.DeleteDownload
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.PlayEpisode
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nowplaying.MiniPlayerSpacer
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.formatShortDate
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * Every episode ever played in the app, most recent play first. Rows resume playback
 * (pause, for the episode currently playing) or delete the downloaded file; deleting
 * keeps the episode's row (and its play history), it only frees the disk space. Without
 * a downloaded file the play button becomes a (re-)download button — kept in the accent
 * color while the rest of the row greys out. While a download runs the row shows the
 * same detail as the episode-list rows: a queued icon while waiting for a download
 * slot, then a progress bar — determinate with byte progress, indeterminate while
 * starting and while tacita is cutting ads.
 */
@Composable
internal fun RecentlyPlayedScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    val played by store.stateOf {
        episodesByFeed.values.flatten()
            .filter { it.lastPlayed != null }
            .sortedByDescending { it.lastPlayed }
    }
    val podcastsByFeed by store.stateOf { subscriptions.associateBy { it.feedUrl } }

    ScreenScaffold(title = "Recently Played", navController = navController) {
        if (played.isEmpty()) {
            Text(
                "Nothing played yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(played, key = { it.guid }) { episode ->
                val isPlaying by store.stateOf {
                    nowPlaying?.episodeGuid == episode.guid && nowPlaying?.isPlaying == true
                }
                val downloadStatus by store.stateOf { downloads[episode.guid] }
                RecentlyPlayedRow(
                    episode = episode,
                    podcastTitle = podcastsByFeed[episode.feedUrl]?.title,
                    artworkUrl = podcastsByFeed[episode.feedUrl]?.artworkUrl,
                    isPlaying = isPlaying,
                    downloadStatus = downloadStatus,
                    onClick = {
                        navController.navigate(
                            EpisodeDetailRoute(feedUrl = episode.feedUrl, episodeGuid = episode.guid)
                        )
                    },
                    onResume = { store.dispatch(PlayEpisode(episode.guid)) },
                    onPause = { store.dispatch(TogglePlayPause) },
                    onDownload = { store.dispatch(DownloadEpisode(episode.guid)) },
                    onDeleteFile = { store.dispatch(DeleteDownload(episode.guid)) },
                )
                HorizontalDivider()
            }
            item(key = "mini-player-spacer") { MiniPlayerSpacer() }
        }
    }
}

@Composable
private fun RecentlyPlayedRow(
    episode: Episode,
    podcastTitle: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    downloadStatus: EpisodeDownloadStatus?,
    onClick: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDownload: () -> Unit,
    onDeleteFile: () -> Unit,
) {
    val downloaded = episode.downloadState == DownloadState.Downloaded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                podcastTitle,
                episode.lastPlayed?.let { "Played ${it.formatShortDate()}" },
                "↓ downloaded".takeIf { downloaded },
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
        when (downloadStatus) {
            EpisodeDownloadStatus.Queued -> EpisodeRowQueuedIcon()
            EpisodeDownloadStatus.Starting,
            EpisodeDownloadStatus.CuttingAds,
            EpisodeDownloadStatus.Finishing -> EpisodeRowProgress()
            is EpisodeDownloadStatus.Downloading -> EpisodeRowProgress(downloadStatus.percentComplete)
            is EpisodeDownloadStatus.Failure, null ->
                if (downloaded) {
                    RowIconButton(
                        icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Resume",
                        enabled = true,
                        onClick = if (isPlaying) onPause else onResume,
                    )
                } else {
                    RowIconButton(
                        icon = AppIcons.Download,
                        contentDescription = "Download",
                        enabled = true,
                        onClick = onDownload,
                    )
                }
        }
        RowIconButton(
            icon = AppIcons.Delete,
            contentDescription = "Delete file",
            enabled = downloaded,
            onClick = onDeleteFile,
        )
    }
}

/** 32dp icon buttons matching the episode-list rows in PodcastDetailScreen. */
@Composable
private fun RowIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(EPISODE_ROW_ICON_SIZE),
        )
    }
}
