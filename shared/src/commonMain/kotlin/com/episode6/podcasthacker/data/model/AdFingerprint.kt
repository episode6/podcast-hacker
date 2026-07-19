package com.episode6.podcasthacker.data.model

import kotlin.time.Duration

/**
 * One known ad-creative fingerprint from a feed's tacita store. Fingerprints accumulate
 * as downloads cut ads (diff-proven) and as the listener confirms ads from the player
 * (ear-confirmed); recurrences of a stored creative in later downloads of the feed
 * surface as high-confidence skippable boundaries. Mirrors tacita's AdFingerprintInfo.
 */
data class AdFingerprint(
    /** Content-derived identity: the same creative bytes always produce the same id. */
    val id: String,
    val provenance: Provenance,
    /** Playback duration of the fingerprinted range. */
    val duration: Duration,
    /** Encoded size of the fingerprinted range. */
    val sizeBytes: Long,
) {
    /** How the fingerprint earned its place in the store. Mirrors tacita's Provenance. */
    enum class Provenance { DiffProven, HumanConfirmed }
}
