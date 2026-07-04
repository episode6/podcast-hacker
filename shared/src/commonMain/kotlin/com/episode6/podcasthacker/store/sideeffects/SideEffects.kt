package com.episode6.podcasthacker.store.sideeffects

import com.episode6.podcasthacker.store.AppState
import com.episode6.redux.Action
import com.episode6.redux.sideeffects.SideEffect
import com.episode6.redux.sideeffects.SideEffectContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

// convenience method for a typed app-state side effect
internal fun sideEffect(effect: SideEffectContext<AppState>.() -> Flow<Action>): SideEffect<AppState> =
    SideEffect { effect() }

internal fun <T> Flow<T>.mapActions(mapper: (T) -> List<Action>): Flow<Action> =
    transform { value -> mapper(value).forEach { emit(it) } }
