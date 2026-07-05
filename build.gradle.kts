plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

allprojects {
    configurations.all {
        // tacita ships as a republished 0.0.3-SNAPSHOT; gradle's default 24h TTL for
        // changing modules (plus CI's restored gradle-home cache) can otherwise pin a
        // stale snapshot and fail compilation against an outdated API
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}