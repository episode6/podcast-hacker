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
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.SubscribeToPodcast
import com.episode6.podcasthacker.store.UnsubscribeFromPodcast
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.nav.RecentlyPlayedRoute
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.rememberOpmlExportLauncher
import com.episode6.podcasthacker.ui.util.rememberOpmlImportLauncher
import com.episode6.podcasthacker.ui.util.stateOf

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
 * Overflow (3-dots) menu with OPML subscription import/export. Import subscribes to
 * every feed in the chosen file that isn't already subscribed (categories flattened);
 * new tiles appear as each feed syncs. Export saves the current subscriptions and is
 * disabled while there are none. Hidden entirely on platforms without file dialogs.
 */
@Composable
private fun OverflowMenu(store: AppStore) {
    val importOpml = rememberOpmlImportLauncher { xml ->
        val subscribed = store.state.subscriptions.map { it.feedUrl }.toSet()
        parseOpmlFeeds(xml)
            .filter { it.feedUrl !in subscribed }
            .forEach { store.dispatch(SubscribeToPodcast(it.feedUrl)) }
    }
    val exportOpml = rememberOpmlExportLauncher {
        opmlDocument(store.state.subscriptions.map { OpmlFeed(feedUrl = it.feedUrl, title = it.title) })
    }
    if (importOpml == null && exportOpml == null) return

    val hasSubscriptions by store.stateOf { subscriptions.isNotEmpty() }
    var menuOpen by remember { mutableStateOf(false) }
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
                DropdownMenuItem(
                    text = { Text("Import OPML") },
                    onClick = {
                        menuOpen = false
                        importOpml()
                    },
                )
            }
            if (exportOpml != null) {
                DropdownMenuItem(
                    text = { Text("Export OPML") },
                    enabled = hasSubscriptions,
                    onClick = {
                        menuOpen = false
                        exportOpml()
                    },
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
