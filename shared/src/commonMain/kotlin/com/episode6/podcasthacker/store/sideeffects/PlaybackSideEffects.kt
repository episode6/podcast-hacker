package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.playback.PlaybackMetadata
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.podcasthacker.playback.PodcastPlayer
import com.episode6.podcasthacker.store.AppState
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
import com.episode6.podcasthacker.store.nextAdBoundary
import com.episode6.podcasthacker.store.previousAdBoundary
import com.episode6.redux.sideeffects.SideEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ContributesTo(AppScope::class)
interface PlaybackSideEffects {

    /**
     * Live player state flows into AppState. A [SetNowPlaying] action also re-syncs the
     * current player state: the player's post-load emission can race ahead of the
     * SetNowPlaying that makes the reducer willing to merge it, and a StateFlow won't
     * repeat itself.
     */
    @Provides @IntoSet fun observePlayerState(player: PodcastPlayer): SideEffect<AppState> =
        sideEffect {
            merge(
                actions.filterIsInstance<SetNowPlaying>()
                    .mapNotNull { action -> action.nowPlaying?.let { SetPlayerState(player.state.value) } },
                player.state.mapActions { listOf(SetPlayerState(it)) },
            )
        }

    /**
     * Playback is download-first (tacita needs whole files), so playing an episode means
     * loading its downloaded file; an undownloaded episode surfaces as a nowPlaying error
     * (the ui shouldn't offer Play in that case, this is a guard).
     */
    @Provides @IntoSet fun playEpisode(
        player: PodcastPlayer,
        episodeRepository: EpisodeRepository,
        downloadsRepository: DownloadsRepository,
    ): SideEffect<AppState> = sideEffect {
        actions.filterIsInstance<PlayEpisode>().transform { action ->
            val episode = episodeRepository.episode(action.episodeGuid) ?: return@transform
            val podcast = currentState().subscriptions.firstOrNull { it.feedUrl == episode.feedUrl }
            val nowPlaying = NowPlayingState(
                episodeGuid = episode.guid,
                episodeTitle = episode.title,
                podcastTitle = podcast?.title,
                artworkUrl = podcast?.artworkUrl,
                position = episode.playbackPosition,
                duration = episode.duration,
                adBoundaries = downloadsRepository.adBoundaryCandidates(episode.guid),
            )
            if (!downloadsRepository.downloadedFileExists(episode.guid)) {
                emit(SetNowPlaying(nowPlaying.copy(error = "Episode isn't downloaded yet")))
                return@transform
            }
            emit(SetNowPlaying(nowPlaying.copy(isLoading = true)))
            val result = runCatching {
                player.load(
                    file = downloadsRepository.downloadFilePath(episode.guid),
                    metadata = PlaybackMetadata(
                        episodeGuid = episode.guid,
                        title = episode.title,
                        podcastTitle = podcast?.title,
                        artworkUrl = podcast?.artworkUrl,
                    ),
                    startAt = episode.playbackPosition,
                )
            }
            result.exceptionOrNull()?.let {
                if (it is CancellationException) throw it
                emit(SetNowPlaying(nowPlaying.copy(error = it.message ?: "failed to start playback")))
                return@transform
            }
            // stamped only after a successful load so failed attempts don't pollute
            // the Recently Played list
            episodeRepository.markPlayed(episode.guid)
        }
    }

    /** A failing player command mustn't kill the whole command pipeline. */
    @Provides @IntoSet fun playerCommands(player: PodcastPlayer): SideEffect<AppState> =
        sideEffect {
            actions.transform { action ->
                val nowPlaying = currentState().nowPlaying
                val result = runCatching {
                    when (action) {
                        TogglePlayPause -> if (nowPlaying?.isPlaying == true) player.pause() else player.play()
                        is SeekTo -> player.seekTo(action.position.clampedTo(nowPlaying))
                        is SeekBy -> nowPlaying?.let { player.seekTo((it.position + action.offset).clampedTo(it)) }
                        SkipToNextAdBoundary -> nowPlaying?.nextAdBoundary()
                            ?.let { player.seekTo(it.position.clampedTo(nowPlaying)) }
                        SkipToPreviousAdBoundary -> nowPlaying?.previousAdBoundary()
                            ?.let { player.seekTo(it.position.clampedTo(nowPlaying)) }
                        is SetPlaybackSpeed -> player.setSpeed(action.speed)
                        StopPlayback -> player.stop()
                        else -> {}
                    }
                }
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (action == StopPlayback) emit(SetNowPlaying(null))
            }
        }

    /** Persist playback position to Room: every ~10s of playback and when playback pauses/ends. */
    @Provides @IntoSet fun persistPlaybackPosition(
        player: PodcastPlayer,
        episodeRepository: EpisodeRepository,
    ): SideEffect<AppState> = sideEffect {
        merge(
            actions.filter { false },
            player.state.positionsToPersist().transform {
                episodeRepository.setPlaybackPosition(it.episodeGuid!!, it.position)
            },
        )
    }
}

private fun Duration.clampedTo(nowPlaying: NowPlayingState?): Duration =
    coerceIn(Duration.ZERO, nowPlaying?.duration ?: Duration.INFINITE)

/**
 * Filters a player-state stream down to the moments a position is worth persisting:
 * every [minProgress] of playback progress and on any transition out of Playing (pause,
 * end of media, stop — stop retains the episode guid per the [PodcastPlayer] contract).
 */
internal fun Flow<PlayerState>.positionsToPersist(minProgress: Duration = 10.seconds): Flow<PlayerState> = flow {
    var previous: PlayerState? = null
    var lastPersisted: Duration? = null
    collect { state ->
        val prev = previous
        previous = state
        if (state.episodeGuid == null) return@collect
        if (state.episodeGuid != prev?.episodeGuid) lastPersisted = null
        val stoppedPlaying = prev?.status == PlayerStatus.Playing &&
            state.status != PlayerStatus.Playing &&
            state.episodeGuid == prev.episodeGuid
        val progressed = state.status == PlayerStatus.Playing &&
            lastPersisted.let { it == null || (state.position - it).absoluteValue >= minProgress }
        if (stoppedPlaying || progressed) {
            lastPersisted = state.position
            emit(state)
        }
    }
}
