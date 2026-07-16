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

// snapshot builds carry their own app identity so they install side-by-side with
// release builds instead of overwriting them. selfAppName is the user-facing display
// name, selfAppId the android applicationId / macOS bundle id. (Android debug builds
// additionally append a .debug applicationIdSuffix — see androidApp/build.gradle.kts —
// so they coexist with installed CI-built snapshot APKs too.) The iOS equivalents
// live in iosApp/Configuration/Config.xcconfig (committed = snapshot identity;
// scripts/sync-ios-version.sh --release swaps in the release identity on tag builds).
val selfAppName: String by extra(if (selfIsSnapshot) "PodcastHacker (SNAPSHOT)" else "PodcastHacker")
val selfAppId: String by extra(if (selfIsSnapshot) "com.episode6.podcasthacker.snapshot" else "com.episode6.podcasthacker")

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
// Snapshot builds instead derive their versionCode from git: the commit count at HEAD's
// merge-base with main. Snapshots install under their own applicationId, so their codes
// never compete with release codes; builds from main carry a strictly growing code (a
// newer main snapshot always installs over an older one), while branch/PR builds are
// locked to their closest main ancestor's code so a later main build can install right
// over them. This needs full git history — CI checkouts set fetch-depth: 0, and a
// shallow clone is rejected rather than silently under-counting.
//
// The committed iOS xcconfig can't carry a per-commit number, so snapshot iOS builds
// stay pinned to build number 1 — see printSnapshotVersionCode below and
// scripts/sync-ios-version.sh.
val iosSnapshotBuildNumber = 1
fun git(vararg args: String): String = providers.exec {
    workingDir(rootDir)
    commandLine("git", *args)
}.standardOutput.asText.get().trim()
val gitSnapshotVersionCode: Int by lazy {
    require(git("rev-parse", "--is-shallow-repository") == "false") {
        "snapshot versionCode is derived from git commit count, which a shallow clone " +
            "would under-count — fetch full history (CI: fetch-depth: 0)"
    }
    val mainRef = listOf("origin/main", "main").firstOrNull { ref ->
        providers.exec {
            workingDir(rootDir)
            commandLine("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}")
            isIgnoreExitValue = true
        }.result.get().exitValue == 0
    }
    requireNotNull(mainRef) { "snapshot versionCode needs a main ref (origin/main or main) to merge-base against" }
    git("rev-list", "--count", git("merge-base", mainRef, "HEAD")).toInt()
}
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
val selfVersionCode: Int by extra(if (selfIsSnapshot) gitSnapshotVersionCode else selfReleaseVersionCode)

// query tasks for the release tooling (use with -q and take the last output line);
// printReleaseVersionCode reports the formula-derived code regardless of snapshot-ness
tasks.register("printReleaseVersionCode") {
    val code = selfReleaseVersionCode
    doLast { println(code) }
}
// printSnapshotVersionCode reports the pinned build number the committed iOS xcconfig
// carries for snapshot builds — NOT the git-derived code android/desktop snapshots use
tasks.register("printSnapshotVersionCode") {
    val code = iosSnapshotBuildNumber
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