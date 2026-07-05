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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.DeleteDownload
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.PlayEpisode
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
    val downloadStatus by store.stateOf { downloads[route.episodeGuid] }
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
                // playback is download-first (tacita needs whole files), so Play waits
                // for the episode to finish downloading
                Button(
                    enabled = current.downloadState == DownloadState.Downloaded,
                    onClick = {
                        store.dispatch(PlayEpisode(current.guid))
                        navController.navigate(NowPlayingRoute)
                    },
                ) {
                    Text("Play")
                }
                Spacer(Modifier.width(8.dp))
                DownloadControl(
                    downloadStatus = downloadStatus,
                    downloaded = current.downloadState == DownloadState.Downloaded,
                    onDownload = { store.dispatch(DownloadEpisode(current.guid)) },
                    onDelete = { store.dispatch(DeleteDownload(current.guid)) },
                )
            }
            (downloadStatus as? EpisodeDownloadStatus.Failure)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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

@Composable
private fun DownloadControl(
    downloadStatus: EpisodeDownloadStatus?,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    when (downloadStatus) {
        EpisodeDownloadStatus.Queued,
        EpisodeDownloadStatus.Starting -> OutlinedButton(onClick = {}, enabled = false) {
            CircularProgressIndicator(Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (downloadStatus == EpisodeDownloadStatus.Queued) "Queued" else "Starting…")
        }
        is EpisodeDownloadStatus.Downloading -> OutlinedButton(onClick = {}, enabled = false) {
            LinearProgressIndicator(
                progress = { downloadStatus.percentComplete },
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("${(downloadStatus.percentComplete * 100).toInt()}%")
        }
        EpisodeDownloadStatus.CuttingAds -> OutlinedButton(onClick = {}, enabled = false) {
            CircularProgressIndicator(Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cutting ads…")
        }
        is EpisodeDownloadStatus.Failure -> OutlinedButton(onClick = onDownload) {
            Text("Retry Download")
        }
        null -> if (downloaded) {
            OutlinedButton(onClick = onDelete) { Text("Delete Download") }
        } else {
            OutlinedButton(onClick = onDownload) { Text("Download") }
        }
    }
}
