package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.podcasthacker.data.model.AdBoundary
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NowPlayingStateTest {

    private fun boundary(position: Duration) =
        AdBoundary(position, AdBoundary.Source.DiffCut, AdBoundary.Role.Start)

    private fun state(position: Duration, boundaries: List<Duration> = emptyList()) = NowPlayingState(
        episodeGuid = "guid",
        episodeTitle = "Ep",
        position = position,
        adBoundaries = boundaries.map { boundary(it) },
    )

    @Test
    fun noBoundaries_bothDirectionsEmpty() {
        val state = state(position = 5.minutes)

        assertThat(state.nextAdBoundary()).isNull()
        assertThat(state.previousAdBoundary()).isNull()
    }

    @Test
    fun next_findsFirstBoundaryStrictlyAfterPosition() {
        val state = state(1.minutes + 30.seconds, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.nextAdBoundary()).isEqualTo(boundary(5.minutes))
    }

    @Test
    fun next_exactlyAtBoundary_advancesToTheFollowingOne() {
        val state = state(5.minutes, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.nextAdBoundary()).isEqualTo(boundary(10.minutes))
    }

    @Test
    fun next_pastLastBoundary_isNull() {
        val state = state(11.minutes, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.nextAdBoundary()).isNull()
    }

    @Test
    fun previous_findsLastBoundaryBehindTheGraceWindow() {
        val state = state(5.minutes + 30.seconds, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.previousAdBoundary()).isEqualTo(boundary(5.minutes))
    }

    @Test
    fun previous_justAfterABoundary_walksBackToTheOneBefore() {
        val state = state(5.minutes + 1.seconds, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.previousAdBoundary()).isEqualTo(boundary(1.minutes))
    }

    @Test
    fun previous_beforeFirstBoundary_isNull() {
        val state = state(30.seconds, listOf(1.minutes, 5.minutes))

        assertThat(state.previousAdBoundary()).isNull()
    }

    @Test
    fun previous_atExactlyTheGraceWindow_isEligible() {
        val state = state(1.minutes + SKIP_BACK_GRACE, listOf(1.minutes))

        assertThat(state.previousAdBoundary()).isEqualTo(boundary(1.minutes))
    }
}
