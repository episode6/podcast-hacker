package com.episode6.podcasthacker.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// okhttp on android/jvm, darwin on ios
internal expect fun platformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
