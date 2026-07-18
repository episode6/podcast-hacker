package com.episode6.podcasthacker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.GridRoute
import com.episode6.podcasthacker.ui.nav.LicensesRoute
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.nav.RecentlyPlayedRoute
import com.episode6.podcasthacker.ui.screens.AddPodcastScreen
import com.episode6.podcasthacker.ui.screens.EpisodeDetailScreen
import com.episode6.podcasthacker.ui.screens.GridScreen
import com.episode6.podcasthacker.ui.screens.LicensesScreen
import com.episode6.podcasthacker.ui.screens.NowPlayingScreen
import com.episode6.podcasthacker.ui.screens.PodcastDetailScreen
import com.episode6.podcasthacker.ui.screens.RecentlyPlayedScreen
import kotlinx.coroutines.flow.Flow

/** Duration of the NowPlaying slide-up/down transition and the matching MiniPlayerBar fade. */
internal const val NOW_PLAYING_TRANSITION_MILLIS = 300

/** Matches the NavHost default cross-fade spec, replaced for other destinations by our overrides. */
private const val DEFAULT_TRANSITION_MILLIS = 700

@Composable
internal fun RootUi(openNowPlayingRequests: Flow<Unit>) {
    val navController = rememberNavController()
    LaunchedEffect(openNowPlayingRequests) {
        openNowPlayingRequests.collect {
            navController.navigate(NowPlayingRoute) { launchSingleTop = true }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = GridRoute,
                modifier = Modifier.fillMaxSize(),
                // NowPlaying slides up over the current screen like a bottom sheet, so the
                // screen underneath should hold still instead of cross-fading (the NavHost
                // default, kept for all other destinations).
                exitTransition = {
                    if (targetState.destination.hasRoute(NowPlayingRoute::class)) {
                        ExitTransition.KeepUntilTransitionsFinished
                    } else {
                        fadeOut(animationSpec = tween(DEFAULT_TRANSITION_MILLIS))
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.hasRoute(NowPlayingRoute::class)) {
                        EnterTransition.None
                    } else {
                        fadeIn(animationSpec = tween(DEFAULT_TRANSITION_MILLIS))
                    }
                },
            ) {
                composable<GridRoute> { GridScreen(navController) }
                composable<AddPodcastRoute> { AddPodcastScreen(navController) }
                composable<PodcastDetailRoute> { PodcastDetailScreen(navController, it.toRoute()) }
                composable<EpisodeDetailRoute> { EpisodeDetailScreen(navController, it.toRoute()) }
                composable<NowPlayingRoute>(
                    enterTransition = {
                        slideInVertically(animationSpec = tween(NOW_PLAYING_TRANSITION_MILLIS)) { it }
                    },
                    popExitTransition = {
                        slideOutVertically(animationSpec = tween(NOW_PLAYING_TRANSITION_MILLIS)) { it }
                    },
                ) { NowPlayingScreen(navController) }
                composable<RecentlyPlayedRoute> { RecentlyPlayedScreen(navController) }
                composable<LicensesRoute> { LicensesScreen(navController) }
            }
            MiniPlayerBar(
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
