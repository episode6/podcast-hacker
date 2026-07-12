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

// snapshot unless CI is building from a release tag (GITHUB_REF=refs/tags/v*);
// local and branch/PR builds are always snapshots
val selfIsSnapshot: Boolean by extra(System.getenv("GITHUB_REF")?.startsWith("refs/tags/v") != true)

// The version name in self.versions.toml is the single source of truth: MAJOR.MINOR.PATCH.
// Cutting a release branch bumps the patch by 10, so regular releases land on multiples
// of 10 and the 9 values in between are reserved for hotfixing that release (1.2.30 ->
// hotfixes 1.2.31-1.2.39, next release 1.2.40). The android versionCode / iOS build
// number is derived via mixed radix — (major * 256 + minor) * 10000 + patch — so newer
// versions always outrank older ones and hotfixes need no versionCode coordination with
// main. Major/minor cap at 255 (matching Windows MSI's ProductVersion limits) and patch
// at 9999, making the highest possible code 255.255.9999 -> 655,359,999, well under
// Google Play's 2,100,000,000 versionCode cap. This is the single source of truth for
// the formula: release tooling (scripts/sync-ios-version.sh, scripts/ship-release.py)
// queries it via the printReleaseVersionCode / printSnapshotVersionCode tasks instead
// of reimplementing it.
//
// Snapshot builds instead hardcode 25,600,000 (v10.0.0's derived code): high enough to
// install over every prod build below v10 for the foreseeable future (9.255.9999 is
// 25,599,999), low enough to leave plenty of schema wiggle room if a build with this
// code ever shipped by accident.
val snapshotVersionCode = 25_600_000
val selfVersionName: String = self.versions.name.get()
val selfReleaseVersionCode: Int = run {
    val segments = selfVersionName.split(".")
    require(segments.size == 3) { "version name '$selfVersionName' must be MAJOR.MINOR.PATCH" }
    val nums = segments.map { segment ->
        requireNotNull(segment.toIntOrNull()?.takeIf { it >= 0 }) {
            "version name '$selfVersionName' has a non-numeric segment '$segment'"
        }
    }
    val (major, minor, patch) = nums
    require(major >= 1) { "major version must be >= 1 (jpackage rejects MAJOR==0 for dmg/msi)" }
    require(major <= 255) { "major version maxes out at 255 (got '$selfVersionName')" }
    require(minor <= 255) { "minor version maxes out at 255 (got '$selfVersionName')" }
    require(patch <= 9999) { "patch version maxes out at 9999 (got '$selfVersionName')" }
    (major * 256 + minor) * 10000 + patch
}

// the name is validated on every build, but only release-tag builds carry its code
val selfVersionCode: Int by extra(if (selfIsSnapshot) snapshotVersionCode else selfReleaseVersionCode)

// query tasks for the release tooling (use with -q and take the last output line);
// printReleaseVersionCode reports the formula-derived code regardless of snapshot-ness
tasks.register("printReleaseVersionCode") {
    val code = selfReleaseVersionCode
    doLast { println(code) }
}
tasks.register("printSnapshotVersionCode") {
    val code = snapshotVersionCode
    doLast { println(code) }
}

allprojects {
    configurations.all {
        // tacita ships as a republished 0.0.3-SNAPSHOT; gradle's default 24h TTL for
        // changing modules (plus CI's restored gradle-home cache) can otherwise pin a
        // stale snapshot and fail compilation against an outdated API
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}