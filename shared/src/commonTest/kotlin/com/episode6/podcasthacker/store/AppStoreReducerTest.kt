package com.episode6.podcasthacker.store

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.AdFingerprint
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.redux.Action
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppStoreReducerTest {

    private val playing = NowPlayingState(episodeGuid = "guid-1", episodeTitle = "test episode", isPlaying = true)

    @Test
    fun setNowPlaying_updatesState() {
        val result = AppState().reduce(SetNowPlaying(playing))

        assertThat(result.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun setNowPlayingNull_clearsState() {
        val result = AppState(nowPlaying = playing).reduce(SetNowPlaying(null))

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun unknownAction_leavesStateUntouched() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(object : Action {})

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun setSubscriptions_replacesList() {
        val podcasts = listOf(Podcast(feedUrl = "https://example.com/feed.xml", title = "Test"))

        val result = AppState().reduce(SetSubscriptions(podcasts))

        assertThat(result.subscriptions).isEqualTo(podcasts)
    }

    @Test
    fun setEpisodes_replacesMap_andEpisodeLookupFindsByGuid() {
        val episode = Episode(guid = "g1", feedUrl = "feed", title = "Ep One")

        val result = AppState().reduce(SetEpisodes(mapOf("feed" to listOf(episode))))

        assertThat(result.episodesByFeed).isEqualTo(mapOf("feed" to listOf(episode)))
        assertThat(result.episode("g1")).isEqualTo(episode)
        assertThat(result.episode("missing")).isNull()
    }

    @Test
    fun setFeedSyncing_addsAndRemovesFromSet() {
        val feedUrl = "https://example.com/feed.xml"

        val syncing = AppState().reduce(SetFeedSyncing(feedUrl, true))
        assertThat(syncing.feedSync.syncing).isEqualTo(setOf(feedUrl))

        val done = syncing.reduce(SetFeedSyncing(feedUrl, false))
        assertThat(done.feedSync.syncing).isEmpty()
    }

    @Test
    fun setPlayerState_mergesMatchingEpisode() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(
                PlayerState(
                    episodeGuid = "guid-1",
                    status = PlayerStatus.Paused,
                    position = 90.seconds,
                    duration = 30.minutes,
                    speed = 1.5f,
                )
            )
        )

        assertThat(result.nowPlaying).isEqualTo(
            playing.copy(
                isPlaying = false,
                position = 90.seconds,
                duration = 30.minutes,
                speed = 1.5f,
            )
        )
    }

    @Test
    fun setPlayerState_preservesAdBoundaries() {
        val boundaries = listOf(AdBoundary(60.seconds, AdBoundary.Source.DaiSlot, AdBoundary.Role.Join, confidence = 0.8f))
        val state = AppState(nowPlaying = playing.copy(adBoundaries = boundaries))

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying?.adBoundaries).isEqualTo(boundaries)
    }

    @Test
    fun setAdBoundaryConfidenceFilter_setsAndClamps() {
        val state = AppState(nowPlaying = playing)

        assertThat(state.reduce(SetAdBoundaryConfidenceFilter(0.7f)).nowPlaying?.adBoundaryConfidenceFilter)
            .isEqualTo(0.7f)
        assertThat(state.reduce(SetAdBoundaryConfidenceFilter(3f)).nowPlaying?.adBoundaryConfidenceFilter)
            .isEqualTo(1f)
        assertThat(state.reduce(SetAdBoundaryConfidenceFilter(-1f)).nowPlaying?.adBoundaryConfidenceFilter)
            .isEqualTo(0f)
    }

    @Test
    fun setAdBoundaryConfidenceFilter_ignoredWhileNothingPlaying() {
        assertThat(AppState().reduce(SetAdBoundaryConfidenceFilter(0.7f)).nowPlaying).isNull()
    }

    @Test
    fun setPlayerState_preservesConfidenceFilter() {
        val state = AppState(nowPlaying = playing.copy(adBoundaryConfidenceFilter = 0.7f))

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying?.adBoundaryConfidenceFilter).isEqualTo(0.7f)
    }

    @Test
    fun setPlayerState_ignoresOtherEpisodes() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "other-guid", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun setPlayerState_ignoredWhileNothingPlaying() {
        val result = AppState().reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing))
        )

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun setPlayerState_surfacesErrors() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Error("boom")))
        )

        assertThat(result.nowPlaying?.error).isEqualTo("boom")
    }

    private val firstConfirmed = ConfirmedAd(1.minutes..2.minutes, fingerprintId = "fp-1")

    @Test
    fun markAdRangeConfirmed_appendsToConfirmedRanges() {
        val state = AppState(nowPlaying = playing.copy(confirmedAdRanges = listOf(firstConfirmed)))

        val result = state.reduce(MarkAdRangeConfirmed("guid-1", 5.minutes, 7.minutes, "fp-2"))

        assertThat(result.nowPlaying?.confirmedAdRanges)
            .isEqualTo(listOf(firstConfirmed, ConfirmedAd(5.minutes..7.minutes, "fp-2")))
    }

    @Test
    fun markAdRangeConfirmed_reconfirmingSameFingerprint_doesNotDuplicate() {
        // fingerprint ids are content-derived, so re-confirming the same creative
        // hands back the same id
        val state = AppState(nowPlaying = playing.copy(confirmedAdRanges = listOf(firstConfirmed)))

        val result = state.reduce(MarkAdRangeConfirmed("guid-1", 1.minutes, 2.minutes, "fp-1"))

        assertThat(result.nowPlaying?.confirmedAdRanges).isEqualTo(listOf(firstConfirmed))
    }

    @Test
    fun markAdRangeConfirmed_ignoresOtherEpisodes() {
        val state = AppState(nowPlaying = playing)

        val result = state.reduce(MarkAdRangeConfirmed("other-guid", 5.minutes, 7.minutes, "fp-2"))

        assertThat(result.nowPlaying).isEqualTo(playing)
    }

    @Test
    fun markAdRangeConfirmed_ignoredWhileNothingPlaying() {
        val result = AppState().reduce(MarkAdRangeConfirmed("guid-1", 5.minutes, 7.minutes, "fp-2"))

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun markAdRangeUnconfirmed_removesTheMatchingRange() {
        val other = ConfirmedAd(5.minutes..7.minutes, fingerprintId = "fp-2")
        val state = AppState(nowPlaying = playing.copy(confirmedAdRanges = listOf(firstConfirmed, other)))

        val result = state.reduce(MarkAdRangeUnconfirmed("guid-1", "fp-1"))

        assertThat(result.nowPlaying?.confirmedAdRanges).isEqualTo(listOf(other))
    }

    @Test
    fun markAdRangeUnconfirmed_ignoresOtherEpisodes() {
        val marked = playing.copy(confirmedAdRanges = listOf(firstConfirmed))
        val state = AppState(nowPlaying = marked)

        val result = state.reduce(MarkAdRangeUnconfirmed("other-guid", "fp-1"))

        assertThat(result.nowPlaying).isEqualTo(marked)
    }

    @Test
    fun markAdRangeUnconfirmed_ignoredWhileNothingPlaying() {
        val result = AppState().reduce(MarkAdRangeUnconfirmed("guid-1", "fp-1"))

        assertThat(result.nowPlaying).isNull()
    }

    @Test
    fun setPlayerState_preservesConfirmedRanges() {
        val ranges = listOf(firstConfirmed)
        val state = AppState(nowPlaying = playing.copy(confirmedAdRanges = ranges))

        val result = state.reduce(
            SetPlayerState(PlayerState(episodeGuid = "guid-1", status = PlayerStatus.Playing, position = 5.seconds))
        )

        assertThat(result.nowPlaying?.confirmedAdRanges).isEqualTo(ranges)
    }

    @Test
    fun setAdFingerprints_replacesOnlyThatFeedsEntry() {
        val existing = AdFingerprint("fp-a", AdFingerprint.Provenance.DiffProven, 30.seconds, 480_000L)
        val state = AppState(adFingerprints = mapOf("feed-1" to listOf(existing)))
        val incoming = listOf(AdFingerprint("fp-b", AdFingerprint.Provenance.HumanConfirmed, 45.seconds, 720_000L))

        val replaced = state.reduce(SetAdFingerprints("feed-1", incoming))
        assertThat(replaced.adFingerprints).isEqualTo(mapOf("feed-1" to incoming))

        val added = state.reduce(SetAdFingerprints("feed-2", incoming))
        assertThat(added.adFingerprints)
            .isEqualTo(mapOf("feed-1" to listOf(existing), "feed-2" to incoming))
    }

    @Test
    fun setFeedSyncError_setsAndClears() {
        val withError = AppState().reduce(SetFeedSyncError("boom"))
        assertThat(withError.feedSync.lastError).isEqualTo("boom")

        val cleared = withError.reduce(SetFeedSyncError(null))
        assertThat(cleared.feedSync.lastError).isNull()
    }
}
