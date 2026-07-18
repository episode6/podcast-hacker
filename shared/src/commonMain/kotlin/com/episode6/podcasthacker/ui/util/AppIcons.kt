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
    /** Material "arrow_back" (filled). */
    val ArrowBack: ImageVector by lazy {
        materialIcon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineToRelative(-2f)
            close()
        }
    }

    /** Material "play_arrow" (filled). */
    val Play: ImageVector by lazy {
        materialIcon("Play") {
            moveTo(8f, 5f)
            verticalLineTo(19f)
            lineTo(19f, 12f)
            close()
        }
    }

    /** Material "pause" (filled). */
    val Pause: ImageVector by lazy {
        materialIcon("Pause") {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            close()
        }
    }

    /** Material "replay" (counterclockwise circular arrow). */
    val Replay: ImageVector by lazy {
        materialIcon("Replay") {
            moveTo(12f, 5f)
            verticalLineTo(1f)
            lineTo(7f, 6f)
            lineToRelative(5f, 5f)
            verticalLineTo(7f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
            reflectiveCurveToRelative(-2.69f, 6f, -6f, 6f)
            reflectiveCurveToRelative(-6f, -2.69f, -6f, -6f)
            horizontalLineTo(4f)
            curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
            reflectiveCurveToRelative(-3.58f, -8f, -8f, -8f)
            close()
        }
    }

    /** Clockwise circular arrow — the base of Material "forward_30" without the digits. */
    val Forward: ImageVector by lazy {
        materialIcon("Forward") {
            moveTo(18f, 13f)
            curveToRelative(0f, 3.31f, -2.69f, 6f, -6f, 6f)
            reflectiveCurveToRelative(-6f, -2.69f, -6f, -6f)
            reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
            verticalLineToRelative(4f)
            lineToRelative(5f, -5f)
            lineToRelative(-5f, -5f)
            verticalLineToRelative(4f)
            curveToRelative(-4.42f, 0f, -8f, 3.58f, -8f, 8f)
            reflectiveCurveToRelative(3.58f, 8f, 8f, 8f)
            reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
            horizontalLineToRelative(-2f)
            close()
        }
    }

    /** Material "skip_previous" (filled). */
    val SkipPrevious: ImageVector by lazy {
        materialIcon("SkipPrevious") {
            moveTo(6f, 6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(12f)
            horizontalLineTo(6f)
            close()
            moveTo(9.5f, 12f)
            lineToRelative(8.5f, 6f)
            verticalLineTo(6f)
            close()
        }
    }

    /** Material "skip_next" (filled). */
    val SkipNext: ImageVector by lazy {
        materialIcon("SkipNext") {
            moveTo(6f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(6f, 6f)
            close()
            moveTo(16f, 6f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            close()
        }
    }

    /** Material "keyboard_arrow_down" (filled chevron). */
    val CollapseDown: ImageVector by lazy {
        materialIcon("CollapseDown") {
            moveTo(7.41f, 8.59f)
            lineTo(12f, 13.17f)
            lineToRelative(4.59f, -4.58f)
            lineTo(18f, 10f)
            lineToRelative(-6f, 6f)
            lineToRelative(-6f, -6f)
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

    /** Material "delete" (filled trash can). */
    val Delete: ImageVector by lazy {
        materialIcon("Delete") {
            moveTo(6f, 19f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            horizontalLineTo(6f)
            verticalLineToRelative(12f)
            close()
            moveTo(19f, 4f)
            horizontalLineToRelative(-3.5f)
            lineToRelative(-1f, -1f)
            horizontalLineToRelative(-5f)
            lineToRelative(-1f, 1f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(14f)
            close()
        }
    }

    /** Material "more_vert" (vertical 3-dot overflow). */
    val MoreVert: ImageVector by lazy {
        materialIcon("MoreVert") {
            moveTo(12f, 8f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            close()
            moveTo(12f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 16f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
        }
    }

    /** Material "refresh" (circular arrow). */
    val Refresh: ImageVector by lazy {
        materialIcon("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
            reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
            curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
            horizontalLineToRelative(-2.08f)
            curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
            curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
            reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
            curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
            lineTo(13f, 11f)
            horizontalLineToRelative(7f)
            verticalLineTo(4f)
            lineToRelative(-2.35f, 2.35f)
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
