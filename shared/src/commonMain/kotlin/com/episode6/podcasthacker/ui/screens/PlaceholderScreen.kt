package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Simple scaffold shared by all Stage-2 placeholder screens: a title, an optional
 * back affordance (desktop has no system back), and arbitrary content below.
 */
@Composable
internal fun PlaceholderScreen(
    title: String,
    navController: NavController,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (navController.previousBackStackEntry != null) {
                TextButton(onClick = { navController.popBackStack() }) { Text("← Back") }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}
