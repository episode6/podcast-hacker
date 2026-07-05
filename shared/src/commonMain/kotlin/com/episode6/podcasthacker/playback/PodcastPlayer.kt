package com.episode6.podcasthacker.playback

import com.episode6.podcasthacker.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okio.Path
import kotlin.time.Duration

/**
 * Platform audio player for downloaded episode files. Implementations marshal commands
 * to whatever thread their media engine requires, so all methods are safe to call from
 * any thread and return immediately; results surface via [state].
 */
interface PodcastPlayer {
    val state: StateFlow<PlayerState>

    /**
     * Load a local audio [file] and begin buffering; playback starts from [startAt] once
     * ready (when [playWhenReady]). Replaces any previously loaded media.
     */
    fun load(file: Path, metadata: PlaybackMetadata, startAt: Duration = Duration.ZERO, playWhenReady: Boolean = true)

    /** Resume playback (restarts from the beginning after [PlayerStatus.Ended]). */
    fun play()
    fun pause()
    fun seekTo(position: Duration)
    fun setSpeed(speed: Float)

    /**
     * Halt playback and unload the media. Status returns to [PlayerStatus.Idle];
     * [PlayerState.episodeGuid] and the last position are retained so observers (e.g.
     * position persistence) can record where playback left off.
     */
    fun stop()
}

/** Shown on platform media surfaces (notification, lock screen, now-playing info). */
data class PlaybackMetadata(
    val episodeGuid: String,
    val title: String,
    val podcastTitle: String? = null,
    val artworkUrl: String? = null,
)

data class PlayerState(
    val episodeGuid: String? = null,
    val status: PlayerStatus = PlayerStatus.Idle,
    val position: Duration = Duration.ZERO,
    /** null until the media engine reports it (feed-supplied duration lives in AppState). */
    val duration: Duration? = null,
    val speed: Float = 1f,
)

sealed interface PlayerStatus {
    data object Idle : PlayerStatus
    data object Loading : PlayerStatus
    data object Playing : PlayerStatus
    data object Paused : PlayerStatus
    data object Ended : PlayerStatus
    data class Error(val message: String) : PlayerStatus
}

internal expect fun PlatformContext.createPodcastPlayer(scope: CoroutineScope): PodcastPlayer
