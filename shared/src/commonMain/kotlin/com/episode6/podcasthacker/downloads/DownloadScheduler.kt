package com.episode6.podcasthacker.downloads

import com.episode6.podcasthacker.PlatformContext

/**
 * Platform hook that keeps episode downloads running while the app is backgrounded.
 * The download side effects call these when the in-flight queue transitions between
 * empty and non-empty; on Android that pins the process with a user-initiated data
 * transfer job (api 34+) or a dataSync foreground service (api 24–33), elsewhere it's
 * a no-op (desktop/ios processes aren't killed for backgrounding the same way).
 */
interface DownloadScheduler {
    fun onQueueActive()
    fun onQueueIdle()
}

internal expect fun PlatformContext.createDownloadScheduler(): DownloadScheduler
