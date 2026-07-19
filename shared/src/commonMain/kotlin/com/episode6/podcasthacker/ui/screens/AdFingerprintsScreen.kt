package com.episode6.podcasthacker.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.data.model.AdFingerprint
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.LoadAdFingerprints
import com.episode6.podcasthacker.store.RemoveAdFingerprint
import com.episode6.podcasthacker.ui.nowplaying.MiniPlayerSpacer
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.formatRuntime
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * Management screen for the per-feed ad-creative fingerprint stores (reached from the
 * podcast grid's overflow menu). Lists every subscribed feed's stored fingerprints —
 * the creatives tacita flags as high-confidence boundaries when they recur in later
 * downloads — with provenance (ear-confirmed outranks diff-proven), the creative's
 * runtime, encoded size, and content id, plus a delete button that revokes the
 * fingerprint (e.g. one confirmed in error). Stores reload on every visit; feeds
 * without fingerprints are omitted entirely.
 */
@Composable
internal fun AdFingerprintsScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    LaunchedEffect(store) { store.dispatch(LoadAdFingerprints) }
    val subscriptions by store.stateOf { subscriptions }
    val fingerprints by store.stateOf { adFingerprints }

    ScreenScaffold(title = "Ad Fingerprints", navController = navController) {
        val sections = subscriptions.mapNotNull { podcast ->
            fingerprints[podcast.feedUrl]?.takeIf { it.isNotEmpty() }?.let { podcast to it }
        }
        if (sections.isEmpty()) {
            Text(
                "No ad fingerprints yet. They accumulate as downloads cut ads and as you " +
                    "confirm ads from the player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            sections.forEach { (podcast, feedFingerprints) ->
                item(key = "header-${podcast.feedUrl}") { PodcastHeader(podcast) }
                items(feedFingerprints, key = { "${podcast.feedUrl}:${it.id}" }) { fingerprint ->
                    FingerprintRow(
                        fingerprint = fingerprint,
                        onDelete = { store.dispatch(RemoveAdFingerprint(podcast.feedUrl, fingerprint.id)) },
                    )
                    HorizontalDivider()
                }
            }
            item(key = "mini-player-spacer") { MiniPlayerSpacer() }
        }
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    ) {
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            podcast.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FingerprintRow(fingerprint: AdFingerprint, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (fingerprint.provenance) {
                    AdFingerprint.Provenance.HumanConfirmed -> "Ear-confirmed ad"
                    AdFingerprint.Provenance.DiffProven -> "Diff-proven ad"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${fingerprint.duration.formatRuntime()} · ${fingerprint.sizeBytes.formatSize()}" +
                    " · ${fingerprint.id.take(12)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = "Delete fingerprint",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** e.g. `733 KB` or `12.4 MB` — ad creatives are small, no GB tier needed. */
private fun Long.formatSize(): String = when {
    this >= 1_000_000 -> "${(this / 100_000) / 10.0} MB"
    else -> "${(this / 1_000).coerceAtLeast(1)} KB"
}
