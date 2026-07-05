package com.episode6.podcasthacker.data.repo

import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.data.db.AppDatabase
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
    }

    /** Candidates describe the downloaded file's timeline, so a re-download replaces them. */
    suspend fun saveAdBoundaryCandidates(episodeGuid: String, candidates: List<AdBoundary>) {
        db.adBoundaryCandidateDao().replaceForEpisode(episodeGuid, candidates.map { it.toEntity(episodeGuid) })
    }

    suspend fun adBoundaryCandidates(episodeGuid: String): List<AdBoundary> =
        db.adBoundaryCandidateDao().getForEpisode(episodeGuid).map { it.toDomain() }
}

private fun String.fileSafeHash(): String = encodeUtf8().sha256().hex()
