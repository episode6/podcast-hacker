package com.episode6.podcasthacker.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.SeekBy
import com.episode6.podcasthacker.store.SeekTo
import com.episode6.podcasthacker.store.SetAdBoundaryConfidenceFilter
import com.episode6.podcasthacker.store.SetPlaybackSpeed
import com.episode6.podcasthacker.store.SkipToNextAdBoundary
import com.episode6.podcasthacker.store.SkipToPreviousAdBoundary
import com.episode6.podcasthacker.store.StopPlayback
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.podcasthacker.store.filteredAdBoundaries
import com.episode6.podcasthacker.store.nextAdBoundary
import com.episode6.podcasthacker.store.previousAdBoundary
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.formatTimestamp
import com.episode6.podcasthacker.ui.util.overlappedNavBarBottomPadding
import com.episode6.redux.Action
import kotlin.time.Duration.Companion.seconds

private val SPEED_OPTIONS = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)

/**
 * The Now Playing sheet's expanded face: drag handle, title row, and artwork above a
 * scrollable column of seek bar, transport controls, ad-boundary filter, speed options,
 * and Stop. Everything above the scrollable column is swipe-to-collapse territory.
 * Stopping playback clears now-playing state, which hides the whole sheet.
 */
@Composable
internal fun NowPlayingContent(
    nowPlaying: NowPlayingState,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppGraph.current.appStore
    // matches ScreenScaffold's window-adaptive padding and content-width cap
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompact = !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val screenPadding = if (isCompact) 16.dp else 24.dp
    Column(
        // top + horizontal safe-content insets apply here rather than on the shared
        // sheet container: the collapsed bar never reaches the top of the window, and
        // the mini player face manages its own horizontal insets
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeContent.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DragHandle()
        Column(
            modifier = Modifier
                .padding(bottom = overlappedNavBarBottomPadding())
                .padding(horizontal = screenPadding)
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = AppIcons.CollapseDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            // the artwork stays outside the scrollable column so vertical drags on it fall
            // through to the sheet's anchoredDraggable, extending the swipe-to-collapse
            // area from the drag handle down through the image
            AsyncImage(
                model = nowPlaying.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(nowPlaying.episodeTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                nowPlaying.podcastTitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                nowPlaying.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                SeekBar(nowPlaying, onSeek = { store.dispatch(SeekTo(it)) })
                Spacer(Modifier.height(16.dp))
                TransportControls(nowPlaying, dispatch = { store.dispatch(it) })
                Spacer(Modifier.height(16.dp))
                AdBoundaryFilterSlider(nowPlaying, onFilterChange = { store.dispatch(SetAdBoundaryConfidenceFilter(it)) })
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SPEED_OPTIONS.forEach { speed ->
                        val selected = nowPlaying.speed == speed
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
                OutlinedButton(onClick = { store.dispatch(StopPlayback) }) {
                    Text("Stop")
                }
            }
        }
    }
}

/**
 * Single-row transport: skip-to-previous-ad-boundary, back 15s, play/pause, forward 30s,
 * skip-to-next-ad-boundary. Ad-boundary candidates are tacita's unverified guesses, so
 * skipping is strictly user-initiated; the labels under the outer buttons show the time
 * since the previous candidate / until the next one, and with no candidate in a
 * direction the button disables and its label shows --:--. Those buttons stay visible
 * (disabled) for episodes with no candidates at all, e.g. anything downloaded before
 * candidates existed.
 */
@Composable
private fun TransportControls(nowPlaying: NowPlayingState, dispatch: (Action) -> Unit) {
    // prev uses the same grace-windowed selector as the skip, so the label always
    // describes where the button would land
    val prev = nowPlaying.previousAdBoundary()
    val next = nowPlaying.nextAdBoundary()
    // IntrinsicSize.Max lets the label row span exactly the width of the button row,
    // so each countdown sits under its outer skip button
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { dispatch(SkipToPreviousAdBoundary) },
                enabled = prev != null,
                modifier = Modifier.testTag("skipToPrevAdBoundary"),
            ) {
                Icon(AppIcons.SkipPrevious, contentDescription = "Skip to previous ad boundary")
            }
            IconButton(onClick = { dispatch(SeekBy((-15).seconds)) }) {
                SeekAmountIcon(AppIcons.Replay, amount = "15", contentDescription = "Back 15 seconds")
            }
            val buttonState = nowPlayingButtonState(nowPlaying)
            Button(
                onClick = {
                    when (buttonState) {
                        NowPlayingButtonState.PlayPause -> dispatch(TogglePlayPause)
                        NowPlayingButtonState.Download -> dispatch(DownloadEpisode(nowPlaying.episodeGuid))
                        else -> {}
                    }
                },
                enabled = buttonState !is NowPlayingButtonState.DownloadProgress,
                modifier = Modifier.size(72.dp).testTag("playPauseButton"),
            ) {
                when (buttonState) {
                    NowPlayingButtonState.Download -> Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(36.dp),
                    )
                    NowPlayingButtonState.Queued -> Icon(
                        imageVector = AppIcons.Schedule,
                        contentDescription = "Queued",
                        modifier = Modifier.size(36.dp),
                    )
                    is NowPlayingButtonState.DownloadProgress ->
                        if (buttonState.percentComplete == null) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            CircularProgressIndicator(
                                progress = { buttonState.percentComplete },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    NowPlayingButtonState.PlayPause ->
                        if (nowPlaying.isLoading) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(
                                imageVector = if (nowPlaying.isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                }
            }
            IconButton(onClick = { dispatch(SeekBy(30.seconds)) }) {
                SeekAmountIcon(AppIcons.Forward, amount = "30", contentDescription = "Forward 30 seconds")
            }
            IconButton(
                onClick = { dispatch(SkipToNextAdBoundary) },
                enabled = next != null,
                modifier = Modifier.testTag("skipToNextAdBoundary"),
            ) {
                Icon(AppIcons.SkipNext, contentDescription = "Skip to next ad boundary")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AdBoundaryLabel(prev?.let { (nowPlaying.position - it.position).formatTimestamp() })
            AdBoundaryLabel(next?.let { (it.position - nowPlaying.position).formatTimestamp() })
        }
    }
}

@Composable
private fun AdBoundaryLabel(text: String?) {
    Text(
        text = text ?: "--:--",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        // match the 48dp IconButton above so the countdown centers under its button
        modifier = Modifier.width(48.dp),
    )
}

/** A seek-arrow icon with the seek amount (in seconds) rendered inside the circle. */
@Composable
private fun SeekAmountIcon(icon: ImageVector, amount: String, contentDescription: String) {
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(32.dp))
        // the arrow's circle is centered slightly below the icon's midpoint
        Text(
            text = amount,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            modifier = Modifier.offset(y = 2.dp),
        )
    }
}

/**
 * Confidence filter for the skip row: at 0 every boundary is a skip target, at 1 only the
 * episode's top-confidence tier remains (see NowPlayingState.filteredAdBoundaries). The
 * slider starts hidden behind its "skips" label, which shows the reachable count range
 * (min-max, or a single number when the filter can't change anything). Tapping the label
 * toggles the slider when there's a range to scrub through; while the slider is visible
 * the label shows the current surviving count so dragging gives immediate feedback.
 */
@Composable
private fun AdBoundaryFilterSlider(nowPlaying: NowPlayingState, onFilterChange: (Float) -> Unit) {
    // the counts at the filter's extremes: every boundary at 0, top tier only at 1
    val maxSkips = nowPlaying.adBoundaries.size
    val minSkips = nowPlaying.copy(adBoundaryConfidenceFilter = 1f).filteredAdBoundaries().size
    val hasRange = minSkips != maxSkips
    var revealed by remember(nowPlaying.episodeGuid) { mutableStateOf(false) }
    val showSlider = revealed && hasRange
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // keep the row at the slider's height while collapsed so toggling doesn't
        // shift the controls below
        modifier = Modifier.widthIn(max = 320.dp).heightIn(min = 28.dp),
    ) {
        Text(
            text = when {
                showSlider -> "skips: ${nowPlaying.filteredAdBoundaries().size}"
                hasRange -> "skips: $minSkips-$maxSkips"
                else -> "skips: $maxSkips"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(enabled = hasRange) { revealed = !revealed }
                .testTag("skipsLabel")
                .padding(vertical = 6.dp),
        )
        if (showSlider) {
            Spacer(Modifier.width(12.dp))
            // visually slimmer than the default expressive slider so the filter reads as a
            // secondary control next to the seek bar
            val interactionSource = remember { MutableInteractionSource() }
            Slider(
                value = nowPlaying.adBoundaryConfidenceFilter,
                onValueChange = onFilterChange,
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        thumbSize = DpSize(4.dp, 22.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(6.dp),
                    )
                },
                modifier = Modifier.height(28.dp).testTag("adBoundaryConfidenceFilter"),
            )
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
