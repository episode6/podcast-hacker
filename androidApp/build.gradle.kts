import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// derived from self.versions.name in the root build script (see the formula there)
val selfVersionCode: Int by rootProject.extra
val selfIsSnapshot: Boolean by rootProject.extra
val selfAppName: String by rootProject.extra
val selfAppId: String by rootProject.extra
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    // registers androidx.activity.ComponentActivity in the debug manifest so device
    // tests can host App() with a test graph instead of launching MainActivity
    debugImplementation(libs.androidx.compose.uiTestManifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.uiTestJunit4)
    // ui-test-junit4 pulls espresso 3.5.0 transitively, which crashes on api 36+
    // (removed InputManager.getInstance); force the newer pin
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.kotlin.testJunit)
    androidTestImplementation(libs.okio)
}

android {
    namespace = "com.episode6.podcasthacker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        // for the snapshot-aware app_name resValue in defaultConfig
        resValues = true
    }

    defaultConfig {
        // snapshot builds get their own applicationId and launcher label so they can
        // be installed side-by-side with the released app instead of overwriting it
        // (the namespace above stays fixed, so R + manifest class refs are unaffected)
        applicationId = selfAppId
        resValue("string", "app_name", selfAppName)
        // snapshot builds also get their own launcher icon (white glyph on episode6
        // orange) so the two installs are distinguishable at a glance; placeholders
        // resolve at manifest merge, so lint + resource shrinking still see the
        // concrete @mipmap reference per build
        manifestPlaceholders["appIcon"] =
            if (selfIsSnapshot) "@mipmap/ic_launcher_snapshot" else "@mipmap/ic_launcher"
        manifestPlaceholders["appIconRound"] =
            if (selfIsSnapshot) "@mipmap/ic_launcher_round_snapshot" else "@mipmap/ic_launcher_round"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = selfVersionCode
        versionName = self.versions.name.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            // CI decodes the ANDROID_KEYSTORE secret to a file and exports these
            // env vars; without them (local builds, PR CI) release stays unsigned
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_ROOT_PASSWORD")
                keyAlias = "episode6"
                keyPassword = System.getenv("ANDROID_KEYSTORE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            // R8 runs in full mode by default on AGP 8+; keep rules live in proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}