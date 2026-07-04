package com.episode6.podcasthacker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.episode6.podcasthacker.ui.nav.AddPodcastRoute
import com.episode6.podcasthacker.ui.nav.EpisodeDetailRoute
import com.episode6.podcasthacker.ui.nav.GridRoute
import com.episode6.podcasthacker.ui.nav.NowPlayingRoute
import com.episode6.podcasthacker.ui.nav.PodcastDetailRoute
import com.episode6.podcasthacker.ui.screens.AddPodcastScreen
import com.episode6.podcasthacker.ui.screens.EpisodeDetailScreen
import com.episode6.podcasthacker.ui.screens.GridScreen
import com.episode6.podcasthacker.ui.screens.NowPlayingScreen
import com.episode6.podcasthacker.ui.screens.PodcastDetailScreen

@Composable
internal fun RootUi() {
    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                composable<NowPlayingRoute> { NowPlayingScreen(navController) }
            }
            MiniPlayerBar(
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
