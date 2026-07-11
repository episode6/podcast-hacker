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

    private fun boundary(position: Duration, confidence: Float = 0.5f) =
        AdBoundary(position, AdBoundary.Source.DiffCut, AdBoundary.Role.Start, confidence)

    private fun state(position: Duration, boundaries: List<Duration> = emptyList()) = NowPlayingState(
        episodeGuid = "guid",
        episodeTitle = "Ep",
        position = position,
        adBoundaries = boundaries.map { boundary(it) },
    )

    private fun mixedConfidenceState(filter: Float) = NowPlayingState(
        episodeGuid = "guid",
        episodeTitle = "Ep",
        position = Duration.ZERO,
        adBoundaries = listOf(
            boundary(1.minutes, confidence = 0.3f),
            boundary(2.minutes, confidence = 0.65f),
            boundary(3.minutes, confidence = 0.9f),
        ),
        adBoundaryConfidenceFilter = filter,
    )

    @Test
    fun filterAtZero_keepsEveryBoundary() {
        assertThat(mixedConfidenceState(filter = 0f).filteredAdBoundaries().map { it.confidence })
            .isEqualTo(listOf(0.3f, 0.65f, 0.9f))
    }

    @Test
    fun filterAtMax_keepsOnlyTheTopConfidenceTier() {
        assertThat(mixedConfidenceState(filter = 1f).filteredAdBoundaries().map { it.confidence })
            .isEqualTo(listOf(0.9f))
    }

    @Test
    fun filterMidway_thresholdsAcrossTheObservedRange() {
        // threshold = 0.3 + (0.9 - 0.3) * 0.5 = 0.6
        assertThat(mixedConfidenceState(filter = 0.5f).filteredAdBoundaries().map { it.confidence })
            .isEqualTo(listOf(0.65f, 0.9f))
    }

    @Test
    fun uniformConfidences_filterKeepsEverythingAtEveryPosition() {
        val state = state(Duration.ZERO, listOf(1.minutes, 5.minutes))
            .copy(adBoundaryConfidenceFilter = 1f)

        assertThat(state.filteredAdBoundaries()).isEqualTo(state.adBoundaries)
    }

    @Test
    fun skipSelectors_respectTheConfidenceFilter() {
        val state = mixedConfidenceState(filter = 1f).copy(position = 90.seconds)

        // the 0.65 boundary at 2:00 is filtered out; next jumps straight to 3:00
        assertThat(state.nextAdBoundary()).isEqualTo(boundary(3.minutes, confidence = 0.9f))
        // the 0.3 boundary at 1:00 behind us is filtered out too
        assertThat(state.previousAdBoundary()).isNull()
    }

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

    @Test
    fun platformSkipTargets_usePlayerPositionNotStatePosition() {
        // state position (30s) would pick different targets than the player's live 6:00
        val state = state(30.seconds, listOf(1.minutes, 5.minutes, 10.minutes))

        assertThat(state.adBoundarySkipForwardTarget("guid", 6.minutes)).isEqualTo(10.minutes)
        assertThat(state.adBoundarySkipBackTarget("guid", 6.minutes)).isEqualTo(5.minutes)
    }

    @Test
    fun platformSkipTargets_nullForADifferentOrMissingEpisode() {
        val state = state(30.seconds, listOf(1.minutes, 5.minutes))

        assertThat(state.adBoundarySkipForwardTarget("other-guid", 30.seconds)).isNull()
        assertThat(state.adBoundarySkipBackTarget("other-guid", 2.minutes)).isNull()
        assertThat(state.adBoundarySkipForwardTarget(null, 30.seconds)).isNull()
    }

    @Test
    fun platformSkipTargets_nullWhenNoBoundaryInThatDirection() {
        val state = state(Duration.ZERO, listOf(1.minutes, 5.minutes))

        // before the first boundary / past the last one → callers fall back to 15s/30s
        assertThat(state.adBoundarySkipBackTarget("guid", 30.seconds)).isNull()
        assertThat(state.adBoundarySkipForwardTarget("guid", 6.minutes)).isNull()
    }

    @Test
    fun platformSkipTargets_nullWhenEpisodeHasNoBoundaries() {
        val state = state(5.minutes)

        assertThat(state.adBoundarySkipForwardTarget("guid", 5.minutes)).isNull()
        assertThat(state.adBoundarySkipBackTarget("guid", 5.minutes)).isNull()
    }

    @Test
    fun platformSkipTargets_respectTheConfidenceFilter() {
        val state = mixedConfidenceState(filter = 1f)

        // only the 0.9-confidence boundary at 3:00 survives the filter
        assertThat(state.adBoundarySkipForwardTarget("guid", 90.seconds)).isEqualTo(3.minutes)
        assertThat(state.adBoundarySkipBackTarget("guid", 4.minutes)).isEqualTo(3.minutes)
    }
}
