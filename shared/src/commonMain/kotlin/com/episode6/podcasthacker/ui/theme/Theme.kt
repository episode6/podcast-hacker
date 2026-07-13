package com.episode6.podcasthacker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

// episode6-orange accent (matching the app icon) on near-black surfaces
private val AccentOrange = Color(0xFFFF6600)

private val DarkColors = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C2600),
    onPrimaryContainer = Color(0xFFFFDBC7),
    secondary = Color(0xFFE0BCA8),
    onSecondary = Color(0xFF432B15),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE6E1E1),
    surface = Color(0xFF1B1B1D),
    onSurface = Color(0xFFE6E1E1),
    surfaceVariant = Color(0xFF29292C),
    onSurfaceVariant = Color(0xFFCAC4C4),
)

private val AppTypography = Typography().let {
    it.copy(
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
    )
}

/**
 * Dark-forward theme: the app ships dark-only for now; a light scheme can come later.
 */
@Composable
fun PodcastHackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
