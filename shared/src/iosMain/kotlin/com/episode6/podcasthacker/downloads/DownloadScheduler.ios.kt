package com.episode6.podcasthacker.downloads

import com.episode6.podcasthacker.PlatformContext

// True ios background-download resilience means NSURLSession background sessions, which
// tacita's download pipeline can't run through; out of scope for the best-effort target.
internal actual fun PlatformContext.createDownloadScheduler(): DownloadScheduler = NoOpDownloadScheduler

private object NoOpDownloadScheduler : DownloadScheduler {
    override fun onQueueActive() = Unit
    override fun onQueueIdle() = Unit
}
