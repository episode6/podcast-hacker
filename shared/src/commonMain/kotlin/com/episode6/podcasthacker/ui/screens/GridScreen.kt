package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.opml.OpmlFeed
import com.episode6.podcasthacker.data.opml.opmlDocument
import com.episode6.podcasthacker.data.opml.parseOpmlFeeds
import com.episode6.podcasthacker.data.progress.episodeProgressDocument
import com.episode6.podcasthacker.data.progress.parseEpisodeProgress
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.ImportEpisodeProgress
import com.episode6.podcasthacker.store.SubscribeToPodcast
import com.episode6.podcasthacker.store.UnsubscribeFromPodcast
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.nav.RecentlyPlayedRoute
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.rememberFileExportLauncher
import com.episode6.podcasthacker.ui.util.rememberFileImportLauncher
import com.episode6.podcasthacker.ui.util.stateOf
import kotlin.time.Duration

@Composable
internal fun GridScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    val subscriptions by store.stateOf { subscriptions }
    val isSyncing by store.stateOf { feedSync.isSyncing }

    ScreenScaffold(
        title = "Podcasts",
        navController = navController,
        constrainContentWidth = false,
        actions = { OverflowMenu(store) },
    ) {
        if (isSyncing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (subscriptions.isEmpty()) {
            Text(
                "No subscriptions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(subscriptions, key = { it.feedUrl }) { podcast ->
                PodcastTile(
                    podcast = podcast,
                    onClick = { navController.navigate(PodcastDetailRoute(feedUrl = podcast.feedUrl)) },
                    onUnsubscribe = { store.dispatch(UnsubscribeFromPodcast(podcast.feedUrl)) },
                )
            }
            item(key = "recently-played") {
                LabelTile(
                    label = "↺\nRecently Played",
                    onClick = { navController.navigate(RecentlyPlayedRoute) },
                )
            }
            item(key = "add-podcast") {
                LabelTile(
                    label = "+\nAdd Podcast",
                    onClick = { navController.navigate(AddPodcastRoute) },
                )
            }
        }
    }
}

/**
 * Overflow (3-dots) menu with OPML subscription import/export plus episode-progress
 * import/export. OPML import subscribes to every feed in the chosen file that isn't
 * already subscribed (categories flattened); new tiles appear as each feed syncs.
 * Progress import restores resume positions / play history to episodes already in the
 * library, so it belongs after an OPML import has synced. Exports save the current
 * subscriptions / listening state and are disabled while there's nothing to write.
 * Hidden entirely on platforms without file dialogs.
 */
@Composable
private fun OverflowMenu(store: AppStore) {
    val importOpml = rememberFileImportLauncher(title = "Import OPML") { xml ->
        val subscribed = store.state.subscriptions.map { it.feedUrl }.toSet()
        parseOpmlFeeds(xml)
            .filter { it.feedUrl !in subscribed }
            .forEach { store.dispatch(SubscribeToPodcast(it.feedUrl)) }
    }
    val exportOpml = rememberFileExportLauncher(
        title = "Export OPML",
        fileName = "subscriptions.opml",
        mimeType = "text/xml",
    ) {
        opmlDocument(store.state.subscriptions.map { OpmlFeed(feedUrl = it.feedUrl, title = it.title) })
    }
    val importProgress = rememberFileImportLauncher(title = "Import Episode Progress") { json ->
        parseEpisodeProgress(json).takeIf { it.isNotEmpty() }?.let { store.dispatch(ImportEpisodeProgress(it)) }
    }
    val exportProgress = rememberFileExportLauncher(
        title = "Export Episode Progress",
        fileName = "episode-progress.json",
        mimeType = "application/json",
    ) {
        episodeProgressDocument(store.state.episodesByFeed.values.flatten())
    }
    if (importOpml == null && exportOpml == null && importProgress == null && exportProgress == null) return

    val hasSubscriptions by store.stateOf { subscriptions.isNotEmpty() }
    val hasProgress by store.stateOf {
        episodesByFeed.values.any { episodes ->
            episodes.any { it.playbackPosition > Duration.ZERO || it.lastPlayed != null }
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

    fun menuAction(action: () -> Unit): () -> Unit = {
        menuOpen = false
        action()
    }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (importOpml != null) {
                DropdownMenuItem(text = { Text("Import OPML") }, onClick = menuAction(importOpml))
            }
            if (exportOpml != null) {
                DropdownMenuItem(
                    text = { Text("Export OPML") },
                    enabled = hasSubscriptions,
                    onClick = menuAction(exportOpml),
                )
            }
            if (importProgress != null) {
                DropdownMenuItem(text = { Text("Import Episode Progress") }, onClick = menuAction(importProgress))
            }
            if (exportProgress != null) {
                DropdownMenuItem(
                    text = { Text("Export Episode Progress") },
                    enabled = hasProgress,
                    onClick = menuAction(exportProgress),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastTile(
    podcast: Podcast,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(
                podcast.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Unsubscribe") },
                onClick = {
                    menuOpen = false
                    onUnsubscribe()
                },
            )
        }
    }
}

/** Artwork-less grid tile (Add Podcast, Recently Played), sized like a podcast tile. */
@Composable
private fun LabelTile(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
