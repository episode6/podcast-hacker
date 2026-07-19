package com.episode6.podcasthacker.store

import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class AppState(
    val nowPlaying: NowPlayingState? = null,
    val subscriptions: List<Podcast> = emptyList(),
    /** All episodes grouped by feedUrl (pubDate desc), fed from Room by one app-lifetime
     * observer — per-screen Room flows proved unreliable (TODO.md Risk 10). */
    val episodesByFeed: Map<String, List<Episode>> = emptyMap(),
    val feedSync: FeedSyncState = FeedSyncState(),
    /** In-flight download status by episode guid; completed/deleted entries are removed
     * (Room's persisted downloadState is the source of truth once a download settles). */
    val downloads: Map<String, EpisodeDownloadStatus> = emptyMap(),
) {
    fun episode(guid: String): Episode? =
        episodesByFeed.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.guid == guid } }

    /** Subscriptions for the grid: freshest episode release first. Feeds with no loaded
     * episodes (or none carrying a pubDate) sort last; the sort is stable, so ties keep
     * their [subscriptions] (db) order. */
    fun subscriptionsByLatestEpisode(): List<Podcast> =
        subscriptions.sortedByDescending { podcast ->
            episodesByFeed[podcast.feedUrl].orEmpty().mapNotNull { it.pubDate }.maxOrNull()
                ?: Instant.DISTANT_PAST
        }
}

sealed interface EpisodeDownloadStatus {
    data object Queued : EpisodeDownloadStatus
    data object Starting : EpisodeDownloadStatus
    data class Downloading(val percentComplete: Float) : EpisodeDownloadStatus
    data object CuttingAds : EpisodeDownloadStatus

    /** Terminal status: the file is on disk and the Room downloaded flag is written, but
     * that flag hasn't round-tripped through Room's flow into [AppState.episodesByFeed]
     * yet. Clearing the entry before then would flash the Download button between the
     * progress indicator and the Play button, so the entry stays (rendered as progress)
     * until the store's episode reads Downloaded. */
    data object Finishing : EpisodeDownloadStatus
    data class Failure(val message: String) : EpisodeDownloadStatus
}

data class NowPlayingState(
    val episodeGuid: String,
    val episodeTitle: String,
    val podcastTitle: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: Duration = Duration.ZERO,
    /** Player-reported duration, falling back to the feed-supplied one until loaded. */
    val duration: Duration? = null,
    val speed: Float = 1f,
    val error: String? = null,
    /** Tacita's unverified ad-boundary guesses for this episode, sorted by position.
     * User-skippable markers only — never auto-skip on them. */
    val adBoundaries: List<AdBoundary> = emptyList(),
    /** 0..1 position of the confidence-filter slider: 0 keeps every boundary, 1 keeps
     * only this episode's top-confidence tier. Thresholds across the episode's observed
     * confidence range, so the max position always leaves at least one boundary. */
    val adBoundaryConfidenceFilter: Float = 0f,
    /** Ranges the listener has ear-checked as ads, appended once tacita records each
     * fingerprint and removed again when one is revoked. Session-scoped: cleared with
     * the rest of this state when playback moves to another episode. */
    val confirmedAdRanges: List<ConfirmedAd> = emptyList(),
)

/** An ad range the listener has ear-checked this session, kept with the store id of the
 * fingerprint the confirmation recorded so a second tap on the flag can revoke it. */
data class ConfirmedAd(val range: ClosedRange<Duration>, val fingerprintId: String)

/** A previous boundary must sit at least this far behind the playhead, so repeated
 * back-presses walk backward instead of re-landing on the boundary just seeked to. */
val SKIP_BACK_GRACE: Duration = 2.seconds

/** [NowPlayingState.adBoundaries] surviving the confidence filter; skip targets and the
 * Now Playing labels both come from this list so the UI always matches the buttons. */
fun NowPlayingState.filteredAdBoundaries(): List<AdBoundary> {
    if (adBoundaries.isEmpty() || adBoundaryConfidenceFilter <= 0f) return adBoundaries
    val min = adBoundaries.minOf { it.confidence }
    val max = adBoundaries.maxOf { it.confidence }
    // coerce guards float drift at filter=1 so the top tier itself always survives
    val threshold = (min + (max - min) * adBoundaryConfidenceFilter).coerceAtMost(max)
    return adBoundaries.filter { it.confidence >= threshold }
}

fun NowPlayingState.nextAdBoundary(): AdBoundary? =
    filteredAdBoundaries().firstOrNull { it.position > position }

/**
 * The filtered-boundary pair bracketing the playhead — the range a "confirm ad" action
 * would fingerprint. Null when the playhead isn't between two candidates. No grace
 * window: while listening inside an ad, the boundary just crossed IS the ad's start.
 */
fun NowPlayingState.bracketingAdBoundaries(): Pair<AdBoundary, AdBoundary>? {
    val boundaries = filteredAdBoundaries()
    val prev = boundaries.lastOrNull { it.position <= position } ?: return null
    val next = boundaries.firstOrNull { it.position > position } ?: return null
    return prev to next
}

/** The confirmed range the playhead currently sits inside, or null outside them all —
 * non-null tints the confirm-ad flag and routes its tap to a revoke instead. */
fun NowPlayingState.confirmedAdAtPlayhead(): ConfirmedAd? =
    confirmedAdRanges.firstOrNull { position in it.range }

fun NowPlayingState.previousAdBoundary(): AdBoundary? =
    filteredAdBoundaries().lastOrNull { it.position <= position - SKIP_BACK_GRACE }

/**
 * Where a platform skip-forward control (e.g. the media notification button) should land:
 * the next filtered ad boundary, or null when there's nothing to jump to — callers fall
 * back to their fixed seek increment. [playerGuid]/[playerPosition] come from the
 * platform player, which is fresher than this state and guards against acting on
 * boundaries of a previously loaded episode.
 */
fun NowPlayingState.adBoundarySkipForwardTarget(playerGuid: String?, playerPosition: Duration): Duration? =
    takeIf { it.episodeGuid == playerGuid }?.copy(position = playerPosition)?.nextAdBoundary()?.position

/** Skip-back counterpart of [adBoundarySkipForwardTarget], honoring [SKIP_BACK_GRACE]. */
fun NowPlayingState.adBoundarySkipBackTarget(playerGuid: String?, playerPosition: Duration): Duration? =
    takeIf { it.episodeGuid == playerGuid }?.copy(position = playerPosition)?.previousAdBoundary()?.position

data class FeedSyncState(
    val syncing: Set<String> = emptySet(),
    val lastError: String? = null,
) {
    val isSyncing: Boolean get() = syncing.isNotEmpty()
}
