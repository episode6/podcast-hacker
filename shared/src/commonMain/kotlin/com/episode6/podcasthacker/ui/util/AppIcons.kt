package com.episode6.podcasthacker.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-inlined Material Symbols. The multiplatform material-icons artifacts were
 * discontinued after Compose Multiplatform 1.7, so the few icons we need are defined
 * here from the standard 24x24 Material path data instead of pulling a dependency.
 */
internal object AppIcons {
    /** Material "play_arrow" (filled). */
    val Play: ImageVector by lazy {
        materialIcon("Play") {
            moveTo(8f, 5f)
            verticalLineTo(19f)
            lineTo(19f, 12f)
            close()
        }
    }

    /** Material "download" (filled). */
    val Download: ImageVector by lazy {
        materialIcon("Download") {
            moveTo(19f, 9f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            lineTo(12f, 16f)
            close()
            moveTo(5f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            close()
        }
    }

    /** Material "schedule" (filled clock face). */
    val Schedule: ImageVector by lazy {
        materialIcon("Schedule") {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.47f, 22f, 11.99f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
            reflectiveCurveTo(7.58f, 4f, 12f, 4f)
            reflectiveCurveTo(20f, 7.58f, 20f, 12f)
            reflectiveCurveTo(16.42f, 20f, 12f, 20f)
            close()
            moveTo(12.5f, 7f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            lineTo(16.25f, 16.15f)
            lineTo(17f, 14.92f)
            lineTo(12.5f, 12.25f)
            close()
        }
    }

    private fun materialIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = pathData)
        }.build()
}
