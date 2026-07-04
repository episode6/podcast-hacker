package com.episode6.podcasthacker.ui.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FormattingTest {

    // formatShortDate uses the system time zone; noon UTC keeps the date stable in any
    // zone a dev machine or CI runner realistically uses
    private val instant = Instant.parse("2026-06-02T12:00:00Z")

    @Test
    fun formatRuntime_coversAllShapes() {
        assertThat((1.hours + 2.minutes).formatRuntime()).isEqualTo("1h 2m")
        assertThat(42.minutes.formatRuntime()).isEqualTo("42m")
        assertThat(55.seconds.formatRuntime()).isEqualTo("55s")
    }

    @Test
    fun episodeSubtitle_combinesBothSides() {
        assertThat(episodeSubtitle(instant, 30.minutes)).isEqualTo("Jun 2, 2026 · 30m")
        assertThat(episodeSubtitle(instant, null)).isEqualTo("Jun 2, 2026")
        assertThat(episodeSubtitle(null, 30.minutes)).isEqualTo("30m")
        assertThat(episodeSubtitle(null, null)).isNull()
    }
}
