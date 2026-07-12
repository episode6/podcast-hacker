package com.episode6.podcasthacker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.episode6.podcasthacker.playback.PlaybackService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    /** Conflated so a request sent before composition starts collecting isn't lost. */
    private val openNowPlayingRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        // only on fresh launches — a config-change recreation would otherwise replay the
        // notification tap and stomp wherever the user had navigated since
        if (savedInstanceState == null) maybeRequestNowPlaying(intent)

        setContent {
            App(
                appGraph = (application as PodcastHackerApplication).appGraph,
                openNowPlayingRequests = openNowPlayingRequests.receiveAsFlow(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        maybeRequestNowPlaying(intent)
    }

    /** The media notification's tap intent asks for the Now Playing screen. */
    private fun maybeRequestNowPlaying(intent: Intent) {
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
            openNowPlayingRequests.trySend(Unit)
        }
    }

    /** The media playback notification needs this granted on api 33+. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
    }
}
