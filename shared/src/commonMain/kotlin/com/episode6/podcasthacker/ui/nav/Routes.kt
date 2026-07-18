package com.episode6.podcasthacker.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object GridRoute
@Serializable data object AddPodcastRoute
@Serializable data class PodcastDetailRoute(val feedUrl: String)
@Serializable data class EpisodeDetailRoute(val feedUrl: String, val episodeGuid: String)
@Serializable data object RecentlyPlayedRoute
@Serializable data object LicensesRoute
