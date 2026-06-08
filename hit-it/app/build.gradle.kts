import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hitit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hitit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two distributions:
    //  - lite: no on-device LLM, so NO large native libs and NO ABI filter — a small, UNIVERSAL APK
    //          that installs on any device/ABI. The Coach runs its always-on rule-based engine.
    //  - full: bundles the optional MediaPipe on-device LLM. Its native libs are big, so we ship
    //          arm64-only to keep size down (a Play release would use an App Bundle instead).
    flavorDimensions += "distribution"
    productFlavors {
        create("lite") {
            dimension = "distribution"
        }
        create("full") {
            dimension = "distribution"
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)

    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Optional on-device LLM for the Coach (rephraser) — FULL flavor only. Safe when no model is
    // present (app falls back to the rule-based coach). The lite flavor omits it entirely, which is
    // what keeps that build small and free of per-ABI native libs. No INTERNET permission needed.
    "fullImplementation"(libs.tasks.genai)

    // Instrumented tests (src/androidTest) — Room DAO tests; require a device/emulator.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.coroutines.test)
}
