rootProject.name = "PodcastHacker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // lets a locally-built tacita snapshot (./gradlew publishToMavenLocal in the
        // tacita repo) take precedence while developing against unreleased tacita
        // changes; scoped + snapshotsOnly so it can never shadow released artifacts
        mavenLocal {
            mavenContent {
                includeGroup("com.episode6.tacita")
                snapshotsOnly()
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                includeGroup("com.episode6.tacita")
                snapshotsOnly()
            }
        }
    }
    versionCatalogs {
        create("self") {
            from(files("self.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")