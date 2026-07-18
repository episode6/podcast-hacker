package com.episode6.podcasthacker.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.inject.LocalAppGraph
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.ui.util.stateOf

/**
 * What the now-playing play/pause slot should show (both the mini player bar and the
 * expanded transport). A restored or file-deleted episode isn't playable, so its play
 * button becomes a download button and then live download progress, mirroring the
 * episode-row treatment in EpisodeRowAction/RecentlyPlayedRow.
 */
internal sealed interface NowPlayingButtonState {
    data object PlayPause : NowPlayingButtonState
    data object Download : NowPlayingButtonState
    data object Queued : NowPlayingButtonState
    data class DownloadProgress(val percentComplete: Float?) : NowPlayingButtonState
}

@Composable
internal fun nowPlayingButtonState(nowPlaying: NowPlayingState): NowPlayingButtonState {
    val store = LocalAppGraph.current.appStore
    val downloadStatus by store.stateOf { downloads[nowPlaying.episodeGuid] }
    val downloadState by store.stateOf { episode(nowPlaying.episodeGuid)?.downloadState }
    return nowPlayingButtonState(downloadStatus, downloadState)
}

internal fun nowPlayingButtonState(
    downloadStatus: EpisodeDownloadStatus?,
    downloadState: DownloadState?,
): NowPlayingButtonState = when (downloadStatus) {
    EpisodeDownloadStatus.Queued -> NowPlayingButtonState.Queued
    EpisodeDownloadStatus.Starting,
    EpisodeDownloadStatus.CuttingAds -> NowPlayingButtonState.DownloadProgress(null)
    is EpisodeDownloadStatus.Downloading -> NowPlayingButtonState.DownloadProgress(downloadStatus.percentComplete)
    is EpisodeDownloadStatus.Failure, null ->
        // an unknown episode (null downloadState, e.g. the episode list hasn't loaded
        // yet) keeps the plain play button rather than flashing a download button
        if (downloadState == null || downloadState == DownloadState.Downloaded) {
            NowPlayingButtonState.PlayPause
        } else {
            NowPlayingButtonState.Download
        }
}
