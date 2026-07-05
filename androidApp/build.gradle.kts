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

    defaultConfig {
        applicationId = "com.episode6.podcasthacker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = self.versions.code.get().toInt()
        versionName = self.versions.name.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}