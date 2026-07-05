package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass

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
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompact = !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val screenPadding = if (isCompact) 16.dp else 24.dp
    val contentMaxWidth = if (constrainContentWidth && !isCompact) 840.dp else Dp.Unspecified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (navController.previousBackStackEntry != null) {
                    TextButton(onClick = { navController.popBackStack() }) { Text("← Back") }
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
