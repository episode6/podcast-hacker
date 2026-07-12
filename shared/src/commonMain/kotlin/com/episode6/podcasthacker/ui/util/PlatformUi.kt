package com.episode6.podcasthacker.ui.util

/**
 * True when screens should offer pull-to-refresh instead of a pointer-friendly toolbar
 * refresh button. Android only for now: desktop has no touch affordance, and the ios
 * app keeps the button until it gets real device testing.
 */
internal expect val platformUsesPullToRefresh: Boolean
