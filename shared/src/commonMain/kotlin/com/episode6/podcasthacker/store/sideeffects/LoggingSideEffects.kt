package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.store.AppState
import com.episode6.redux.sideeffects.SideEffect
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

@ContributesTo(AppScope::class)
interface LoggingSideEffects {
    @Provides @IntoSet fun loggingSideEffect(): SideEffect<AppState> = sideEffect {
        actions
            .onEach { println("Action dispatched: $it") }
            .filter { false } // observe-only: never emit an action back into the store
    }
}
