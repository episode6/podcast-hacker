package com.episode6.podcasthacker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.episode6.podcasthacker.ui.nav.AdFingerprintsRoute
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.GridRoute
import com.episode6.podcasthacker.ui.nav.LicensesRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.nav.RecentlyPlayedRoute
import com.episode6.podcasthacker.ui.nowplaying.LocalNowPlayingSheetState
import com.episode6.podcasthacker.ui.nowplaying.NowPlayingSheet
import com.episode6.podcasthacker.ui.nowplaying.rememberNowPlayingSheetState
import com.episode6.podcasthacker.ui.screens.AdFingerprintsScreen
import com.episode6.podcasthacker.ui.screens.AddPodcastScreen
import com.episode6.podcasthacker.ui.screens.EpisodeDetailScreen
import com.episode6.podcasthacker.ui.screens.GridScreen
import com.episode6.podcasthacker.ui.screens.LicensesScreen
import com.episode6.podcasthacker.ui.screens.PodcastDetailScreen
import com.episode6.podcasthacker.ui.screens.RecentlyPlayedScreen
import kotlinx.coroutines.flow.Flow

@Composable
internal fun RootUi(openNowPlayingRequests: Flow<Unit>) {
    val navController = rememberNavController()
    val nowPlayingSheetState = rememberNowPlayingSheetState()
    LaunchedEffect(openNowPlayingRequests, nowPlayingSheetState) {
        openNowPlayingRequests.collect { nowPlayingSheetState.expand() }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        CompositionLocalProvider(LocalNowPlayingSheetState provides nowPlayingSheetState) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = GridRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<GridRoute> { GridScreen(navController) }
                    composable<AddPodcastRoute> { AddPodcastScreen(navController) }
                    composable<PodcastDetailRoute> { PodcastDetailScreen(navController, it.toRoute()) }
                    composable<EpisodeDetailRoute> { EpisodeDetailScreen(navController, it.toRoute()) }
                    composable<RecentlyPlayedRoute> { RecentlyPlayedScreen(navController) }
                    composable<LicensesRoute> { LicensesScreen(navController) }
                    composable<AdFingerprintsRoute> { AdFingerprintsScreen(navController) }
                }
                NowPlayingSheet(state = nowPlayingSheetState)
            }
        }
    }
}
