package com.episode6.podcasthacker.inject

import com.episode6.podcasthacker.AppDirs
import com.episode6.podcasthacker.PlatformContext
import com.episode6.podcasthacker.appDirs
import com.episode6.podcasthacker.store.AppState
import com.episode6.podcasthacker.store.AppStore
import com.episode6.podcasthacker.store.reduce
import com.episode6.redux.StoreFlow
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectMiddleware
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@DependencyGraph(AppScope::class)
interface AppGraph {
    val appStore: AppStore
    val appDirs: AppDirs

    @Provides @SingleIn(AppScope::class)
    fun provideAppDirs(context: PlatformContext): AppDirs = context.appDirs()

    @Provides @SingleIn(AppScope::class)
    fun provideAppCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    fun interface Factory {
        fun create(@Provides context: PlatformContext): AppGraph
    }
}

fun createAppGraph(context: PlatformContext): AppGraph =
    createGraphFactory<AppGraph.Factory>().create(context)
