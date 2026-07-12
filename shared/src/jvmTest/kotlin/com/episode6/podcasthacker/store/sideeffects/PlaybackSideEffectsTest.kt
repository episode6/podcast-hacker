package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.progress.EpisodeProgress
import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.playback.PlaybackMetadata
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.podcasthacker.playback.PodcastPlayer
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.ImportEpisodeProgress
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.PlayEpisode
import com.episode6.podcasthacker.store.SeekBy
import com.episode6.podcasthacker.store.SeekTo
import com.episode6.podcasthacker.store.SetNowPlaying
import com.episode6.podcasthacker.store.SetPlaybackSpeed
import com.episode6.podcasthacker.store.SetPlayerState
import com.episode6.podcasthacker.store.SkipToNextAdBoundary
import com.episode6.podcasthacker.store.SkipToPreviousAdBoundary
import com.episode6.podcasthacker.store.StopPlayback
import com.episode6.podcasthacker.store.TogglePlayPause
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlaybackSideEffectsTest {

    private val episode = Episode(
        guid = "guid-1",
        feedUrl = "https://example.com/feed.xml",
        title = "Test Episode",
        audioUrl = "https://example.com/ep1.mp3",
        duration = 30.minutes,
        playbackPosition = 90.seconds,
    )
    private val podcast = Podcast(
        feedUrl = episode.feedUrl,
        title = "Test Podcast",
        artworkUrl = "https://example.com/art.png",
    )
    private val downloadPath = "/downloads/abc.mp3".toPath()
    private val nowPlaying = NowPlayingState(
        episodeGuid = episode.guid,
        episodeTitle = episode.title,
        podcastTitle = podcast.title,
        artworkUrl = podcast.artworkUrl,
        position = episode.playbackPosition,
        duration = episode.duration,
    )

    private val sideEffects = object : PlaybackSideEffects {}
    private val playerState = MutableStateFlow(PlayerState())
    private val player = mockk<PodcastPlayer>(relaxUnitFun = true) {
        every { state } returns playerState
    }
    private val episodeRepository = mockk<EpisodeRepository> {
        coEvery { episode(episode.guid) } returns episode
        coEvery { setPlaybackPosition(any(), any()) } just Runs
        coEvery { markPlayed(any(), any()) } just Runs
    }
    private val downloadsRepository = mockk<DownloadsRepository> {
        every { downloadedFileExists(episode.guid) } returns true
        every { downloadFilePath(episode.guid) } returns downloadPath
        coEvery { adBoundaryCandidates(episode.guid) } returns emptyList()
    }

    private fun boundary(position: Duration, confidence: Float = 0.5f) =
        AdBoundary(position, AdBoundary.Source.DiffCut, AdBoundary.Role.Start, confidence)

    @Test
    fun playEpisode_loadsDownloadedFileAtPersistedPosition() = runTest {
        val actions = sideEffects.playEpisode(player, episodeRepository, downloadsRepository)
            .output(PlayEpisode(episode.guid), state = AppState(subscriptions = listOf(podcast)))
            .toList()

        assertThat(actions).containsExactly(SetNowPlaying(nowPlaying.copy(isLoading = true)))
        verify(exactly = 1) {
            player.load(
                file = downloadPath,
                metadata = PlaybackMetadata(
                    episodeGuid = episode.guid,
                    title = episode.title,
                    podcastTitle = podcast.title,
                    artworkUrl = podcast.artworkUrl,
                ),
                startAt = episode.playbackPosition,
            )
        }
    }

    @Test
    fun playEpisode_marksEpisodePlayed() = runTest {
        sideEffects.playEpisode(player, episodeRepository, downloadsRepository)
            .output(PlayEpisode(episode.guid), state = AppState(subscriptions = listOf(podcast)))
            .toList()

        coVerify(exactly = 1) { episodeRepository.markPlayed(episode.guid, any()) }
    }

    @Test
    fun playEpisode_loadFails_doesNotMarkPlayed() = runTest {
        every { player.load(any(), any(), any(), any()) } throws RuntimeException("boom")

        val actions = sideEffects.playEpisode(player, episodeRepository, downloadsRepository)
            .output(PlayEpisode(episode.guid), state = AppState(subscriptions = listOf(podcast)))
            .toList()

        assertThat(actions).containsExactly(
            SetNowPlaying(nowPlaying.copy(isLoading = true)),
            SetNowPlaying(nowPlaying.copy(error = "boom")),
        )
        coVerify(exactly = 0) { episodeRepository.markPlayed(any(), any()) }
    }

    @Test
    fun playEpisode_notDownloaded_surfacesError() = runTest {
        every { downloadsRepository.downloadedFileExists(episode.guid) } returns false

        val actions = sideEffects.playEpisode(player, episodeRepository, downloadsRepository)
            .output(PlayEpisode(episode.guid), state = AppState(subscriptions = listOf(podcast)))
            .toList()

        assertThat(actions).containsExactly(
            SetNowPlaying(nowPlaying.copy(error = "Episode isn't downloaded yet"))
        )
        verify(exactly = 0) { player.load(any(), any(), any(), any()) }
    }

    @Test
    fun togglePlayPause_pausesWhilePlaying_playsWhilePaused() = runTest {
        sideEffects.playerCommands(player)
            .output(TogglePlayPause, state = AppState(nowPlaying = nowPlaying.copy(isPlaying = true)))
            .toList()
        verify(exactly = 1) { player.pause() }

        sideEffects.playerCommands(player)
            .output(TogglePlayPause, state = AppState(nowPlaying = nowPlaying.copy(isPlaying = false)))
            .toList()
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun seekTo_clampsToEpisodeDuration() = runTest {
        sideEffects.playerCommands(player)
            .output(SeekTo(2.minutes), SeekTo(45.minutes), state = AppState(nowPlaying = nowPlaying))
            .toList()

        verify(exactly = 1) { player.seekTo(2.minutes) }
        verify(exactly = 1) { player.seekTo(30.minutes) }
    }

    @Test
    fun seekBy_offsetsFromCurrentPosition_clampingAtZero() = runTest {
        val state = AppState(nowPlaying = nowPlaying.copy(position = 10.seconds))

        sideEffects.playerCommands(player)
            .output(SeekBy(30.seconds), SeekBy((-15).seconds), state = state)
            .toList()

        verify(exactly = 1) { player.seekTo(40.seconds) }
        verify(exactly = 1) { player.seekTo(Duration.ZERO) }
    }

    @Test
    fun playEpisode_loadsAdBoundariesIntoNowPlaying() = runTest {
        val boundaries = listOf(boundary(5.minutes), boundary(15.minutes))
        coEvery { downloadsRepository.adBoundaryCandidates(episode.guid) } returns boundaries

        val actions = sideEffects.playEpisode(player, episodeRepository, downloadsRepository)
            .output(PlayEpisode(episode.guid), state = AppState(subscriptions = listOf(podcast)))
            .toList()

        assertThat(actions).containsExactly(
            SetNowPlaying(nowPlaying.copy(isLoading = true, adBoundaries = boundaries)),
        )
    }

    @Test
    fun skipToNextAdBoundary_seeksToFirstCandidateAfterPosition() = runTest {
        val state = AppState(
            nowPlaying = nowPlaying.copy(
                position = 2.minutes,
                adBoundaries = listOf(boundary(1.minutes), boundary(5.minutes)),
            ),
        )

        sideEffects.playerCommands(player).output(SkipToNextAdBoundary, state = state).toList()

        verify(exactly = 1) { player.seekTo(5.minutes) }
    }

    @Test
    fun skipToNextAdBoundary_respectsConfidenceFilter() = runTest {
        val state = AppState(
            nowPlaying = nowPlaying.copy(
                position = 2.minutes,
                adBoundaries = listOf(boundary(3.minutes, confidence = 0.3f), boundary(5.minutes, confidence = 0.9f)),
                adBoundaryConfidenceFilter = 1f,
            ),
        )

        sideEffects.playerCommands(player).output(SkipToNextAdBoundary, state = state).toList()

        // the low-confidence 3:00 boundary is filtered out of the skip list
        verify(exactly = 1) { player.seekTo(5.minutes) }
    }

    @Test
    fun skipToPreviousAdBoundary_appliesGraceWindow() = runTest {
        val boundaries = listOf(boundary(1.minutes), boundary(5.minutes))

        // just past a boundary: the grace window sends us to the one before it
        sideEffects.playerCommands(player)
            .output(
                SkipToPreviousAdBoundary,
                state = AppState(nowPlaying = nowPlaying.copy(position = 5.minutes + 1.seconds, adBoundaries = boundaries)),
            )
            .toList()
        verify(exactly = 1) { player.seekTo(1.minutes) }

        // well past it: it's a normal previous target
        sideEffects.playerCommands(player)
            .output(
                SkipToPreviousAdBoundary,
                state = AppState(nowPlaying = nowPlaying.copy(position = 5.minutes + 30.seconds, adBoundaries = boundaries)),
            )
            .toList()
        verify(exactly = 1) { player.seekTo(5.minutes) }
    }

    @Test
    fun skipToAdBoundary_noCandidateInDirection_doesNothing() = runTest {
        val state = AppState(
            nowPlaying = nowPlaying.copy(
                position = 2.minutes,
                adBoundaries = listOf(boundary(2.minutes - 1.seconds)), // inside the back grace window, none ahead
            ),
        )

        sideEffects.playerCommands(player)
            .output(SkipToNextAdBoundary, SkipToPreviousAdBoundary, state = state)
            .toList()

        verify(exactly = 0) { player.seekTo(any()) }
    }

    @Test
    fun setPlaybackSpeed_forwardsToPlayer() = runTest {
        sideEffects.playerCommands(player)
            .output(SetPlaybackSpeed(1.5f), state = AppState(nowPlaying = nowPlaying))
            .toList()

        verify(exactly = 1) { player.setSpeed(1.5f) }
    }

    @Test
    fun stopPlayback_stopsPlayerAndClearsNowPlaying() = runTest {
        val actions = sideEffects.playerCommands(player)
            .output(StopPlayback, state = AppState(nowPlaying = nowPlaying))
            .toList()

        assertThat(actions).containsExactly(SetNowPlaying(null))
        verify(exactly = 1) { player.stop() }
    }

    @Test
    fun observePlayerState_feedsPlayerStateIntoStore() = runTest {
        val state = PlayerState(episodeGuid = episode.guid, status = PlayerStatus.Playing, position = 5.seconds)
        playerState.value = state

        val action = sideEffects.observePlayerState(player).output().take(1).toList().single()

        assertThat(action).isEqualTo(SetPlayerState(state))
    }

    @Test
    fun persistPosition_onPauseAndEveryTenSecondsOfPlayback() = runTest {
        playerState.value = PlayerState(episodeGuid = episode.guid, status = PlayerStatus.Playing, position = 0.seconds)
        val job = launch { sideEffects.persistPlaybackPosition(player, episodeRepository).output().toList() }
        runCurrent()
        coVerify(exactly = 1) { episodeRepository.setPlaybackPosition(episode.guid, 0.seconds) }

        // small progress isn't persisted
        playerState.value = playerState.value.copy(position = 5.seconds)
        runCurrent()
        coVerify(exactly = 0) { episodeRepository.setPlaybackPosition(episode.guid, 5.seconds) }

        // crossing the 10s window is
        playerState.value = playerState.value.copy(position = 12.seconds)
        runCurrent()
        coVerify(exactly = 1) { episodeRepository.setPlaybackPosition(episode.guid, 12.seconds) }

        // pausing persists immediately
        playerState.value = playerState.value.copy(status = PlayerStatus.Paused, position = 14.seconds)
        runCurrent()
        coVerify(exactly = 1) { episodeRepository.setPlaybackPosition(episode.guid, 14.seconds) }

        job.cancel()
    }

    @Test
    fun persistPosition_ignoresStatesWithoutAnEpisode() = runTest {
        playerState.value = PlayerState(episodeGuid = null, status = PlayerStatus.Playing, position = 5.seconds)
        val job = launch { sideEffects.persistPlaybackPosition(player, episodeRepository).output().toList() }
        runCurrent()

        coVerify(exactly = 0) { episodeRepository.setPlaybackPosition(any(), any()) }

        job.cancel()
    }

    @Test
    fun importEpisodeProgress_restoresPositionAndLastPlayed_skipsUnknownEpisodes() = runTest {
        val known = EpisodeProgress(
            feedUrl = episode.feedUrl,
            guid = episode.guid,
            positionMs = 90_000,
            lastPlayedAtMs = 1_700_000_000_000,
        )
        val unknown = known.copy(guid = "unknown-guid")
        val positionOnly = EpisodeProgress(feedUrl = episode.feedUrl, guid = "position-only", positionMs = 5_000)
        coEvery { episodeRepository.episode("unknown-guid") } returns null
        coEvery { episodeRepository.episode("position-only") } returns episode.copy(guid = "position-only")

        val actions = sideEffects.importEpisodeProgress(episodeRepository)
            .output(ImportEpisodeProgress(listOf(known, unknown, positionOnly)))
            .toList()

        assertThat(actions).isEmpty()
        coVerify(exactly = 1) {
            episodeRepository.setPlaybackPosition(episode.guid, 90.seconds)
            episodeRepository.markPlayed(episode.guid, Instant.fromEpochMilliseconds(1_700_000_000_000))
            episodeRepository.setPlaybackPosition("position-only", 5.seconds)
        }
        coVerify(exactly = 0) {
            episodeRepository.setPlaybackPosition("unknown-guid", any())
            episodeRepository.markPlayed("position-only", any())
        }
    }

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
