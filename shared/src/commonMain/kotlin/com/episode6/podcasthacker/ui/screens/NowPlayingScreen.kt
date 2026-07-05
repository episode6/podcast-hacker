package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.SeekBy
import com.episode6.podcasthacker.store.SeekTo
import com.episode6.podcasthacker.store.SetPlaybackSpeed
import com.episode6.podcasthacker.store.StopPlayback
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.ui.util.formatTimestamp
import com.episode6.podcasthacker.ui.util.stateOf
import kotlin.time.Duration.Companion.seconds

private val SPEED_OPTIONS = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)

@Composable
internal fun NowPlayingScreen(navController: NavController) {
    val store = LocalAppGraph.current.appStore
    val nowPlaying by store.stateOf { nowPlaying }
    ScreenScaffold(title = "Now Playing", navController = navController) {
        val current = nowPlaying
        if (current == null) {
            Text(
                "Nothing playing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = current.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text(current.episodeTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            current.podcastTitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            current.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            SeekBar(current, onSeek = { store.dispatch(SeekTo(it)) })
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { store.dispatch(SeekBy((-15).seconds)) }) {
                    Text("↺ 15", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { store.dispatch(TogglePlayPause) }, modifier = Modifier.size(72.dp)) {
                    if (current.isLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            text = if (current.isPlaying) "❚❚" else "▶",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { store.dispatch(SeekBy(30.seconds)) }) {
                    Text("30 ↻", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SPEED_OPTIONS.forEach { speed ->
                    val selected = current.speed == speed
                    TextButton(onClick = { store.dispatch(SetPlaybackSpeed(speed)) }) {
                        Text(
                            text = "${speed.toString().removeSuffix(".0")}×",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    store.dispatch(StopPlayback)
                    navController.popBackStack()
                },
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun SeekBar(nowPlaying: NowPlayingState, onSeek: (kotlin.time.Duration) -> Unit) {
    val duration = nowPlaying.duration
    // while dragging, track the thumb locally so live position updates don't fight the drag
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val positionFraction = duration
        ?.takeIf { it.isPositive() }
        ?.let { (nowPlaying.position / it).toFloat().coerceIn(0f, 1f) }
        ?: 0f
    Column(modifier = Modifier.widthIn(max = 480.dp)) {
        Slider(
            value = dragFraction ?: positionFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val fraction = dragFraction
                if (fraction != null && duration != null) onSeek(duration * fraction.toDouble())
                dragFraction = null
            },
            enabled = duration != null,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shownPosition = dragFraction?.let { fraction -> duration?.times(fraction.toDouble()) }
                ?: nowPlaying.position
            Text(shownPosition.formatTimestamp(), style = MaterialTheme.typography.bodySmall)
            Text(duration?.formatTimestamp() ?: "--:--", style = MaterialTheme.typography.bodySmall)
        }
    }
}
