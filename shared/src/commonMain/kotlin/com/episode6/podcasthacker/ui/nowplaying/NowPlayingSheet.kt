package com.episode6.podcasthacker.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.ui.util.overlappedNavBarBottomPadding
import com.episode6.podcasthacker.ui.util.stateOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Duration of the Now Playing sheet's slide-in/out when playback starts/stops. */
private const val SHEET_VISIBILITY_TRANSITION_MILLIS = 300

internal val DRAG_HANDLE_HEIGHT = 14.dp

/** Height of the collapsed sheet (mini player) excluding the nav-bar inset. */
internal val MINI_PLAYER_HEIGHT = DRAG_HANDLE_HEIGHT + 64.dp

/** The expanded content is fully faded in (and the mini player out) by this expansion fraction. */
private const val CROSS_FADE_END_FRACTION = 0.25f

internal enum class NowPlayingSheetValue { Collapsed, Expanded }

/**
 * State of the Now Playing sheet: [NowPlayingSheetValue.Collapsed] is the mini player bar
 * pinned above the bottom edge, [NowPlayingSheetValue.Expanded] covers the window with the
 * full Now Playing UI. The sheet tracks the user's finger between the two anchors.
 */
@Stable
internal class NowPlayingSheetState {
    internal val draggable = AnchoredDraggableState(initialValue = NowPlayingSheetValue.Collapsed)

    val isExpanded: Boolean get() = draggable.currentValue == NowPlayingSheetValue.Expanded

    suspend fun expand() = draggable.animateTo(NowPlayingSheetValue.Expanded)
    suspend fun collapse() = draggable.animateTo(NowPlayingSheetValue.Collapsed)

    /**
     * How far the sheet is between Collapsed (0) and Expanded (1); drives the cross-fade
     * between the mini player and the full Now Playing content.
     */
    internal fun expandFraction(): Float {
        val collapsedAt = draggable.anchors.positionOf(NowPlayingSheetValue.Collapsed)
        val expandedAt = draggable.anchors.positionOf(NowPlayingSheetValue.Expanded)
        val offset = draggable.offset
        if (offset.isNaN() || collapsedAt.isNaN() || expandedAt.isNaN() || collapsedAt == expandedAt) return 0f
        return ((collapsedAt - offset) / (collapsedAt - expandedAt)).coerceIn(0f, 1f)
    }
}

@Composable
internal fun rememberNowPlayingSheetState(): NowPlayingSheetState = remember { NowPlayingSheetState() }

/** Lets screens (e.g. Episode Detail's Play button) expand the sheet without plumbing params. */
internal val LocalNowPlayingSheetState = staticCompositionLocalOf<NowPlayingSheetState> {
    error("NowPlayingSheetState not provided")
}

/**
 * The Now Playing sheet, overlaid on the nav content and visible whenever something is
 * playing. Collapsed it renders [MiniPlayerContent]; drag the handle (or tap the bar) to
 * expand it into [NowPlayingContent], and drag back down (or back-press / tap the collapse
 * chevron) to shrink it back to the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NowPlayingSheet(state: NowPlayingSheetState, modifier: Modifier = Modifier) {
    val store = LocalAppGraph.current.appStore
    val nowPlaying by store.stateOf { nowPlaying }
    // retain the last-playing state so the sheet still has content to render while it
    // animates off screen after playback stops
    val retained = remember { mutableStateOf<NowPlayingState?>(null) }
    if (nowPlaying != null) SideEffect { retained.value = nowPlaying }
    val displayed = nowPlaying ?: retained.value

    val visible = nowPlaying != null
    LaunchedEffect(visible) {
        if (!visible) state.draggable.snapTo(NowPlayingSheetValue.Collapsed)
    }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = visible && state.isExpanded) { scope.launch { state.collapse() } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val collapsedHeight = MINI_PLAYER_HEIGHT + overlappedNavBarBottomPadding()
        val collapsedHeightPx = with(LocalDensity.current) { collapsedHeight.toPx() }
        remember(containerHeightPx, collapsedHeightPx) {
            state.draggable.updateAnchors(
                DraggableAnchors {
                    NowPlayingSheetValue.Collapsed at containerHeightPx - collapsedHeightPx
                    NowPlayingSheetValue.Expanded at 0f
                },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SHEET_VISIBILITY_TRANSITION_MILLIS)) { it },
            exit = slideOutVertically(tween(SHEET_VISIBILITY_TRANSITION_MILLIS)) { it },
        ) {
            // The surface is always full-window-sized and simply offset down to its anchor,
            // so dragging never re-measures content. Surface also consumes stray touches,
            // keeping taps from falling through to the nav content beneath.
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, state.draggable.requireOffset().roundToInt()) }
                    .anchoredDraggable(state.draggable, Orientation.Vertical)
                    .testTag("nowPlayingSheet"),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
            ) {
                val current = displayed ?: return@Surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)),
                ) {
                    // compose each layer only while it's at all visible so the hidden one
                    // can't swallow clicks or confuse test matchers
                    val showExpanded by remember { derivedStateOf { state.expandFraction() > 0f } }
                    val showCollapsed by remember { derivedStateOf { state.expandFraction() < 1f } }
                    if (showExpanded) {
                        NowPlayingContent(
                            nowPlaying = current,
                            onCollapse = { scope.launch { state.collapse() } },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = (state.expandFraction() / CROSS_FADE_END_FRACTION).coerceIn(0f, 1f)
                                },
                        )
                    }
                    if (showCollapsed) {
                        MiniPlayerContent(
                            nowPlaying = current,
                            onClick = { scope.launch { state.expand() } },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    alpha = 1f - (state.expandFraction() / CROSS_FADE_END_FRACTION).coerceIn(0f, 1f)
                                },
                        )
                    }
                }
            }
        }
    }
}

/** The pill affordance at the top of the sheet signalling it can be dragged. */
@Composable
internal fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(DRAG_HANDLE_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}
