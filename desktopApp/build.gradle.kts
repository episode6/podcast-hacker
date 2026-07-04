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

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.episode6.podcasthacker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PodcastHacker"
            packageVersion = self.versions.name.get()
            macOS {
                // jpackage requires MAJOR > 0 for dmg; map 0.x.y -> 1.x.y until v1.0
                dmgPackageVersion = self.versions.name.get()
                    .let { if (it.startsWith("0.")) "1." + it.substringAfter("0.") else it }
            }
            windows {
                // msi shares jpackage's MAJOR > 0 constraint
                msiPackageVersion = self.versions.name.get()
                    .let { if (it.startsWith("0.")) "1." + it.substringAfter("0.") else it }
            }
        }
    }
}