package com.episode6.podcasthacker.playback

import com.episode6.podcasthacker.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okio.Path
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

internal actual fun PlatformContext.createPodcastPlayer(scope: CoroutineScope): PodcastPlayer =
    VlcjPodcastPlayer()

/**
 * Desktop player backed by vlcj/libvlc (requires VLC to be installed). JavaFX Media was
 * the original plan but its Linux backend dlopens a system libavcodec no newer than the
 * ones it shipped against (<= .60 for JavaFX 21, <= .61 for 25), which makes it a dead
 * end on current distros — vlcj was the documented fallback.
 *
 * Native discovery happens lazily on the first [load], so merely constructing the player
 * (e.g. in CI test runs) never touches libvlc; a missing VLC surfaces as an error state.
 */
internal class VlcjPodcastPlayer : PodcastPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val playerLock = Any()
    private var component: AudioPlayerComponent? = null
    @Volatile private var ended = false

    override fun load(file: Path, metadata: PlaybackMetadata, startAt: Duration, playWhenReady: Boolean) {
        _state.update {
            PlayerState(episodeGuid = metadata.episodeGuid, status = PlayerStatus.Loading, position = startAt, speed = it.speed)
        }
        val player = playerOrNull() ?: return
        ended = false
        val options = buildList {
            if (startAt > Duration.ZERO) add(":start-time=${startAt.toDouble(DurationUnit.SECONDS)}")
        }.toTypedArray()
        if (playWhenReady) {
            player.media().play(file.toString(), *options)
            player.controls().setRate(_state.value.speed)
        } else {
            player.media().prepare(file.toString(), *options)
        }
    }

    override fun play() {
        val player = playerOrNull() ?: return
        if (ended) {
            // a finished libvlc player needs a stop before play restarts the media
            ended = false
            player.controls().stop()
        }
        player.controls().play()
        player.controls().setRate(_state.value.speed)
    }

    override fun pause() {
        playerOrNull()?.controls()?.setPause(true)
    }

    override fun seekTo(position: Duration) {
        val player = playerOrNull() ?: return
        ended = false
        player.controls().setTime(position.inWholeMilliseconds)
        // seeks while paused don't reliably emit timeChanged
        _state.update { it.copy(position = position) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        playerOrNull()?.controls()?.setRate(speed)
    }

    override fun stop() {
        playerOrNull()?.controls()?.stop()
        ended = false
        // guid + position retained per the PodcastPlayer contract
        _state.update { it.copy(status = PlayerStatus.Idle) }
    }

    private fun playerOrNull(): MediaPlayer? = synchronized(playerLock) {
        component?.let { return it.mediaPlayer() }
        val result = runCatching { AudioPlayerComponent() }
        result.exceptionOrNull()?.let { e ->
            _state.update {
                it.copy(status = PlayerStatus.Error("VLC not found — install VLC to enable playback (${e.message})"))
            }
            return null
        }
        return result.getOrThrow()
            .also { component = it }
            .mediaPlayer()
            .also { it.events().addMediaPlayerEventListener(StateListener()) }
    }

    /**
     * Note: vlcj forbids calling back into libvlc from its event threads, so these
     * callbacks only touch [_state].
     */
    private inner class StateListener : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) {
            if (!ended) _state.update { it.copy(status = PlayerStatus.Playing) }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            if (!ended) _state.update { it.copy(status = PlayerStatus.Paused) }
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            ended = true
            _state.update { it.copy(status = PlayerStatus.Ended, position = it.duration ?: it.position) }
        }

        override fun error(mediaPlayer: MediaPlayer) {
            _state.update { it.copy(status = PlayerStatus.Error("playback error")) }
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            if (!ended) _state.update { it.copy(position = newTime.coerceAtLeast(0).milliseconds) }
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (newLength > 0) _state.update { it.copy(duration = newLength.milliseconds) }
        }
    }
}
