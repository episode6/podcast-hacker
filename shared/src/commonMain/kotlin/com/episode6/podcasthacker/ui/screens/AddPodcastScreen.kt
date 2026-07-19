package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.SubscribeToPodcast
import com.episode6.podcasthacker.ui.addpodcast.AddPodcastViewModel
import com.episode6.podcasthacker.ui.addpodcast.PodcastSearchResult
import com.episode6.podcasthacker.ui.addpodcast.SetQuery
import com.episode6.podcasthacker.ui.nowplaying.MiniPlayerSpacer
import com.episode6.redux.compose.collectAsState

@Composable
internal fun AddPodcastScreen(navController: NavController) {
    val appGraph = LocalAppGraph.current
    val viewModel: AddPodcastViewModel = viewModel { AddPodcastViewModel(appGraph.itunesSearchClient) }
    val store = viewModel.store
    val state by store.collectAsState()

    fun subscribeAndClose(feedUrl: String) {
        appGraph.appStore.dispatch(SubscribeToPodcast(feedUrl))
        navController.popBackStack()
    }

    ScreenScaffold(title = "Add Podcast", navController = navController) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { store.dispatch(SetQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search podcasts or paste an RSS url") },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        when {
            state.queryIsUrl -> SubscribeToUrlRow(
                feedUrl = state.query.trim(),
                onClick = { subscribeAndClose(state.query.trim()) },
            )
            state.searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Searching…", style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null -> Text(
                state.error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> LazyColumn {
                items(state.results, key = { it.feedUrl }) { result ->
                    SearchResultRow(result = result, onClick = { subscribeAndClose(result.feedUrl) })
                }
                item(key = "mini-player-spacer") { MiniPlayerSpacer() }
            }
        }
    }
}

@Composable
private fun SubscribeToUrlRow(feedUrl: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Subscribe to RSS url", style = MaterialTheme.typography.titleMedium)
            Text(
                feedUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultRow(result: PodcastSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            result.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
