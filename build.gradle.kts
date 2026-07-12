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

// The version name in self.versions.toml is the single source of truth:
// MAJOR.MINOR.PATCH, where the patch segment is reserved for hotfixing shipped releases
// (normal releases always ship X.Y.0, and cutting a release branch bumps the minor).
// The android versionCode / iOS build number is derived from the name by concatenating
// MAJOR | MINOR(4 digits) | PATCH(2 digits), e.g. 1.2.0 -> 1000200 and a 1.2.3 hotfix
// -> 1000203, so newer versions always outrank older ones and hotfixes slot between
// minors. The major has no fixed max — the code just has to stay within Google Play's
// 2,100,000,000 versionCode cap, which allows majors up to 2100. This is the single
// source of truth for the formula: release tooling (scripts/sync-ios-version.sh,
// scripts/ship-release.py) queries it via the printReleaseVersionCode /
// printSnapshotVersionCode tasks instead of reimplementing it.
//
// Snapshot builds instead hardcode 10,000,000 (v10.0.0's derived code): high enough to
// install over every prod build below v10 for the foreseeable future, low enough to
// leave plenty of schema wiggle room if a build with this code ever shipped by accident.
val snapshotVersionCode = 10_000_000
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
    require(minor <= 9999) { "minor version maxes out at 9999 (got '$selfVersionName')" }
    require(patch <= 99) { "patch version maxes out at 99 (got '$selfVersionName')" }
    val code = (major.toLong() * 10000 + minor) * 100 + patch
    require(code <= 2_100_000_000L) {
        "versionCode $code for '$selfVersionName' exceeds Google Play's 2,100,000,000 cap; the major version is too large"
    }
    code.toInt()
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