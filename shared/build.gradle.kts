import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    androidLibrary {
       namespace = "com.episode6.podcasthacker.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            // rememberLauncherForActivityResult for the OPML SAF file pickers
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.jna)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
            // headless (offscreen-skiko) compose ui tests — no display needed, runs in CI
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        getByName("androidHostTest").dependencies {
            // real XmlPullParser for RssParser's android impl; the host-test android.jar
            // only has non-functional stubs
            implementation(libs.kxml2)
        }
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.coil.networkKtor3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3Adaptive)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.client.core)
            implementation(libs.navigation.compose)
            implementation(libs.okio)
            api(libs.redux.storeFlow)
            api(libs.redux.sideEffects)
            implementation(libs.redux.compose)
            implementation(libs.room.runtime)
            implementation(libs.rssparser)
            implementation(libs.sqlite.bundled)
            implementation(libs.tacita)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.assertk)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.client.mock)
            implementation(libs.redux.testSupport)
        }
    }
}

// BuildInfo.kt is generated into commonMain so every target (and the upcoming
// version/snapshot UI) can read the app version + snapshot flag at runtime
val selfVersionCode: Int by rootProject.extra
val selfIsSnapshot: Boolean by rootProject.extra
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val versionName = self.versions.name.get()
    val versionCode = selfVersionCode
    val isSnapshot = selfIsSnapshot
    val outDir = layout.buildDirectory.dir("generated/buildInfo/kotlin")
    inputs.property("versionName", versionName)
    inputs.property("versionCode", versionCode)
    inputs.property("isSnapshot", isSnapshot)
    outputs.dir(outDir)
    doLast {
        val outFile = outDir.get().file("com/episode6/podcasthacker/BuildInfo.kt").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            |package com.episode6.podcasthacker
            |
            |/** Generated from self.versions.toml at build time; do not edit. */
            |object BuildInfo {
            |    const val VERSION_NAME: String = "$versionName"
            |    const val VERSION_CODE: Int = $versionCode
            |
            |    /** False only when CI builds from a release tag; true everywhere else, including local builds. */
            |    const val IS_SNAPSHOT: Boolean = $isSnapshot
            |}
            |""".trimMargin()
        )
    }
}
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateBuildInfo)
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)

    add("kspJvm", libs.room.compiler)
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}