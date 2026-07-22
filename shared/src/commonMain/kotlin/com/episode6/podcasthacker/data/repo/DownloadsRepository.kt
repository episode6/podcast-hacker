package com.episode6.podcasthacker.data.repo

import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.db.DownloadLogLineEntity
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.DownloadState
import com.episode6.podcasthacker.data.model.toDomain
import com.episode6.podcasthacker.data.model.toEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Owns where downloaded episodes and their tacita ad-diff references live on disk, plus
 * the persisted downloaded/not-downloaded flag. Files are named by a hash of the episode
 * guid (guids are urls or arbitrary feed-supplied strings — not filesystem safe).
 */
@Inject
@SingleIn(AppScope::class)
class DownloadsRepository(
    private val db: AppDatabase,
    private val appDirs: AppDirs,
) {
    private val fs: FileSystem = FileSystem.SYSTEM

    fun downloadFilePath(episodeGuid: String): Path =
        appDirs.dataDir / "downloads" / "${episodeGuid.fileSafeHash()}.mp3"

    fun referenceFilePath(episodeGuid: String): Path =
        appDirs.cacheDir / "adrefs" / "${episodeGuid.fileSafeHash()}.adref"

    /**
     * The tacita ad-creative fingerprint store for a feed. Per-feed (not per-episode):
     * creatives are targeted per show, and tacita's docs call for per-feed scoping to
     * avoid false positives from assets shared across a network's shows. Lives under
     * dataDir — accumulated listener confirmations are durable data, not cache. Tacita
     * creates the file (and this parent dir) on first use.
     */
    fun fingerprintStorePath(feedUrl: String): Path =
        appDirs.dataDir / "fingerprints" / "${feedUrl.fileSafeHash()}.tacita-fp"

    /**
     * The tacita *acoustic* fingerprint store. One global file (not per-feed): tacita's
     * acoustic store carries per-feed attributions internally, and its docs call for
     * sharing one store across every followed feed so a creative confirmed on one feed
     * can be recognized on another. Snapshot builds only for now — acoustic matching is
     * log-only in tacita pending real-feed ear verification (see downloadEpisode).
     */
    fun acousticFingerprintStorePath(): Path =
        appDirs.dataDir / "fingerprints" / "acoustic.tacita-afp"

    fun downloadedFileExists(episodeGuid: String): Boolean = fs.exists(downloadFilePath(episodeGuid))

    /** Creates the parent dirs both download outputs need. */
    fun prepareForDownload(episodeGuid: String) {
        fs.createDirectories(downloadFilePath(episodeGuid).parent!!)
        fs.createDirectories(referenceFilePath(episodeGuid).parent!!)
    }

    /** References make future re-downloads better, but we clean them per the v0 plan. */
    fun deleteReferenceFile(episodeGuid: String) {
        fs.delete(referenceFilePath(episodeGuid), mustExist = false)
    }

    suspend fun markDownloaded(episodeGuid: String, downloaded: Boolean) {
        db.episodeDao().setDownloadState(
            guid = episodeGuid,
            state = (if (downloaded) DownloadState.Downloaded else DownloadState.NotDownloaded).name,
        )
    }

    suspend fun deleteDownload(episodeGuid: String) {
        fs.delete(downloadFilePath(episodeGuid), mustExist = false)
        deleteReferenceFile(episodeGuid)
        markDownloaded(episodeGuid, downloaded = false)
        db.adBoundaryCandidateDao().deleteForEpisode(episodeGuid)
        db.downloadLogDao().deleteForEpisode(episodeGuid)
    }

    /** Candidates describe the downloaded file's timeline, so a re-download replaces them. */
    suspend fun saveAdBoundaryCandidates(episodeGuid: String, candidates: List<AdBoundary>) {
        db.adBoundaryCandidateDao().replaceForEpisode(episodeGuid, candidates.map { it.toEntity(episodeGuid) })
    }

    suspend fun adBoundaryCandidates(episodeGuid: String): List<AdBoundary> =
        db.adBoundaryCandidateDao().getForEpisode(episodeGuid).map { it.toDomain() }

    /** Tacita's log lines from the episode's latest download attempt (snapshot builds
     * only — see downloadEpisode); like ad-boundary candidates, a re-download replaces them. */
    suspend fun saveDownloadLog(episodeGuid: String, lines: List<String>) {
        db.downloadLogDao().replaceForEpisode(
            guid = episodeGuid,
            lines = lines.mapIndexed { i, line -> DownloadLogLineEntity(episodeGuid = episodeGuid, seq = i, line = line) },
        )
    }

    suspend fun downloadLog(episodeGuid: String): List<String> =
        db.downloadLogDao().getForEpisode(episodeGuid).map { it.line }
}

private fun String.fileSafeHash(): String = encodeUtf8().sha256().hex()
