package com.episode6.podcasthacker.store.sideeffects

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.episode6.podcasthacker.data.model.DownloadState as PersistedDownloadState
import com.episode6.podcasthacker.data.model.AdBoundary
import com.episode6.podcasthacker.data.model.AdFingerprint
import com.episode6.podcasthacker.data.model.Episode
import com.episode6.podcasthacker.data.model.Podcast
import com.episode6.podcasthacker.data.repo.DownloadsRepository
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.BuildInfo
import com.episode6.podcasthacker.downloads.DownloadScheduler
import com.episode6.podcasthacker.downloads.TacitaFactory
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.ConfirmAdRange
import com.episode6.podcasthacker.store.DeleteDownload
import com.episode6.podcasthacker.store.DownloadEpisode
import com.episode6.podcasthacker.store.EpisodeDownloadStatus
import com.episode6.podcasthacker.store.LoadAdFingerprints
import com.episode6.podcasthacker.store.MarkAdRangeConfirmed
import com.episode6.podcasthacker.store.MarkAdRangeUnconfirmed
import com.episode6.podcasthacker.store.NowPlayingState
import com.episode6.podcasthacker.store.RemoveAdFingerprint
import com.episode6.podcasthacker.store.SetAdFingerprints
import com.episode6.podcasthacker.store.SetEpisodeDownloadStatus
import com.episode6.podcasthacker.store.SetEpisodes
import com.episode6.podcasthacker.store.UnconfirmAdRange
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import com.episode6.tacita.AdBoundaryCandidate
import com.episode6.tacita.AdFingerprintInfo
import com.episode6.tacita.DownloadState
import com.episode6.tacita.Tacita
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DownloadSideEffectsTest {

    private val guid = "episode-guid"
    private val audioUrl = "https://example.com/ep.mp3"
    private val outputFile: Path = "/downloads/hash.mp3".toPath()
    private val referenceFile: Path = "/cache/hash.adref".toPath()
    private val fingerprintStore: Path = "/data/fingerprints/feedhash.tacita-fp".toPath()

    // production gates the acoustic store + feedId (and log persistence) on snapshot
    // builds, so expectations here follow the same flag
    private val acousticStorePath: Path = "/data/fingerprints/acoustic.tacita-afp".toPath()
    private val acousticStore: Path? = acousticStorePath.takeIf { BuildInfo.IS_SNAPSHOT }
    private val feedId: String? = "feed".takeIf { BuildInfo.IS_SNAPSHOT }

    private val sideEffects = object : DownloadSideEffects {}

    private val enclosureBytes = 59_568_000L
    private val duration = 3723.seconds

    private val episodeRepo = mockk<EpisodeRepository> {
        coEvery { episode(guid) } returns Episode(
            guid = guid,
            feedUrl = "feed",
            title = "Ep",
            audioUrl = audioUrl,
            duration = duration,
            enclosureBytes = enclosureBytes,
        )
    }
    private val downloadsRepo = mockk<DownloadsRepository>(relaxUnitFun = true) {
        every { downloadFilePath(guid) } returns outputFile
        every { referenceFilePath(guid) } returns referenceFile
        every { downloadedFileExists(guid) } returns false
        every { fingerprintStorePath(any()) } returns fingerprintStore
        every { acousticFingerprintStorePath() } returns acousticStorePath
        coEvery { markDownloaded(any(), any()) } returns Unit
        coEvery { deleteDownload(any()) } returns Unit
    }

    @Test
    fun downloadRequest_readsAsQueuedImmediately() = runTest {
        val actions = sideEffects.enqueueDownloads().output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Queued),
        )
    }

    @Test
    fun mapsTacitaStates_toEpisodeStatuses() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
            } returns flowOf(
                DownloadState.Downloading(outputFile, 0.25f),
                DownloadState.Downloading(outputFile, 0.75f),
                DownloadState.Downloading(referenceFile, 0.5f), // reference copy
                DownloadState.CuttingAds,
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.25f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.75f)),
            // reference-copy Downloading and CuttingAds collapse into one status
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.CuttingAds),
            // cleared by clearFinishedDownloads once Room delivers the downloaded flag
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Finishing),
        )
        coVerify(exactly = 1) { downloadsRepo.markDownloaded(guid, true) }
        coVerify(exactly = 1) { downloadsRepo.deleteReferenceFile(guid) }
        coVerify(exactly = 1) { downloadsRepo.prepareForDownload(guid) }
    }

    /** Tacita emits progress per 8KB chunk; unthrottled, those actions flood the store's
     * side-effect relay and stall every other action behind them. */
    @Test
    fun progressUpdates_quantizedToWholePercents_andDeduped() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
            } returns flowOf(
                DownloadState.Downloading(outputFile, 0.0f),
                DownloadState.Downloading(outputFile, 0.001f),
                DownloadState.Downloading(outputFile, 0.011f),
                DownloadState.Downloading(outputFile, 0.014f),
                DownloadState.Downloading(outputFile, 0.019f),
                DownloadState.Downloading(outputFile, 0.021f),
                DownloadState.Downloading(referenceFile, 0.5f), // reference copy
                DownloadState.Downloading(referenceFile, 0.9f), // reference copy
                DownloadState.CuttingAds,
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.0f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.01f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Downloading(0.02f)),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.CuttingAds),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Finishing),
        )
    }

    @Test
    fun completedDownload_persistsAdBoundaryCandidates_beforeDownloadedFlag() = runTest {
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
            } returns flowOf(
                DownloadState.Complete(
                    listOf(
                        AdBoundaryCandidate(60_000L, AdBoundaryCandidate.Source.DIFF_CUT, AdBoundaryCandidate.Role.START, confidence = 0.65f),
                        AdBoundaryCandidate(90_000L, AdBoundaryCandidate.Source.DAI_SLOT, AdBoundaryCandidate.Role.JOIN, confidence = 0.8f),
                    ),
                ),
            )
        }

        sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        coVerifyOrder {
            downloadsRepo.saveAdBoundaryCandidates(
                guid,
                listOf(
                    AdBoundary(60.seconds, AdBoundary.Source.DiffCut, AdBoundary.Role.Start, confidence = 0.65f),
                    AdBoundary(90.seconds, AdBoundary.Source.DaiSlot, AdBoundary.Role.Join, confidence = 0.8f),
                ),
            )
            downloadsRepo.markDownloaded(guid, true)
        }
    }

    @Test
    fun existingFile_downloadsWithOverwrite() = runTest {
        every { downloadsRepo.downloadedFileExists(guid) } returns true
        val tacita = mockk<Tacita> {
            every {
                downloadPodcast(audioUrl, outputFile, referenceFile, true, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
            } returns flowOf(
                DownloadState.Complete(),
            )
        }

        sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        coVerify(exactly = 1) {
            tacita.downloadPodcast(audioUrl, outputFile, referenceFile, true, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
        }
    }

    /** The download log is the ear-verification evidence for tacita's fingerprint and
     * acoustic matching — each download's lines land in the db keyed by episode (on
     * snapshot builds), where Now Playing renders them. */
    @Test
    fun downloadLogLines_persistPerEpisode_onSnapshotBuilds() = runTest {
        val tacita = mockk<Tacita>()
        var log: ((String) -> Unit)? = null
        every {
            tacita.downloadPodcast(audioUrl, outputFile, referenceFile, false, true, enclosureBytes, duration.inWholeSeconds, fingerprintStore, acousticStore, feedId)
        } returns flow {
            log?.invoke("AdCutter: hash.mp3: AdsCut(adBreaksRemoved=1)")
            emit(DownloadState.Complete())
        }

        sideEffects.downloadEpisodes(TacitaFactory { l -> log = l; tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        if (BuildInfo.IS_SNAPSHOT) {
            coVerify(exactly = 1) { downloadsRepo.saveDownloadLog(guid, listOf("AdCutter: hash.mp3: AdsCut(adBreaksRemoved=1)")) }
        } else {
            coVerify(exactly = 0) { downloadsRepo.saveDownloadLog(any(), any()) }
        }
    }

    /** Failure lines are diagnostics too — a failed download still records what tacita
     * reported before it died. */
    @Test
    fun downloadLogLines_persist_evenWhenTheDownloadFails() = runTest {
        val tacita = mockk<Tacita>()
        var log: ((String) -> Unit)? = null
        every {
            tacita.downloadPodcast(audioUrl, outputFile, any(), any(), any(), any(), any(), any(), any(), any())
        } returns flow {
            log?.invoke("CleanSourceResolver: probe failed for $audioUrl: boom")
            throw RuntimeException("boom")
        }

        val actions = sideEffects.downloadEpisodes(TacitaFactory { l -> log = l; tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Failure("boom")),
        )
        if (BuildInfo.IS_SNAPSHOT) {
            coVerify(exactly = 1) {
                downloadsRepo.saveDownloadLog(guid, listOf("CleanSourceResolver: probe failed for $audioUrl: boom"))
            }
        }
    }

    @Test
    fun failedDownload_reportsFailure_withoutBlockingTheQueue() = runTest {
        val otherGuid = "other-guid"
        val otherOutput = "/downloads/other.mp3".toPath()
        coEvery { episodeRepo.episode(otherGuid) } returns
            Episode(guid = otherGuid, feedUrl = "feed", title = "Other", audioUrl = audioUrl)
        every { downloadsRepo.downloadFilePath(otherGuid) } returns otherOutput
        every { downloadsRepo.referenceFilePath(otherGuid) } returns referenceFile
        every { downloadsRepo.downloadedFileExists(otherGuid) } returns false

        val tacita = mockk<Tacita> {
            every { downloadPodcast(audioUrl, outputFile, any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
                throw RuntimeException("boom")
            }
            every { downloadPodcast(audioUrl, otherOutput, any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(
                DownloadState.Complete(),
            )
        }

        val actions = sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid), DownloadEpisode(otherGuid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Failure("boom")),
            SetEpisodeDownloadStatus(otherGuid, EpisodeDownloadStatus.Starting),
            SetEpisodeDownloadStatus(otherGuid, EpisodeDownloadStatus.Finishing),
        )
        coVerify(exactly = 1) { downloadsRepo.markDownloaded(otherGuid, true) }
        coVerify(exactly = 0) { downloadsRepo.markDownloaded(guid, any()) }
    }

    @Test
    fun downloads_runConcurrently_cappedAtFour() = runTest {
        val guids = (1..5).map { "guid-$it" }
        val gates = guids.associateWith { CompletableDeferred<Unit>() }
        val started = mutableListOf<String>()
        val tacita = mockk<Tacita>()
        guids.forEach { g ->
            val output = "/downloads/$g.mp3".toPath()
            coEvery { episodeRepo.episode(g) } returns
                Episode(guid = g, feedUrl = "feed", title = g, audioUrl = audioUrl)
            every { downloadsRepo.downloadFilePath(g) } returns output
            every { downloadsRepo.referenceFilePath(g) } returns "/cache/$g.adref".toPath()
            every { downloadsRepo.downloadedFileExists(g) } returns false
            every { tacita.downloadPodcast(audioUrl, output, any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
                started += g
                gates.getValue(g).await()
                emit(DownloadState.Complete())
            }
        }

        val collector = launch {
            sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)
                .output(*guids.map { DownloadEpisode(it) }.toTypedArray())
                .toList()
        }
        runCurrent()
        assertThat(started).containsExactly("guid-1", "guid-2", "guid-3", "guid-4")

        gates.getValue("guid-1").complete(Unit)
        runCurrent()
        assertThat(started).containsExactly("guid-1", "guid-2", "guid-3", "guid-4", "guid-5")

        guids.forEach { gates.getValue(it).complete(Unit) }
        collector.join()
    }

    /** The middleware relays actions through a zero-buffer shared flow: an emit only
     * completes once every side effect has accepted it, so a side effect that suspends
     * mid-collect stalls action delivery to the whole app. A saturated download queue
     * must keep accepting actions — taps beyond the first queued episode used to go
     * dead until a slot freed. */
    @Test
    fun saturatedQueue_keepsAcceptingActions() = runTest {
        val guids = (1..6).map { "guid-$it" }
        val gates = guids.associateWith { CompletableDeferred<Unit>() }
        val started = mutableListOf<String>()
        val tacita = mockk<Tacita>()
        guids.forEach { g ->
            val output = "/downloads/$g.mp3".toPath()
            coEvery { episodeRepo.episode(g) } returns
                Episode(guid = g, feedUrl = "feed", title = g, audioUrl = audioUrl)
            every { downloadsRepo.downloadFilePath(g) } returns output
            every { downloadsRepo.referenceFilePath(g) } returns "/cache/$g.adref".toPath()
            every { downloadsRepo.downloadedFileExists(g) } returns false
            every { tacita.downloadPodcast(audioUrl, output, any(), any(), any(), any(), any(), any(), any(), any()) } returns flow {
                started += g
                gates.getValue(g).await()
                emit(DownloadState.Complete())
            }
        }
        // mimics the relay: emit(action) suspends until the side effect's collector takes it
        val accepted = mutableListOf<String>()
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flow {
                guids.forEach { g ->
                    emit(DownloadEpisode(g))
                    accepted += g
                }
            }
            coEvery { currentState() } returns AppState()
        }
        val effect = sideEffects.downloadEpisodes(TacitaFactory { tacita }, episodeRepo, downloadsRepo)

        val collector = launch { with(effect) { context.act() }.toList() }
        runCurrent()

        // all six requests accepted off the relay even though only four download slots exist
        assertThat(accepted).containsExactly(*guids.toTypedArray())
        assertThat(started).containsExactly("guid-1", "guid-2", "guid-3", "guid-4")

        guids.forEach { gates.getValue(it).complete(Unit) }
        collector.join()
    }

    @Test
    fun episodeWithoutAudioUrl_reportsFailure() = runTest {
        coEvery { episodeRepo.episode(guid) } returns
            Episode(guid = guid, feedUrl = "feed", title = "Ep", audioUrl = null)

        val actions = sideEffects.downloadEpisodes(TacitaFactory { mockk() }, episodeRepo, downloadsRepo)
            .output(DownloadEpisode(guid)).toList()

        assertThat(actions).containsExactly(
            SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Failure("episode has no audio url")),
        )
    }

    private fun episodeInStore(downloadState: PersistedDownloadState) = Episode(
        guid = guid,
        feedUrl = "feed",
        title = "Ep",
        audioUrl = audioUrl,
        downloadState = downloadState,
    )

    @Test
    fun finishingEntry_clears_onceStoreEpisodeReadsDownloaded() = runTest {
        val state = AppState(
            episodesByFeed = mapOf("feed" to listOf(episodeInStore(PersistedDownloadState.Downloaded))),
            downloads = mapOf(guid to EpisodeDownloadStatus.Finishing),
        )

        val actions = sideEffects.clearFinishedDownloads()
            .output(SetEpisodes(state.episodesByFeed), state = state).toList()

        assertThat(actions).containsExactly(SetEpisodeDownloadStatus(guid, null))
    }

    /** The whole point of Finishing: no clear (and thus no Download-button flash) while
     * the Room downloaded flag is still in flight to the store. */
    @Test
    fun finishingEntry_lingers_whileStoreEpisodeStillReadsNotDownloaded() = runTest {
        val state = AppState(
            episodesByFeed = mapOf("feed" to listOf(episodeInStore(PersistedDownloadState.NotDownloaded))),
            downloads = mapOf(guid to EpisodeDownloadStatus.Finishing),
        )

        val actions = sideEffects.clearFinishedDownloads()
            .output(SetEpisodes(state.episodesByFeed), state = state).toList()

        assertThat(actions).isEmpty()
    }

    /** Re-download of an already-Downloaded episode: the flag never changes so Room may
     * never re-emit — the Finishing action itself must trigger the clear. */
    @Test
    fun finishingEntry_clears_whenFlagAlreadyDownloadedAsFinishingLands() = runTest {
        val state = AppState(
            episodesByFeed = mapOf("feed" to listOf(episodeInStore(PersistedDownloadState.Downloaded))),
            downloads = mapOf(guid to EpisodeDownloadStatus.Finishing),
        )

        val actions = sideEffects.clearFinishedDownloads()
            .output(SetEpisodeDownloadStatus(guid, EpisodeDownloadStatus.Finishing), state = state).toList()

        assertThat(actions).containsExactly(SetEpisodeDownloadStatus(guid, null))
    }

    /** An episode gone from the store (feed pruned mid-download) can never settle; drop
     * its entry so it doesn't hold the download queue active forever. */
    @Test
    fun finishingEntry_clears_whenEpisodeMissingFromStore() = runTest {
        val state = AppState(downloads = mapOf(guid to EpisodeDownloadStatus.Finishing))

        val actions = sideEffects.clearFinishedDownloads()
            .output(SetEpisodes(emptyMap()), state = state).toList()

        assertThat(actions).containsExactly(SetEpisodeDownloadStatus(guid, null))
    }

    /** A lingering Failure on a downloaded episode is a retry affordance, not a
     * completion handshake — it must survive. */
    @Test
    fun failureEntry_neverCleared_evenWhenEpisodeReadsDownloaded() = runTest {
        val state = AppState(
            episodesByFeed = mapOf("feed" to listOf(episodeInStore(PersistedDownloadState.Downloaded))),
            downloads = mapOf(guid to EpisodeDownloadStatus.Failure("boom")),
        )

        val actions = sideEffects.clearFinishedDownloads()
            .output(SetEpisodes(state.episodesByFeed), state = state).toList()

        assertThat(actions).isEmpty()
    }

    @Test
    fun confirmAdRange_recordsFingerprintForTheEpisodesFeed() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { confirmAd(outputFile, fingerprintStore, 60_000L, 90_000L) } returns
                AdFingerprintInfo(
                    id = "abc123",
                    provenance = AdFingerprintInfo.Provenance.HUMAN_CONFIRMED,
                    durationMs = 30_000L,
                    sizeBytes = 480_000L,
                )
        }

        val actions = sideEffects.confirmAds(tacita, episodeRepo, downloadsRepo)
            .output(ConfirmAdRange(guid, 60.seconds, 90.seconds)).toList()

        assertThat(actions).containsExactly(MarkAdRangeConfirmed(guid, 60.seconds, 90.seconds, "abc123"))
        coVerify(exactly = 1) { tacita.confirmAd(outputFile, fingerprintStore, 60_000L, 90_000L) }
        verify { downloadsRepo.fingerprintStorePath("feed") }
    }

    @Test
    fun confirmAdRange_failureNeverPropagates() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { confirmAd(any(), any(), any(), any()) } throws IllegalArgumentException("range is too short")
        }

        val actions = sideEffects.confirmAds(tacita, episodeRepo, downloadsRepo)
            .output(ConfirmAdRange(guid, 60.seconds, 62.seconds)).toList()

        assertThat(actions).isEmpty()
    }

    @Test
    fun unconfirmAdRange_removesFingerprintAndUnmarks() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(fingerprintStore, "abc123") } returns true
        }

        val actions = sideEffects.unconfirmAds(tacita, episodeRepo, downloadsRepo)
            .output(UnconfirmAdRange(guid, "abc123")).toList()

        assertThat(actions).containsExactly(MarkAdRangeUnconfirmed(guid, "abc123"))
        coVerify(exactly = 1) { tacita.removeFingerprint(fingerprintStore, "abc123") }
    }

    @Test
    fun unconfirmAdRange_fingerprintAlreadyGone_stillUnmarks() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(any(), any()) } returns false
        }

        val actions = sideEffects.unconfirmAds(tacita, episodeRepo, downloadsRepo)
            .output(UnconfirmAdRange(guid, "abc123")).toList()

        // the store agrees the confirmation no longer exists, so the mark clears anyway
        assertThat(actions).containsExactly(MarkAdRangeUnconfirmed(guid, "abc123"))
    }

    @Test
    fun unconfirmAdRange_failureNeverPropagates_andLeavesTheMark() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(any(), any()) } throws RuntimeException("store is corrupt")
        }

        val actions = sideEffects.unconfirmAds(tacita, episodeRepo, downloadsRepo)
            .output(UnconfirmAdRange(guid, "abc123")).toList()

        assertThat(actions).isEmpty()
    }

    private val humanFingerprintInfo = AdFingerprintInfo(
        id = "abc123",
        provenance = AdFingerprintInfo.Provenance.HUMAN_CONFIRMED,
        durationMs = 30_000L,
        sizeBytes = 480_000L,
    )
    private val humanFingerprint =
        AdFingerprint("abc123", AdFingerprint.Provenance.HumanConfirmed, 30.seconds, 480_000L)

    @Test
    fun loadAdFingerprints_emitsOneSetPerSubscribedFeed() = runTest {
        val emptyStore = "/data/fingerprints/otherhash.tacita-fp".toPath()
        every { downloadsRepo.fingerprintStorePath("feed-1") } returns fingerprintStore
        every { downloadsRepo.fingerprintStorePath("feed-2") } returns emptyStore
        val tacita = mockk<Tacita> {
            coEvery { fingerprints(fingerprintStore) } returns listOf(humanFingerprintInfo)
            coEvery { fingerprints(emptyStore) } returns emptyList()
        }
        val state = AppState(
            subscriptions = listOf(
                Podcast(feedUrl = "feed-1", title = "One"),
                Podcast(feedUrl = "feed-2", title = "Two"),
            ),
        )

        val actions = sideEffects.loadAdFingerprints(tacita, downloadsRepo)
            .output(LoadAdFingerprints, state = state).toList()

        assertThat(actions).containsExactly(
            SetAdFingerprints("feed-1", listOf(humanFingerprint)),
            SetAdFingerprints("feed-2", emptyList()),
        )
    }

    @Test
    fun loadAdFingerprints_unreadableStore_reportsEmpty() = runTest {
        val tacita = mockk<Tacita> {
            coEvery { fingerprints(any()) } throws RuntimeException("store is corrupt")
        }
        val state = AppState(subscriptions = listOf(Podcast(feedUrl = "feed-1", title = "One")))

        val actions = sideEffects.loadAdFingerprints(tacita, downloadsRepo)
            .output(LoadAdFingerprints, state = state).toList()

        assertThat(actions).containsExactly(SetAdFingerprints("feed-1", emptyList()))
    }

    @Test
    fun removeAdFingerprint_removesAndRefreshesTheFeed() = runTest {
        every { downloadsRepo.fingerprintStorePath("feed-1") } returns fingerprintStore
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(fingerprintStore, "abc123") } returns true
            coEvery { fingerprints(fingerprintStore) } returns emptyList()
        }

        val actions = sideEffects.removeAdFingerprints(tacita, episodeRepo, downloadsRepo)
            .output(RemoveAdFingerprint("feed-1", "abc123")).toList()

        assertThat(actions).containsExactly(SetAdFingerprints("feed-1", emptyList()))
        coVerify(exactly = 1) { tacita.removeFingerprint(fingerprintStore, "abc123") }
    }

    @Test
    fun removeAdFingerprint_alsoUnmarksThePlayingEpisodeOfThatFeed() = runTest {
        // the episode mock's feedUrl is "feed" (see episodeRepo above)
        every { downloadsRepo.fingerprintStorePath("feed") } returns fingerprintStore
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(any(), any()) } returns true
            coEvery { fingerprints(any()) } returns emptyList()
        }
        val state = AppState(nowPlaying = NowPlayingState(episodeGuid = guid, episodeTitle = "Ep"))

        val actions = sideEffects.removeAdFingerprints(tacita, episodeRepo, downloadsRepo)
            .output(RemoveAdFingerprint("feed", "abc123"), state = state).toList()

        assertThat(actions).containsExactly(
            MarkAdRangeUnconfirmed(guid, "abc123"),
            SetAdFingerprints("feed", emptyList()),
        )
    }

    @Test
    fun removeAdFingerprint_failureLeavesStateUntouched() = runTest {
        every { downloadsRepo.fingerprintStorePath(any()) } returns fingerprintStore
        val tacita = mockk<Tacita> {
            coEvery { removeFingerprint(any(), any()) } throws RuntimeException("store is corrupt")
        }

        val actions = sideEffects.removeAdFingerprints(tacita, episodeRepo, downloadsRepo)
            .output(RemoveAdFingerprint("feed-1", "abc123")).toList()

        assertThat(actions).isEmpty()
    }

    @Test
    fun deleteDownload_deletesAndClearsStatus() = runTest {
        val actions = sideEffects.deleteDownloads(downloadsRepo).output(DeleteDownload(guid)).toList()

        assertThat(actions).containsExactly(SetEpisodeDownloadStatus(guid, null))
        coVerify(exactly = 1) { downloadsRepo.deleteDownload(guid) }
    }

    /** A slow file delete must not hold up the action-collection path (same
     * relay-stall class as the saturated download queue above). */
    @Test
    fun deleteDownload_slowDelete_doesNotBlockOtherDeletes() = runTest {
        val otherGuid = "other-guid"
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        coEvery { downloadsRepo.deleteDownload(any()) } coAnswers {
            val g = firstArg<String>()
            started += g
            if (g == guid) gate.await()
        }

        val collector = launch {
            sideEffects.deleteDownloads(downloadsRepo).output(DeleteDownload(guid), DeleteDownload(otherGuid)).toList()
        }
        runCurrent()
        assertThat(started).containsExactly(guid, otherGuid)

        gate.complete(Unit)
        collector.join()
    }

    @Test
    fun scheduler_activatesOnFirstDownload_idlesWhenQueueEmpties() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(
                DownloadEpisode("a"),
                DownloadEpisode("b"), // second enqueue while active: no re-activation
                SetEpisodeDownloadStatus("a", null),
                SetEpisodeDownloadStatus("b", null),
            )
            coEvery { currentState() } returnsMany listOf(
                AppState(downloads = mapOf("b" to EpisodeDownloadStatus.Queued)),
                AppState(),
            )
        }

        val effect = sideEffects.scheduleBackgroundDownloads(scheduler)
        with(effect) { context.act() }.toList()

        verifyOrder {
            scheduler.onQueueActive()
            scheduler.onQueueIdle()
        }
        verify(exactly = 1) { scheduler.onQueueActive() }
        verify(exactly = 1) { scheduler.onQueueIdle() }
    }

    @Test
    fun scheduler_treatsLingeringFailuresAsIdle() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)
        val failedState = AppState(downloads = mapOf("a" to EpisodeDownloadStatus.Failure("boom")))
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(
                DownloadEpisode("a"),
                SetEpisodeDownloadStatus("a", EpisodeDownloadStatus.Failure("boom")),
            )
            coEvery { currentState() } returns failedState
        }

        val effect = sideEffects.scheduleBackgroundDownloads(scheduler)
        with(effect) { context.act() }.toList()

        verify(exactly = 1) { scheduler.onQueueIdle() }
    }

    @Test
    fun scheduler_ignoresStatusChangesWhileInactive() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxUnitFun = true)

        sideEffects.scheduleBackgroundDownloads(scheduler)
            .output(SetEpisodeDownloadStatus("a", null))
            .toList()

        verify(exactly = 0) { scheduler.onQueueActive() }
        verify(exactly = 0) { scheduler.onQueueIdle() }
    }

    private fun SideEffect<AppState>.output(vararg input: Action, state: AppState = AppState()): Flow<Action> {
        val context = mockk<SideEffectContext<AppState>> {
            every { actions } returns flowOf(*input)
            coEvery { currentState() } returns state
        }
        return with(this) { context.act() }
    }
}
