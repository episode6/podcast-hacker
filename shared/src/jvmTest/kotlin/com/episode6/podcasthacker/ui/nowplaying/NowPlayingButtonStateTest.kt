package com.episode6.podcasthacker.ui.nowplaying

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import kotlin.test.Test

class NowPlayingButtonStateTest {

    @Test
    fun downloadedEpisode_playPause() {
        assertThat(nowPlayingButtonState(downloadStatus = null, downloadState = DownloadState.Downloaded))
            .isEqualTo(NowPlayingButtonState.PlayPause)
    }

    @Test
    fun unknownEpisode_playPause() {
        // e.g. the episode list hasn't loaded yet: keep the play button rather than
        // flashing a download button at it
        assertThat(nowPlayingButtonState(downloadStatus = null, downloadState = null))
            .isEqualTo(NowPlayingButtonState.PlayPause)
    }

    @Test
    fun notDownloadedEpisode_download() {
        assertThat(nowPlayingButtonState(downloadStatus = null, downloadState = DownloadState.NotDownloaded))
            .isEqualTo(NowPlayingButtonState.Download)
    }

    @Test
    fun failedDownload_offersRetry() {
        assertThat(
            nowPlayingButtonState(
                downloadStatus = EpisodeDownloadStatus.Failure("boom"),
                downloadState = DownloadState.NotDownloaded,
            )
        ).isEqualTo(NowPlayingButtonState.Download)
    }

    @Test
    fun queuedDownload_queued() {
        assertThat(
            nowPlayingButtonState(EpisodeDownloadStatus.Queued, DownloadState.NotDownloaded)
        ).isEqualTo(NowPlayingButtonState.Queued)
    }

    @Test
    fun inFlightDownload_progress() {
        assertThat(
            nowPlayingButtonState(EpisodeDownloadStatus.Starting, DownloadState.NotDownloaded)
        ).isEqualTo(NowPlayingButtonState.DownloadProgress(null))
        assertThat(
            nowPlayingButtonState(EpisodeDownloadStatus.Downloading(0.4f), DownloadState.Downloading)
        ).isEqualTo(NowPlayingButtonState.DownloadProgress(0.4f))
        assertThat(
            nowPlayingButtonState(EpisodeDownloadStatus.CuttingAds, DownloadState.Downloading)
        ).isEqualTo(NowPlayingButtonState.DownloadProgress(null))
    }

    @Test
    fun finishingDownload_staysOnProgress_untilRoomFlagLands() {
        // the completion handshake: still NotDownloaded in the store, but the entry
        // reads Finishing — keep showing progress, never flash the download button
        assertThat(
            nowPlayingButtonState(EpisodeDownloadStatus.Finishing, DownloadState.NotDownloaded)
        ).isEqualTo(NowPlayingButtonState.DownloadProgress(null))
    }
}
