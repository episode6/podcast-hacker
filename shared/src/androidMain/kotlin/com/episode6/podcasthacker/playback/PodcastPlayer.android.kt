package com.episode6.podcasthacker.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.episode6.podcasthacker.PlatformContext
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Path
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal actual fun PlatformContext.createPodcastPlayer(scope: CoroutineScope): PodcastPlayer =
    MediaControllerPodcastPlayer(context, scope)

/**
 * Android player: a MediaController connected to [PlaybackService]'s MediaSession, so the
 * ExoPlayer instance lives in the service (notification, lock screen, audio focus).
 * MediaController requires main-thread access and connects asynchronously; commands are
 * marshaled through [withController], which connects lazily on first use.
 */
internal class MediaControllerPodcastPlayer(
    private val context: Context,
    scope: CoroutineScope,
) : PodcastPlayer {

    private val mainScope = CoroutineScope(scope.coroutineContext + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllerDeferred: Deferred<MediaController>? = null // main thread only

    override fun load(file: Path, metadata: PlaybackMetadata, startAt: Duration, playWhenReady: Boolean) {
        _state.update {
            PlayerState(episodeGuid = metadata.episodeGuid, status = PlayerStatus.Loading, position = startAt, speed = it.speed)
        }
        withController { controller ->
            val item = MediaItem.Builder()
                .setMediaId(metadata.episodeGuid)
                .setUri(Uri.fromFile(File(file.toString())))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(metadata.title)
                        .setArtist(metadata.podcastTitle)
                        .setArtworkUri(metadata.artworkUrl?.let(Uri::parse))
                        .build()
                )
                .build()
            controller.setMediaItem(item, startAt.inWholeMilliseconds)
            controller.prepare()
            controller.playWhenReady = playWhenReady
        }
    }

    override fun play() = withController { controller ->
        // play() alone doesn't restart an ENDED player
        if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
        controller.play()
    }

    override fun pause() = withController { it.pause() }

    override fun seekTo(position: Duration) = withController { it.seekTo(position.inWholeMilliseconds) }

    override fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    override fun stop() = withController { controller ->
        controller.stop()
        // media item (and thus episode guid) is retained per the PodcastPlayer contract
        _state.update { it.copy(status = PlayerStatus.Idle) }
    }

    private fun withController(block: (MediaController) -> Unit) {
        mainScope.launch {
            val deferred = controllerDeferred ?: async { connectController() }.also { controllerDeferred = it }
            block(deferred.await())
        }
    }

    private suspend fun connectController(): MediaController {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().await()
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                _state.update { it.updatedFrom(player) }
            }
        })
        _state.update { it.updatedFrom(controller) }
        startPositionTicker(controller)
        return controller
    }

    /** The Player api doesn't emit position updates; poll while playing. */
    private fun startPositionTicker(controller: MediaController) {
        mainScope.launch {
            while (isActive) {
                if (controller.isPlaying) _state.update { it.updatedFrom(controller) }
                delay(500)
            }
        }
    }
}

private fun PlayerState.updatedFrom(player: Player): PlayerState = copy(
    episodeGuid = player.currentMediaItem?.mediaId ?: episodeGuid,
    status = when {
        player.playerError != null -> PlayerStatus.Error(player.playerError?.message ?: "playback error")
        player.playbackState == Player.STATE_BUFFERING -> PlayerStatus.Loading
        player.playbackState == Player.STATE_ENDED -> PlayerStatus.Ended
        player.playbackState == Player.STATE_READY ->
            if (player.playWhenReady) PlayerStatus.Playing else PlayerStatus.Paused
        else -> PlayerStatus.Idle // STATE_IDLE: nothing loaded / stopped
    },
    position = player.currentPosition.coerceAtLeast(0).milliseconds,
    duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.milliseconds ?: duration,
    speed = player.playbackParameters.speed,
)

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener({
        try {
            cont.resume(get())
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }, { it.run() })
    cont.invokeOnCancellation { cancel(false) }
}
