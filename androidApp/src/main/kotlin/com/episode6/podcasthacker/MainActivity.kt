package com.episode6.podcasthacker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            App((application as PodcastHackerApplication).appGraph)
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
