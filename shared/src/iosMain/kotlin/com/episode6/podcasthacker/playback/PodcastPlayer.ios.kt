@file:OptIn(ExperimentalForeignApi::class)

package com.episode6.podcasthacker.playback

import com.episode6.podcasthacker.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okio.Path
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal actual fun PlatformContext.createPodcastPlayer(scope: CoroutineScope): PodcastPlayer =
    AvPodcastPlayer()

/**
 * iOS player: AVPlayer + AVAudioSession(.playback), publishing to MPNowPlayingInfoCenter
 * and handling MPRemoteCommandCenter (lock screen / control center) commands.
 * Best-effort and compile-verified only, per the project plan.
 */
internal class AvPodcastPlayer : PodcastPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val player = AVPlayer()
    private var metadata: PlaybackMetadata? = null
    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var ended = false

    init {
        MPRemoteCommandCenter.sharedCommandCenter().apply {
            playCommand.addTargetWithHandler { play(); MPRemoteCommandHandlerStatusSuccess }
            pauseCommand.addTargetWithHandler { pause(); MPRemoteCommandHandlerStatusSuccess }
            skipBackwardCommand.preferredIntervals = listOf(15.0)
            skipBackwardCommand.addTargetWithHandler {
                seekTo(_state.value.position - 15.seconds)
                MPRemoteCommandHandlerStatusSuccess
            }
            skipForwardCommand.preferredIntervals = listOf(30.0)
            skipForwardCommand.addTargetWithHandler {
                seekTo(_state.value.position + 30.seconds)
                MPRemoteCommandHandlerStatusSuccess
            }
            changePlaybackPositionCommand.addTargetWithHandler { event ->
                (event as? MPChangePlaybackPositionCommandEvent)?.let { seekTo(it.positionTime.seconds) }
                MPRemoteCommandHandlerStatusSuccess
            }
        }
    }

    override fun load(file: Path, metadata: PlaybackMetadata, startAt: Duration, playWhenReady: Boolean) {
        configureAudioSession()
        this.metadata = metadata
        ended = false
        _state.update {
            PlayerState(episodeGuid = metadata.episodeGuid, status = PlayerStatus.Loading, position = startAt, speed = it.speed)
        }

        val item = AVPlayerItem(uRL = NSURL.fileURLWithPath(file.toString()))
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            ended = true
            _state.update { it.copy(status = PlayerStatus.Ended, position = it.duration ?: it.position) }
            updateNowPlayingInfo()
        }
        player.replaceCurrentItemWithPlayerItem(item)
        if (startAt > Duration.ZERO) {
            player.seekToTime(CMTimeMakeWithSeconds(startAt.toDouble(DurationUnit.SECONDS), 600))
        }
        if (timeObserver == null) {
            timeObserver = player.addPeriodicTimeObserverForInterval(
                interval = CMTimeMakeWithSeconds(0.5, 600),
                queue = null,
            ) { _ -> syncStateFromPlayer() }
        }
        if (playWhenReady) play()
    }

    override fun play() {
        if (ended) {
            ended = false
            player.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
        }
        player.play()
        player.setRate(_state.value.speed)
        _state.update { it.copy(status = PlayerStatus.Playing) }
        updateNowPlayingInfo()
    }

    override fun pause() {
        player.pause()
        _state.update { it.copy(status = PlayerStatus.Paused) }
        updateNowPlayingInfo()
    }

    override fun seekTo(position: Duration) {
        val target = position.coerceAtLeast(Duration.ZERO)
        ended = false
        player.seekToTime(CMTimeMakeWithSeconds(target.toDouble(DurationUnit.SECONDS), 600))
        _state.update { it.copy(position = target) }
        updateNowPlayingInfo()
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        if (player.timeControlStatus == AVPlayerTimeControlStatusPlaying) player.setRate(speed)
        updateNowPlayingInfo()
    }

    override fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        ended = false
        // guid + position retained per the PodcastPlayer contract
        _state.update { it.copy(status = PlayerStatus.Idle) }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun syncStateFromPlayer() {
        if (ended) return
        val positionSeconds = CMTimeGetSeconds(player.currentTime())
        val durationSeconds = player.currentItem?.duration?.let { CMTimeGetSeconds(it) }
        _state.update { prev ->
            prev.copy(
                status = when (player.timeControlStatus) {
                    AVPlayerTimeControlStatusPlaying -> PlayerStatus.Playing
                    AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlayerStatus.Loading
                    else -> if (player.currentItem == null) prev.status else PlayerStatus.Paused
                },
                position = positionSeconds.takeIf { !it.isNaN() }?.seconds ?: prev.position,
                duration = durationSeconds?.takeIf { !it.isNaN() && it > 0 }?.seconds ?: prev.duration,
            )
        }
        updateNowPlayingInfo()
    }

    private fun updateNowPlayingInfo() {
        val meta = metadata ?: return
        val current = _state.value
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any>(
            MPMediaItemPropertyTitle to meta.title,
            MPMediaItemPropertyArtist to (meta.podcastTitle ?: ""),
            MPMediaItemPropertyPlaybackDuration to (current.duration?.toDouble(DurationUnit.SECONDS) ?: 0.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to current.position.toDouble(DurationUnit.SECONDS),
            MPNowPlayingInfoPropertyPlaybackRate to
                if (current.status == PlayerStatus.Playing) current.speed.toDouble() else 0.0,
        )
    }

    private fun configureAudioSession() {
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
    }
}
