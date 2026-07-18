package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.backup.libraryBackupDocument
import com.episode6.podcasthacker.data.backup.parseLibraryImport
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.network.UpdateCheckResult
import com.episode6.podcasthacker.data.opml.OpmlFeed
import com.episode6.podcasthacker.data.opml.opmlDocument
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.ImportLibrary
import com.episode6.podcasthacker.store.RefreshAllFeeds
import com.episode6.podcasthacker.store.UnsubscribeFromPodcast
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.LicensesRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.nav.RecentlyPlayedRoute
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.platformUsesPullToRefresh
import com.episode6.podcasthacker.ui.util.rememberFileExportLauncher
import com.episode6.podcasthacker.ui.util.rememberFileImportLauncher
import com.episode6.podcasthacker.ui.util.stateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GridScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    // freshest release first; the Recently Played / Add Podcast label tiles are
    // appended after the podcast items, so they always stay at the bottom
    val subscriptions by store.stateOf { subscriptionsByLatestEpisode() }
    val isSyncing by store.stateOf { feedSync.isSyncing }

    ScreenScaffold(
        title = "Podcasts",
        navController = navController,
        constrainContentWidth = false,
        actions = {
            // touch platforms refresh by pulling the grid instead of a toolbar control
            if (!platformUsesPullToRefresh) {
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                } else {
                    IconButton(onClick = { store.dispatch(RefreshAllFeeds) }) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            OverflowMenu(store, navController)
        },
    ) {
        // pull-to-refresh platforms surface syncing via the pull indicator, so the
        // bar pinned above the grid would be a second, redundant spinner there
        if (isSyncing && !platformUsesPullToRefresh) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (platformUsesPullToRefresh) {
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = { store.dispatch(RefreshAllFeeds) },
                modifier = Modifier.fillMaxSize(),
            ) {
                PodcastGrid(navController, store, subscriptions)
            }
        } else {
            PodcastGrid(navController, store, subscriptions)
        }
    }
}

@Composable
private fun PodcastGrid(
    navController: NavController,
    store: AppStore,
    subscriptions: List<Podcast>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
 * Overflow (3-dots) menu with library import/export and third-party license notices.
 * Import opens one picker, sniffs the chosen file — the app's json backup or an OPML
 * subscription list — and hands it to [ImportLibrary], which subscribes to any
 * not-yet-subscribed feeds (categories flattened) and then restores any listening state
 * the file carries. Export offers a format submenu: OPML for interop with other podcast
 * apps, json for the full library (subscriptions + progress); both disabled with nothing
 * to export. Import/export are hidden on platforms without file dialogs; the license
 * notices and check-for-updates items are always present. Check for updates asks github
 * for the newest build (latest main commit for snapshots, latest release otherwise) and
 * either opens its download page in the browser or reports the app is already current
 * via an alert dialog — see [com.episode6.podcasthacker.data.network.AppUpdateChecker].
 */
@Composable
private fun OverflowMenu(store: AppStore, navController: NavController) {
    val importFile = rememberFileImportLauncher(title = "Import") { text ->
        val backup = parseLibraryImport(text)
        if (backup.podcasts.isNotEmpty() || backup.episodes.isNotEmpty()) {
            store.dispatch(ImportLibrary(backup))
        }
    }
    val exportOpml = rememberFileExportLauncher(
        title = "Export OPML",
        fileName = "subscriptions.opml",
        // android's SAF appends an extension when the name's doesn't match the mime
        // (subscriptions.opml + text/xml saved as "subscriptions.opml.xml"); the
        // unknown-type mime has no canonical extension so the name survives intact
        mimeType = "application/octet-stream",
    ) {
        opmlDocument(store.state.subscriptions.map { OpmlFeed(feedUrl = it.feedUrl, title = it.title) })
    }
    val exportJson = rememberFileExportLauncher(
        title = "Export JSON",
        fileName = "library.json",
        mimeType = "application/json",
    ) {
        libraryBackupDocument(store.state.subscriptions, store.state.episodesByFeed.values.flatten())
    }

    // progress can't exist without a subscription, so this alone gates both exports
    val hasSubscriptions by store.stateOf { subscriptions.isNotEmpty() }
    var menuOpen by remember { mutableStateOf(false) }
    var showExportFormats by remember { mutableStateOf(false) }

    val updateChecker = LocalAppGraph.current.appUpdateChecker
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // non-null shows the check-for-updates alert dialog: a spinner while the github
    // lookup is in flight, then the already-up-to-date or failure message. An available
    // update opens the browser and dismisses the dialog instead of showing a message.
    var updateCheck by remember { mutableStateOf<UpdateCheckDialog?>(null) }
    var updateCheckJob by remember { mutableStateOf<Job?>(null) }

    fun dismissUpdateCheck() {
        updateCheckJob?.cancel()
        updateCheck = null
    }

    fun checkForUpdates() {
        updateCheck = UpdateCheckDialog.Checking
        updateCheckJob = scope.launch {
            try {
                when (val result = updateChecker.checkForUpdate()) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        uriHandler.openUri(result.url)
                        updateCheck = null
                    }
                    is UpdateCheckResult.UpToDate -> updateCheck = UpdateCheckDialog.Message(
                        "You're already on the latest version (${result.versionLabel})."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCheck = UpdateCheckDialog.Message("Update check failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun openMenu() {
        showExportFormats = false
        menuOpen = true
    }

    fun menuAction(action: () -> Unit): () -> Unit = {
        menuOpen = false
        action()
    }

    Box {
        IconButton(onClick = ::openMenu) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!showExportFormats) {
                if (importFile != null) {
                    DropdownMenuItem(text = { Text("Import") }, onClick = menuAction(importFile))
                }
                if (exportOpml != null || exportJson != null) {
                    DropdownMenuItem(
                        text = { Text("Export") },
                        enabled = hasSubscriptions,
                        onClick = { showExportFormats = true },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Check for updates") },
                    onClick = menuAction(::checkForUpdates),
                )
                DropdownMenuItem(
                    text = { Text("Third-party license notices") },
                    onClick = menuAction { navController.navigate(LicensesRoute) },
                )
            } else {
                if (exportOpml != null) {
                    DropdownMenuItem(text = { Text("OPML") }, onClick = menuAction(exportOpml))
                }
                if (exportJson != null) {
                    DropdownMenuItem(text = { Text("JSON") }, onClick = menuAction(exportJson))
                }
            }
        }
    }
    updateCheck?.let { check ->
        AlertDialog(
            onDismissRequest = ::dismissUpdateCheck,
            title = { Text("Check for updates") },
            text = {
                when (check) {
                    is UpdateCheckDialog.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Checking for updates…")
                    }
                    is UpdateCheckDialog.Message -> Text(check.text)
                }
            },
            confirmButton = {
                TextButton(onClick = ::dismissUpdateCheck) {
                    Text(if (check is UpdateCheckDialog.Checking) "Cancel" else "OK")
                }
            },
        )
    }
}

/** State of the check-for-updates dialog; dismissing it mid-[Checking] cancels the lookup. */
private sealed interface UpdateCheckDialog {
    data object Checking : UpdateCheckDialog
    data class Message(val text: String) : UpdateCheckDialog
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
