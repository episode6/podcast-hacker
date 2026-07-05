package com.episode6.podcasthacker.inject

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.appDirs
import com.episode6.podcasthacker.coroutines.ioDispatcher
import com.episode6.podcasthacker.data.db.AppDatabase
import com.episode6.podcasthacker.data.db.appDatabaseBuilder
import com.episode6.podcasthacker.data.network.ItunesSearchClient
import com.episode6.podcasthacker.data.network.platformHttpClient
import com.episode6.podcasthacker.data.repo.EpisodeRepository
import com.episode6.podcasthacker.data.repo.SubscriptionRepository
import com.episode6.podcasthacker.downloads.DownloadScheduler
import com.episode6.podcasthacker.downloads.createDownloadScheduler
import com.episode6.podcasthacker.playback.PodcastPlayer
import com.episode6.podcasthacker.playback.createPodcastPlayer
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.reduce
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectMiddleware
import com.episode6.tacita.Tacita
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.SYSTEM

/**
 * IO seams for tests: integration tests swap in a mock-engine [httpClient] and/or a
 * temp-dir [appDirs] while the rest of the graph stays production. Production code
 * passes no overrides.
 */
data class AppGraphOverrides(
    val httpClient: HttpClient? = null,
    val appDirs: AppDirs? = null,
    val podcastPlayer: PodcastPlayer? = null,
)

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appStore: AppStore
    val appDirs: AppDirs
    val httpClient: HttpClient
    val itunesSearchClient: ItunesSearchClient
    val subscriptionRepository: SubscriptionRepository
    val episodeRepository: EpisodeRepository

    @Provides @SingleIn(AppScope::class)
    fun provideAppDirs(context: PlatformContext, overrides: AppGraphOverrides): AppDirs =
        overrides.appDirs ?: context.appDirs()

    @Provides @SingleIn(AppScope::class)
    fun provideHttpClient(overrides: AppGraphOverrides): HttpClient =
        overrides.httpClient ?: platformHttpClient {
            expectSuccess = true
        }

    @Provides @SingleIn(AppScope::class)
    fun provideTacita(httpClient: HttpClient): Tacita =
        // tacita guards its internal passes (clean-source probes, the ad-boundary
        // detector) and reports their failures only through this lambda — discard it and
        // those passes fail invisibly
        Tacita.withClient(reuse = true, log = { println("tacita: $it") }) { httpClient }

    @Provides @SingleIn(AppScope::class)
    fun provideAppDatabase(context: PlatformContext, appDirs: AppDirs): AppDatabase {
        FileSystem.SYSTEM.createDirectories(appDirs.dataDir)
        return context.appDatabaseBuilder(appDirs.dataDir / AppDatabase.FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .build()
    }

    @Provides @SingleIn(AppScope::class)
    fun provideAppCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @SingleIn(AppScope::class)
    fun provideDownloadScheduler(context: PlatformContext): DownloadScheduler =
        context.createDownloadScheduler()

    @Provides @SingleIn(AppScope::class)
    fun providePodcastPlayer(
        context: PlatformContext,
        scope: CoroutineScope,
        overrides: AppGraphOverrides,
    ): PodcastPlayer = overrides.podcastPlayer ?: context.createPodcastPlayer(scope)

    @Provides @SingleIn(AppScope::class)
    fun provideAppStore(
        scope: CoroutineScope,
        sideEffects: Set<SideEffect<AppState>>,
    ): AppStore = StoreFlow(
        scope = scope,
        initialValue = AppState(),
        reducer = AppState::reduce,
        middlewares = listOf(SideEffectMiddleware(sideEffects)),
    )

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides context: PlatformContext,
            @Provides overrides: AppGraphOverrides,
        ): AppGraph
    }
}

fun createAppGraph(
    context: PlatformContext,
    overrides: AppGraphOverrides = AppGraphOverrides(),
): AppGraph = createGraphFactory<AppGraph.Factory>().create(context, overrides)
