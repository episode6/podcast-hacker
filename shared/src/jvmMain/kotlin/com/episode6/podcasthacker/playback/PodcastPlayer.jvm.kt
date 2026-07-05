package com.episode6.podcasthacker.playback

import com.episode6.podcasthacker.PlatformContext
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okio.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

internal actual fun PlatformContext.createPodcastPlayer(scope: CoroutineScope): PodcastPlayer =
    LibVlcPodcastPlayer()

/**
 * Desktop player on libvlc via our own [LibVlc] JNA binding (bundled libvlc preferred,
 * system VLC as fallback — see [LibVlcDiscovery]). Engine init happens lazily on the
 * first [load], so merely constructing the player (e.g. in CI test runs) never touches
 * native code; an unavailable libvlc surfaces as an error state.
 */
internal class LibVlcPodcastPlayer : PodcastPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val engineLock = Any()
    private var engine: Engine? = null
    @Volatile private var ended = false

    override fun load(file: Path, metadata: PlaybackMetadata, startAt: Duration, playWhenReady: Boolean) {
        _state.update {
            PlayerState(episodeGuid = metadata.episodeGuid, status = PlayerStatus.Loading, position = startAt, speed = it.speed)
        }
        val engine = engineOrNull() ?: return
        ended = false
        engine.run {
            val media = vlc.libvlc_media_new_path(instance, file.toString())
            if (media == null) {
                _state.update { it.copy(status = PlayerStatus.Error("couldn't open ${file.name}")) }
                return
            }
            if (startAt > Duration.ZERO) {
                vlc.libvlc_media_add_option(media, ":start-time=${startAt.toDouble(DurationUnit.SECONDS)}")
            }
            vlc.libvlc_media_player_set_media(player, media)
            vlc.libvlc_media_release(media) // the player holds its own reference
            if (playWhenReady) {
                vlc.libvlc_media_player_play(player)
                vlc.libvlc_media_player_set_rate(player, _state.value.speed)
            }
        }
    }

    override fun play() {
        val engine = engineOrNull() ?: return
        if (ended) {
            // a finished libvlc player needs a stop before play restarts the media
            ended = false
            engine.vlc.libvlc_media_player_stop(engine.player)
        }
        engine.vlc.libvlc_media_player_play(engine.player)
        engine.vlc.libvlc_media_player_set_rate(engine.player, _state.value.speed)
    }

    override fun pause() {
        engineOrNull()?.run { vlc.libvlc_media_player_set_pause(player, 1) }
    }

    override fun seekTo(position: Duration) {
        val engine = engineOrNull() ?: return
        ended = false
        engine.vlc.libvlc_media_player_set_time(engine.player, position.inWholeMilliseconds)
        // seeks while paused don't reliably emit TimeChanged
        _state.update { it.copy(position = position) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        engineOrNull()?.run { vlc.libvlc_media_player_set_rate(player, speed) }
    }

    override fun stop() {
        engineOrNull()?.run { vlc.libvlc_media_player_stop(player) }
        ended = false
        // guid + position retained per the PodcastPlayer contract
        _state.update { it.copy(status = PlayerStatus.Idle) }
    }

    private fun engineOrNull(): Engine? = synchronized(engineLock) {
        engine?.let { return it }
        val vlc = LibVlcDiscovery.instance
        if (vlc == null) {
            _state.update {
                it.copy(status = PlayerStatus.Error("playback engine unavailable — libvlc not found"))
            }
            return null
        }
        val result = runCatching { Engine(vlc, eventHandler) }
        result.exceptionOrNull()?.let { e ->
            _state.update { it.copy(status = PlayerStatus.Error("playback engine failed to start (${e.message})")) }
            return null
        }
        return result.getOrThrow().also { engine = it }
    }

    /**
     * Runs on libvlc's event threads: must not call back into libvlc, so time/length
     * come from the event payload rather than getter calls.
     */
    private val eventHandler = LibVlcEventCallback { event, _ ->
        when (event.eventType()) {
            LibVlcEvent.MediaPlayerPlaying ->
                if (!ended) _state.update { it.copy(status = PlayerStatus.Playing) }
            LibVlcEvent.MediaPlayerPaused ->
                if (!ended) _state.update { it.copy(status = PlayerStatus.Paused) }
            LibVlcEvent.MediaPlayerEndReached -> {
                ended = true
                _state.update { it.copy(status = PlayerStatus.Ended, position = it.duration ?: it.position) }
            }
            LibVlcEvent.MediaPlayerEncounteredError ->
                _state.update { it.copy(status = PlayerStatus.Error("playback error")) }
            LibVlcEvent.MediaPlayerTimeChanged ->
                if (!ended) _state.update { it.copy(position = event.eventInt64Payload().coerceAtLeast(0).milliseconds) }
            LibVlcEvent.MediaPlayerLengthChanged ->
                event.eventInt64Payload().takeIf { it > 0 }?.let { length ->
                    _state.update { it.copy(duration = length.milliseconds) }
                }
        }
    }

    /** One libvlc instance + media player reused across loads. */
    private class Engine(val vlc: LibVlc, callback: LibVlcEventCallback) {
        val instance: Pointer = requireNotNull(vlc.libvlc_new(ARGS.size, ARGS)) {
            "libvlc_new failed: ${vlc.libvlc_errmsg()}"
        }
        val player: Pointer = requireNotNull(vlc.libvlc_media_player_new(instance)) {
            "libvlc_media_player_new failed: ${vlc.libvlc_errmsg()}"
        }

        // strong reference lives as long as the engine: JNA callbacks passed to native
        // code are otherwise eligible for gc
        @Suppress("unused") val eventCallback: LibVlcEventCallback = callback

        init {
            val eventManager = vlc.libvlc_media_player_event_manager(player)
            intArrayOf(
                LibVlcEvent.MediaPlayerPlaying,
                LibVlcEvent.MediaPlayerPaused,
                LibVlcEvent.MediaPlayerEndReached,
                LibVlcEvent.MediaPlayerEncounteredError,
                LibVlcEvent.MediaPlayerTimeChanged,
                LibVlcEvent.MediaPlayerLengthChanged,
            ).forEach { vlc.libvlc_event_attach(eventManager, it, callback, null) }
        }

        private companion object {
            val ARGS = arrayOf("--no-video", "--quiet")
        }
    }
}
