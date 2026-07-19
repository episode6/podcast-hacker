package com.episode6.podcasthacker.data.model

import com.episode6.tacita.AdBoundaryCandidate
import kotlin.time.Duration.Companion.milliseconds

internal fun AdBoundaryCandidate.toDomain(): AdBoundary = AdBoundary(
    position = timeMs.milliseconds,
    confidence = confidence,
    source = when (source) {
        AdBoundaryCandidate.Source.SEGMENT_BOUNDARY -> AdBoundary.Source.SegmentBoundary
        AdBoundaryCandidate.Source.DIFF_CUT -> AdBoundary.Source.DiffCut
        AdBoundaryCandidate.Source.DAI_SLOT -> AdBoundary.Source.DaiSlot
        AdBoundaryCandidate.Source.ID3_CHAPTER -> AdBoundary.Source.Id3Chapter
        AdBoundaryCandidate.Source.FINGERPRINT -> AdBoundary.Source.Fingerprint
    },
    role = when (role) {
        AdBoundaryCandidate.Role.START -> AdBoundary.Role.Start
        AdBoundaryCandidate.Role.END -> AdBoundary.Role.End
        AdBoundaryCandidate.Role.JOIN -> AdBoundary.Role.Join
    },
)
