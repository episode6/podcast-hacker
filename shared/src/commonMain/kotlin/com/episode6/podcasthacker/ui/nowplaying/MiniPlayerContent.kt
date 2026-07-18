package com.episode6.podcasthacker.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.ui.screens.EpisodeRowProgress
import com.episode6.podcasthacker.ui.screens.EpisodeRowQueuedIcon
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.navBarOverlapPadding

/**
 * The Now Playing sheet's collapsed face: a mini player bar with drag handle, artwork,
 * title, playback progress, and a play/pause toggle. Tapping it (or dragging the handle
 * up) expands the sheet into the full Now Playing UI.
 */
@Composable
internal fun MiniPlayerContent(
    nowPlaying: NowPlayingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppGraph.current.appStore
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_PLAYER_HEIGHT)
            .clickable(onClick = onClick)
            .testTag("miniPlayerBar"),
    ) {
        val progress = nowPlaying.duration
            ?.takeIf { it.isPositive() }
            ?.let { ((nowPlaying.position / it).toFloat()).coerceIn(0f, 1f) }
        // pinned to the sheet's top edge so it doubles as the mini player's top border;
        // square caps, no gap, and no stop dot so it draws as a solid edge-to-edge line
        LinearProgressIndicator(
            progress = { progress ?: 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        DragHandle()
        Row(
            // start padding is a touch wider so the artwork clears rounded display
            // corners; the bottom padding excludes the slice of the bar that overlaps the
            // nav inset, centering the row between the drag handle and the system gesture pill
            modifier = Modifier
                .padding(start = 16.dp, end = 12.dp)
                .weight(1f)
                .padding(bottom = navBarOverlapPadding()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = nowPlaying.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.episodeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowPlaying.podcastTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            when (val buttonState = nowPlayingButtonState(nowPlaying)) {
                NowPlayingButtonState.Queued -> EpisodeRowQueuedIcon()
                is NowPlayingButtonState.DownloadProgress -> EpisodeRowProgress(buttonState.percentComplete)
                NowPlayingButtonState.Download -> IconButton(
                    onClick = { store.dispatch(DownloadEpisode(nowPlaying.episodeGuid)) },
                    modifier = Modifier.testTag("miniPlayerPlayPause"),
                ) {
                    Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                NowPlayingButtonState.PlayPause -> IconButton(
                    onClick = { store.dispatch(TogglePlayPause) },
                    modifier = Modifier.testTag("miniPlayerPlayPause"),
                ) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
