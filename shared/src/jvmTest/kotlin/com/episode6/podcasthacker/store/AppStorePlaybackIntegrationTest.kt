package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.playback.PlaybackMetadata
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.podcasthacker.playback.PodcastPlayer
import com.episode6.podcasthacker.store.sideeffects.PlaybackSideEffects
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffectMiddleware
import com.episode6.redux.testsupport.runStoreTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the full play flow through a real store: PlayEpisode → SetNowPlaying → player
 * load → live PlayerState merged back into AppState. The player is a stateful mock whose
 * commands mutate a PlayerState flow the way a real media engine would. Also a
 * regression test for the load/SetNowPlaying dispatch race: the player's post-load state
 * emission can be reduced before SetNowPlaying is, and must not be lost.
 */
class AppStorePlaybackIntegrationTest {

    private val episode = Episode(
        guid = "guid-1",
        feedUrl = "https://example.com/feed.xml",
        title = "Test Episode",
        audioUrl = "https://example.com/ep1.mp3",
        playbackPosition = 90.seconds,
    )

    private val playerState = MutableStateFlow(PlayerState())

    // gotcha: value-class args (kotlin.time.Duration) can't be read via arg<T>(n) inside
    // an answers block (mockk hands over the raw underlying Long), so the load answer
    // uses the known test values instead of the startAt arg
    private val player = mockk<PodcastPlayer> {
        every { state } returns playerState
        every { load(any(), any(), any(), any()) } answers {
            val metadata = arg<PlaybackMetadata>(1)
            playerState.value = PlayerState(
                episodeGuid = metadata.episodeGuid,
                status = PlayerStatus.Playing,
                position = episode.playbackPosition,
                duration = 30.minutes,
            )
        }
        every { pause() } answers { playerState.update { it.copy(status = PlayerStatus.Paused) } }
        every { stop() } answers { playerState.update { it.copy(status = PlayerStatus.Idle) } }
    }
    private val episodeRepository = mockk<EpisodeRepository> {
        coEvery { episode(episode.guid) } returns episode
        coEvery { lastPlayedEpisode() } returns null
        coEvery { setPlaybackPosition(any(), any()) } just Runs
        coEvery { markPlayed(any(), any()) } just Runs
    }
    private val downloadsRepository = mockk<DownloadsRepository> {
        every { downloadedFileExists(episode.guid) } returns true
        every { downloadFilePath(episode.guid) } returns "/downloads/abc.mp3".toPath()
        coEvery { adBoundaryCandidates(episode.guid) } returns emptyList()
        coEvery { downloadLog(episode.guid) } returns emptyList()
    }
    private val subscriptionRepository = mockk<SubscriptionRepository> {
        coEvery { podcast(episode.feedUrl) } returns Podcast(feedUrl = episode.feedUrl, title = "Test Podcast")
    }

    private fun CoroutineScope.playbackStore(): AppStore {
        val playback = object : PlaybackSideEffects {}
        return StoreFlow(
            scope = this,
            initialValue = AppState(),
            reducer = AppState::reduce,
            middlewares = listOf(
                SideEffectMiddleware(
                    setOf(
                        playback.observePlayerState(player),
                        playback.playEpisode(player, episodeRepository, downloadsRepository),
                        playback.playerCommands(player),
                        playback.persistPlaybackPosition(player, episodeRepository),
                        playback.restoreNowPlaying(episodeRepository, subscriptionRepository, downloadsRepository),
                    )
                )
            ),
        )
    }

    @Test
    fun playEpisode_landsInPlayingState() = runStoreTest(storeBuilder = { playbackStore() }) { store ->
        store.dispatch(PlayEpisode(episode.guid))

        val nowPlaying = store.first { it.nowPlaying?.isPlaying == true }.nowPlaying!!
        kotlin.test.assertEquals(episode.title, nowPlaying.episodeTitle)
        kotlin.test.assertEquals(90.seconds, nowPlaying.position)
        kotlin.test.assertEquals(30.minutes, nowPlaying.duration)
    }

    @Test
    fun togglePlayPause_landsInPausedState() = runStoreTest(storeBuilder = { playbackStore() }) { store ->
        store.dispatch(PlayEpisode(episode.guid))
        store.first { it.nowPlaying?.isPlaying == true }

        store.dispatch(TogglePlayPause)

        store.first { it.nowPlaying?.isPlaying == false }
    }

    @Test
    fun stopPlayback_clearsNowPlaying() = runStoreTest(storeBuilder = { playbackStore() }) { store ->
        store.dispatch(PlayEpisode(episode.guid))
        store.first { it.nowPlaying?.isPlaying == true }

        store.dispatch(StopPlayback)

        store.first { it.nowPlaying == null }
    }

    // the coEvery overrides live in the storeBuilder lambdas below: the restore side
    // effect reads lastPlayedEpisode() as soon as the store is built, before the test body

    @Test
    fun coldStart_restoresLastPlayedEpisodeAsPausedBar() = runStoreTest(storeBuilder = {
        coEvery { episodeRepository.lastPlayedEpisode() } returns episode
        playbackStore()
    }) { store ->
        val nowPlaying = store.first { it.nowPlaying != null }.nowPlaying!!

        kotlin.test.assertEquals(episode.title, nowPlaying.episodeTitle)
        kotlin.test.assertEquals("Test Podcast", nowPlaying.podcastTitle)
        kotlin.test.assertEquals(90.seconds, nowPlaying.position)
        kotlin.test.assertEquals(false, nowPlaying.isPlaying)
    }

    @Test
    fun coldStart_playOnRestoredEpisode_loadsAndPlays() = runStoreTest(storeBuilder = {
        coEvery { episodeRepository.lastPlayedEpisode() } returns episode
        playbackStore()
    }) { store ->
        store.first { it.nowPlaying != null }

        store.dispatch(TogglePlayPause)

        val nowPlaying = store.first { it.nowPlaying?.isPlaying == true }.nowPlaying!!
        kotlin.test.assertEquals(90.seconds, nowPlaying.position)
    }
}
