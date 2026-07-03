package com.episode6.podcasthacker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform