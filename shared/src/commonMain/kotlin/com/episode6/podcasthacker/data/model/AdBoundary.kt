package com.episode6.podcasthacker.data.model

import kotlin.time.Duration

/**
 * A point in a downloaded episode's timeline that *might* be the start or end of an ad,
 * as reported by tacita when the download completed. These are aggressive, unverified
 * guesses — false positives are expected by design. Render them as user-skippable
 * markers only; never auto-skip or auto-cut on them.
 */
data class AdBoundary(
    val position: Duration,
    val source: Source,
    val role: Role,
    /** Tacita's heuristic 0..1 ranking of how ad-like this boundary is — ordering is
     * meaningful, absolute values are not. Drives the Now Playing confidence filter. */
    val confidence: Float,
) {
    /** Which detection signal produced the candidate. Mirrors tacita's Source. */
    enum class Source { SegmentBoundary, DiffCut, DaiSlot, Id3Chapter, Unknown }

    /** How [position] relates to a possible ad. Mirrors tacita's Role. */
    enum class Role { Start, End, Join, Unknown }
}
