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

compose.desktop {
    application {
        mainClass = "com.episode6.podcasthacker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PodcastHacker"
            // jpackage versions are MAJOR.MINOR.PATCH only, so a hotfix release's 4th
            // segment is dropped here; the derived versionCode/build number carries it
            packageVersion = self.versions.name.get().split(".").take(3).joinToString(".")
            // per-OS libvlc staged by scripts/fetch-libvlc.sh lands in the app image
            // (gitignored; dev builds without it fall back to a system VLC)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            // jpackage requires MAJOR > 0 for the macOS app image/dmg and for msi,
            // so the version in self.versions.toml must stay >= 1.0.0
            macOS {
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