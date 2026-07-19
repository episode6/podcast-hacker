package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.playback.PlayerState
import com.episode6.podcasthacker.playback.PlayerStatus
import com.episode6.redux.Action
import com.episode6.redux.StoreFlow

typealias AppStore = StoreFlow<AppState>

internal fun AppState.reduce(action: Action): AppState = when (action) {
    is UpdateStateAction -> reduceUpdateStateAction(action)
    else                 -> this
}

private fun AppState.reduceUpdateStateAction(action: UpdateStateAction): AppState = when (action) {
    is SetNowPlaying     -> copy(nowPlaying = action.nowPlaying)
    is SetSubscriptions  -> copy(subscriptions = action.subscriptions)
    is SetEpisodes       -> copy(episodesByFeed = action.episodesByFeed)
    is SetFeedSyncing    -> copy(feedSync = feedSync.copy(
        syncing = if (action.syncing) feedSync.syncing + action.feedUrl else feedSync.syncing - action.feedUrl,
    ))
    is SetFeedSyncError  -> copy(feedSync = feedSync.copy(lastError = action.message))
    is SetEpisodeDownloadStatus -> copy(downloads = when (action.status) {
        null -> downloads - action.episodeGuid
        else -> downloads + (action.episodeGuid to action.status)
    })
    is SetPlayerState -> copy(nowPlaying = nowPlaying?.mergedWith(action.playerState))
    is SetAdBoundaryConfidenceFilter -> copy(
        nowPlaying = nowPlaying?.copy(adBoundaryConfidenceFilter = action.filter.coerceIn(0f, 1f)),
    )
    is MarkAdRangeConfirmed -> copy(nowPlaying = nowPlaying?.withConfirmedRange(action))
    is MarkAdRangeUnconfirmed -> copy(nowPlaying = nowPlaying?.withoutConfirmedRange(action))
}

/** Stale confirmations (playback moved to a different episode) leave the state untouched.
 * Replaces-by-id rather than appending: re-confirming a range tacita already fingerprinted
 * hands back the same id and must not duplicate the entry. */
private fun NowPlayingState.withConfirmedRange(action: MarkAdRangeConfirmed): NowPlayingState =
    if (action.episodeGuid != episodeGuid) this
    else copy(
        confirmedAdRanges = confirmedAdRanges.filter { it.fingerprintId != action.fingerprintId } +
            ConfirmedAd(action.start..action.end, action.fingerprintId),
    )

/** Same staleness rule as [withConfirmedRange]. */
private fun NowPlayingState.withoutConfirmedRange(action: MarkAdRangeUnconfirmed): NowPlayingState =
    if (action.episodeGuid != episodeGuid) this
    else copy(confirmedAdRanges = confirmedAdRanges.filter { it.fingerprintId != action.fingerprintId })

/** Stale player states (a different or unloaded episode) leave the ui state untouched. */
private fun NowPlayingState.mergedWith(player: PlayerState): NowPlayingState =
    if (player.episodeGuid != episodeGuid) this else copy(
        isPlaying = player.status == PlayerStatus.Playing,
        isLoading = player.status == PlayerStatus.Loading,
        position = player.position,
        duration = player.duration ?: duration,
        speed = player.speed,
        error = (player.status as? PlayerStatus.Error)?.message,
    )
