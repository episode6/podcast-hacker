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

// The version name in self.versions.toml is the single source of truth:
// MAJOR.MINOR.PATCH plus an optional 4th HOTFIX segment used only when hotfixing a
// shipped release. The android versionCode / iOS build number is derived from it by
// concatenating MAJOR | MINOR(2 digits) | PATCH(3 digits) | HOTFIX(2 digits), e.g.
// 1.2.3 -> 10200300 and 1.2.3.4 -> 10200304, so newer versions always outrank older
// ones and hotfixes slot between patches. The major has no fixed max — the code just
// has to stay within Google Play's 2,100,000,000 versionCode cap, which allows majors
// up to 210. scripts/version-code.py mirrors this formula for the iOS xcconfig sync +
// release tooling; keep the two in sync.
val selfVersionName: String = self.versions.name.get()
val selfVersionCode: Int by extra(run {
    val segments = selfVersionName.split(".")
    require(segments.size in 3..4) {
        "version name '$selfVersionName' must be MAJOR.MINOR.PATCH with an optional 4th hotfix segment"
    }
    val nums = segments.map { segment ->
        requireNotNull(segment.toIntOrNull()?.takeIf { it >= 0 }) {
            "version name '$selfVersionName' has a non-numeric segment '$segment'"
        }
    }
    val (major, minor, patch) = nums
    val hotfix = nums.getOrElse(3) { 0 }
    require(major >= 1) { "major version must be >= 1 (jpackage rejects MAJOR==0 for dmg/msi)" }
    require(minor <= 99) { "minor version maxes out at 99 (got '$selfVersionName')" }
    require(patch <= 999) { "patch version maxes out at 999 (got '$selfVersionName')" }
    require(hotfix <= 99) { "hotfix version maxes out at 99 (got '$selfVersionName')" }
    val code = ((major.toLong() * 100 + minor) * 1000 + patch) * 100 + hotfix
    require(code <= 2_100_000_000L) {
        "versionCode $code for '$selfVersionName' exceeds Google Play's 2,100,000,000 cap; the major version is too large"
    }
    code.toInt()
})

// snapshot unless CI is building from a release tag (GITHUB_REF=refs/tags/v*);
// local and branch/PR builds are always snapshots
val selfIsSnapshot: Boolean by extra(System.getenv("GITHUB_REF")?.startsWith("refs/tags/v") != true)

allprojects {
    configurations.all {
        // tacita ships as a republished 0.0.3-SNAPSHOT; gradle's default 24h TTL for
        // changing modules (plus CI's restored gradle-home cache) can otherwise pin a
        // stale snapshot and fail compilation against an outdated API
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}