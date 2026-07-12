import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.okio)

    implementation(libs.compose.uiToolingPreview)
}

val selfIsSnapshot: Boolean by rootProject.extra
val selfAppId: String by rootProject.extra
compose.desktop {
    application {
        mainClass = "com.episode6.podcasthacker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // snapshot installers get their own package name (install dir, .app name,
            // deb package, msi upgrade identity all derive from it) so they install
            // side-by-side with the released app; "-SNAPSHOT" rather than " (SNAPSHOT)"
            // because the name is also used in filesystem paths and the deb package
            // name. AppDirs.jvm.kt mirrors this split for the data/cache dirs.
            packageName = if (selfIsSnapshot) "PodcastHacker-SNAPSHOT" else "PodcastHacker"
            packageVersion = self.versions.name.get()
            // per-OS libvlc staged by scripts/fetch-libvlc.sh lands in the app image
            // (gitignored; dev builds without it fall back to a system VLC)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // jpackage requires MAJOR > 0 for the macOS app image/dmg and for msi,
            // so the version in self.versions.toml must stay >= 1.0.0
            macOS {
                // explicit so snapshot/release .apps are distinct apps to LaunchServices
                // (jpackage would otherwise derive the same id for both from mainClass)
                bundleID = selfAppId
                iconFile.set(project.file("icons/PodcastHacker.icns"))
            }
            windows {
                iconFile.set(project.file("icons/PodcastHacker.ico"))
            }
            linux {
                iconFile.set(project.file("icons/PodcastHacker.png"))
            }
        }
    }
}