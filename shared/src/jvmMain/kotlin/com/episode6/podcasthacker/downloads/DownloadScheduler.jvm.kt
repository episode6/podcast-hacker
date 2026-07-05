package com.episode6.podcasthacker.downloads

import com.episode6.podcasthacker.PlatformContext

internal actual fun PlatformContext.createDownloadScheduler(): DownloadScheduler = NoOpDownloadScheduler

private object NoOpDownloadScheduler : DownloadScheduler {
    override fun onQueueActive() = Unit
    override fun onQueueIdle() = Unit
}
