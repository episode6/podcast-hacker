package com.episode6.podcasthacker.data.repo

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import assertk.assertions.matches
import assertk.assertions.isEmpty
import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.db.EpisodeEntity
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DownloadsRepositoryTest {

    // guids are urls/arbitrary strings in the wild — must never leak into filenames
    private val guid = "https://example.com/episodes/1?session=a/b"

    private val tempDir = Files.createTempDirectory("downloads-repo-test").toOkioPath()
    private val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val repo = DownloadsRepository(
        db = db,
        appDirs = AppDirs(dataDir = tempDir / "data", cacheDir = tempDir / "cache"),
    )

    @AfterTest
    fun tearDown() {
        db.close()
        FileSystem.SYSTEM.deleteRecursively(tempDir)
    }

    @Test
    fun filePaths_useFileSafeHashedNames() {
        val download = repo.downloadFilePath(guid)
        val reference = repo.referenceFilePath(guid)

        assertThat(download.name).matches(Regex("[0-9a-f]{64}\\.mp3"))
        assertThat(reference.name).matches(Regex("[0-9a-f]{64}\\.adref"))
        assertThat(repo.downloadFilePath("other-guid")).isNotEqualTo(download)
    }

    @Test
    fun markDownloaded_persistsToRoom() = runTest {
        db.episodeDao().upsertAll(listOf(episodeEntity()))

        repo.markDownloaded(guid, downloaded = true)
        assertThat(db.episodeDao().get(guid)!!.downloadState).isEqualTo(DownloadState.Downloaded.name)

        repo.markDownloaded(guid, downloaded = false)
        assertThat(db.episodeDao().get(guid)!!.downloadState).isEqualTo(DownloadState.NotDownloaded.name)
    }

    @Test
    fun deleteDownload_removesFilesAndResetsState() = runTest {
        db.episodeDao().upsertAll(listOf(episodeEntity(downloadState = DownloadState.Downloaded.name)))
        repo.prepareForDownload(guid)
        FileSystem.SYSTEM.write(repo.downloadFilePath(guid)) { writeUtf8("mp3") }
        FileSystem.SYSTEM.write(repo.referenceFilePath(guid)) { writeUtf8("ref") }

        repo.deleteDownload(guid)

        assertThat(repo.downloadedFileExists(guid)).isFalse()
        assertThat(FileSystem.SYSTEM.exists(repo.referenceFilePath(guid))).isFalse()
        assertThat(db.episodeDao().get(guid)!!.downloadState).isEqualTo(DownloadState.NotDownloaded.name)
    }

    @Test
    fun downloadedFileExists_tracksTheFile() {
        assertThat(repo.downloadedFileExists(guid)).isFalse()

        repo.prepareForDownload(guid)
        FileSystem.SYSTEM.write(repo.downloadFilePath(guid)) { writeUtf8("mp3") }

        assertThat(repo.downloadedFileExists(guid)).isTrue()
    }

    @Test
    fun adBoundaryCandidates_roundTripSortedByPosition() = runTest {
        db.episodeDao().upsertAll(listOf(episodeEntity()))
        val unsorted = listOf(
            AdBoundary(10.minutes, AdBoundary.Source.DaiSlot, AdBoundary.Role.Join),
            AdBoundary(90.seconds, AdBoundary.Source.DiffCut, AdBoundary.Role.Start),
            AdBoundary(2.minutes, AdBoundary.Source.Id3Chapter, AdBoundary.Role.End),
        )

        repo.saveAdBoundaryCandidates(guid, unsorted)

        assertThat(repo.adBoundaryCandidates(guid)).isEqualTo(
            listOf(unsorted[1], unsorted[2], unsorted[0]),
        )
    }

    @Test
    fun adBoundaryCandidates_resaveReplaces() = runTest {
        db.episodeDao().upsertAll(listOf(episodeEntity()))
        repo.saveAdBoundaryCandidates(
            guid,
            listOf(AdBoundary(1.minutes, AdBoundary.Source.SegmentBoundary, AdBoundary.Role.Join)),
        )

        val second = listOf(AdBoundary(3.minutes, AdBoundary.Source.DiffCut, AdBoundary.Role.End))
        repo.saveAdBoundaryCandidates(guid, second)

        assertThat(repo.adBoundaryCandidates(guid)).isEqualTo(second)
    }

    @Test
    fun deleteDownload_clearsAdBoundaryCandidates() = runTest {
        db.episodeDao().upsertAll(listOf(episodeEntity(downloadState = DownloadState.Downloaded.name)))
        repo.saveAdBoundaryCandidates(
            guid,
            listOf(AdBoundary(1.minutes, AdBoundary.Source.DiffCut, AdBoundary.Role.Start)),
        )

        repo.deleteDownload(guid)

        assertThat(repo.adBoundaryCandidates(guid)).isEmpty()
    }

    private fun episodeEntity(downloadState: String = DownloadState.NotDownloaded.name) = EpisodeEntity(
        guid = guid,
        feedUrl = "https://example.com/feed.xml",
        title = "Ep",
        notes = null,
        audioUrl = null,
        pubDateEpochMillis = null,
        durationSeconds = null,
        enclosureBytes = null,
        downloadState = downloadState,
        playbackPositionMillis = 0L,
    )
}
