package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import com.episode6.podcasthacker.ui.util.AppIcons
import com.episode6.podcasthacker.ui.util.overlappedNavBarBottomPadding

/**
 * Simple scaffold shared by all screens: a title row with an optional back affordance
 * (desktop has no system back) and optional trailing [actions], with arbitrary content
 * below. Content adapts to the window: wider padding on medium+ windows, and unless
 * [constrainContentWidth] is disabled (grids want the full window) reading-oriented
 * content is capped at a comfortable width and centered.
 */
@Composable
internal fun ScreenScaffold(
    title: String,
    navController: NavController,
    constrainContentWidth: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompact = !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val screenPadding = if (isCompact) 16.dp else 24.dp
    val contentMaxWidth = if (constrainContentWidth && !isCompact) 840.dp else Dp.Unspecified

    // Safe-content padding everywhere except the bottom, where the nav bar inset is
    // deliberately overlapped so content isn't cut off high above the gesture pill.
    // The opaque background keeps screens from being see-through while a navigation
    // transition slides them over another screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor)
            .windowInsetsPadding(
                WindowInsets.safeContent.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
            .padding(bottom = overlappedNavBarBottomPadding())
            .padding(screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // previousBackStackEntry isn't observable on its own: the root screen's
                // title row composes mid-pop, while the popped entry is still on the
                // stack, and nothing re-read it afterwards — leaving a dead back button.
                // Observing the current entry re-runs this check once the pop settles.
                val currentEntry by navController.currentBackStackEntryAsState()
                if (currentEntry != null && navController.previousBackStackEntry != null) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}
