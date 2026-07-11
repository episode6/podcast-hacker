package com.episode6.podcasthacker.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.episode6.podcasthacker.inject.AppGraphOwner
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.adBoundarySkipBackTarget
import com.episode6.podcasthacker.store.adBoundarySkipForwardTarget
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hosts the ExoPlayer + MediaSession so playback survives the activity and drives the
 * media notification, lock screen controls, audio focus, and headset events. The app
 * talks to it through the MediaController inside [MediaControllerPodcastPlayer].
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val appStore = (application as AppGraphOwner).appGraph.appStore
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        mediaSession = MediaSession.Builder(this, AdBoundarySeekingPlayer(player, appStore))
            .setSessionActivity(nowPlayingActivityIntent())
            .build()
    }

    /** Opens the app on the Now Playing screen when the media notification is tapped. */
    private fun nowPlayingActivityIntent(): PendingIntent {
        val intent = checkNotNull(packageManager.getLaunchIntentForPackage(packageName)) {
            "no launch intent for $packageName"
        }
        intent.putExtra(EXTRA_OPEN_NOW_PLAYING, true)
        // single-activity app: deliver to the existing MainActivity (onNewIntent) instead
        // of stacking a new instance
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            /* requestCode = */ 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.episode6.podcasthacker.OPEN_NOW_PLAYING"
    }
}

/**
 * Retargets the session's seek-back/forward commands (the media notification's skip
 * buttons, also lock screen / headset seeks) at the episode's filtered ad-boundary
 * candidates — the same targets as the Now Playing screen's skip row. With no boundary
 * in a direction (or none at all) the command falls back to the player's fixed 15s/30s
 * increment, so the buttons always do something. Skipping stays strictly user-initiated;
 * this only changes where an explicit seek command lands.
 */
private class AdBoundarySeekingPlayer(
    player: Player,
    private val appStore: AppStore,
) : ForwardingPlayer(player) {

    override fun seekForward() {
        val target = appStore.state.nowPlaying
            ?.adBoundarySkipForwardTarget(currentMediaItem?.mediaId, currentPosition.milliseconds)
        if (target != null) seekTo(target.inWholeMilliseconds) else super.seekForward()
    }

    override fun seekBack() {
        val target = appStore.state.nowPlaying
            ?.adBoundarySkipBackTarget(currentMediaItem?.mediaId, currentPosition.milliseconds)
        if (target != null) seekTo(target.inWholeMilliseconds) else super.seekBack()
    }
}
