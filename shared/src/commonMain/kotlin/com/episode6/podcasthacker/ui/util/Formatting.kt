package com.episode6.podcasthacker.ui.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

private val shortDateFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    day(padding = Padding.NONE)
    char(',')
    char(' ')
    year()
}

/** e.g. `Jun 2, 2026` in the device's time zone. */
internal fun Instant.formatShortDate(): String =
    shortDateFormat.format(toLocalDateTime(TimeZone.currentSystemDefault()).date)

/** e.g. `1h 2m`, `42m`, or `55s` for sub-minute runtimes. */
internal fun Duration.formatRuntime(): String = when {
    inWholeHours > 0 -> "${inWholeHours}h ${inWholeMinutes % 60}m"
    inWholeMinutes > 0 -> "${inWholeMinutes}m"
    else -> "${inWholeSeconds}s"
}

/** `Jun 2, 2026 · 1h 2m` — either side optional; null when both are missing. */
internal fun episodeSubtitle(pubDate: Instant?, duration: Duration?): String? =
    listOfNotNull(pubDate?.formatShortDate(), duration?.formatRuntime())
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
