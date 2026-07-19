package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.backup.LibraryBackup
import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.redux.Action
import kotlin.time.Duration

/**
 * Actions that directly mutate [AppState] in the reducer. All other action types are
 * handled by side effects.
 */
sealed interface UpdateStateAction : Action

data class SetNowPlaying(val nowPlaying: NowPlayingState?) : UpdateStateAction
data class SetSubscriptions(val subscriptions: List<Podcast>) : UpdateStateAction
data class SetEpisodes(val episodesByFeed: Map<String, List<Episode>>) : UpdateStateAction
data class SetFeedSyncing(val feedUrl: String, val syncing: Boolean) : UpdateStateAction
data class SetFeedSyncError(val message: String?) : UpdateStateAction

/** null [status] clears the episode's in-flight entry. */
data class SetEpisodeDownloadStatus(
    val episodeGuid: String,
    val status: EpisodeDownloadStatus?,
) : UpdateStateAction

/** Merges live [PlayerState] into [NowPlayingState] (ignored while nothing is playing). */
data class SetPlayerState(val playerState: PlayerState) : UpdateStateAction

/** Moves the ad-boundary confidence-filter slider; clamped to 0..1 in the reducer. */
data class SetAdBoundaryConfidenceFilter(val filter: Float) : UpdateStateAction

/**
 * Requests handled by side effects (repo call → result actions).
 */
sealed interface AsyncAction : Action

data class SubscribeToPodcast(val feedUrl: String) : AsyncAction
data class UnsubscribeFromPodcast(val feedUrl: String) : AsyncAction
data class RefreshFeed(val feedUrl: String) : AsyncAction

/** Grid-screen refresh: syncs every subscribed feed in parallel, each skipped if
 * already mid-sync (same rule as [RefreshFeed]). */
data object RefreshAllFeeds : AsyncAction
data class DownloadEpisode(val episodeGuid: String) : AsyncAction
data class DeleteDownload(val episodeGuid: String) : AsyncAction
data class PlayEpisode(val episodeGuid: String) : AsyncAction
data object TogglePlayPause : AsyncAction
data class SeekTo(val position: Duration) : AsyncAction
data class SeekBy(val offset: Duration) : AsyncAction

/** User-initiated jump to an ad-boundary candidate; no-op when none exists in that
 * direction. Only ever dispatched from a button press — candidates are unverified
 * guesses and must never trigger an automatic skip. */
data object SkipToNextAdBoundary : AsyncAction
data object SkipToPreviousAdBoundary : AsyncAction

/** The listener's ear-check that [start]..[end] of a downloaded episode really is an ad.
 * Tacita fingerprints the range into the feed's store, so future downloads of the feed
 * flag recurrences of the creative as high-confidence skippable boundaries. */
data class ConfirmAdRange(val episodeGuid: String, val start: Duration, val end: Duration) : AsyncAction
data class SetPlaybackSpeed(val speed: Float) : AsyncAction
data object StopPlayback : AsyncAction

/** Imports a library backup (parsed from an OPML or json file): subscribes to its new
 * feeds, then applies its listening state once every subscribe has finished. */
data class ImportLibrary(val backup: LibraryBackup) : AsyncAction

/** Re-runs the cold-start now-playing restore: seeds the bar from the most recently
 * played episode when nothing is playing (no-op otherwise). Emitted after an import
 * applies played-episode listening state, so the bar pops up like it would have on
 * the device the backup came from. */
data object RestoreNowPlaying : AsyncAction
