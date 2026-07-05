package com.episode6.podcasthacker.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far bottom-anchored content may overlap the navigation bar inset. The gesture pill
 * floats over otherwise-empty surface, so reserving the full inset reads as excess padding.
 */
private val NAV_BAR_OVERLAP = 12.dp

/**
 * Bottom padding that keeps content clear of the navigation bar while deliberately
 * overlapping the inset by [NAV_BAR_OVERLAP]. Zero on platforms with no bottom inset.
 */
@Composable
internal fun overlappedNavBarBottomPadding(): Dp =
    (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() - NAV_BAR_OVERLAP)
        .coerceAtLeast(0.dp)
