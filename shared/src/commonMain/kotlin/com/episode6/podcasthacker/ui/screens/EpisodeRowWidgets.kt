package com.episode6.podcasthacker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.episode6.podcasthacker.ui.util.AppIcons

internal val EPISODE_ROW_ICON_SIZE = 32.dp

/** Deliberately not a button: a queued episode has no action yet, the icon just marks
 * it as waiting for one of the download slots. Sized like the buttons so rows don't
 * jump as states change. */
@Composable
internal fun EpisodeRowQueuedIcon() {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = AppIcons.Schedule,
            contentDescription = "Queued",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EPISODE_ROW_ICON_SIZE),
        )
    }
}

@Composable
internal fun EpisodeRowProgress(percentComplete: Float? = null) {
    // matches the buttons' min touch-target height so rows don't jump as states change
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (percentComplete == null) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            CircularProgressIndicator(
                progress = { percentComplete },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
