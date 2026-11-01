plugins {
    id("com.android.application")
}

android {
    namespace = "app.hyderabadatlas"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.hyderabadatlas"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // Sideload signing key kept in the repo so every future build can update
    // the installed app without uninstalling (which would wipe your progress).
    signingConfigs {
        create("atlas") {
            storeFile = file("../keystore/atlas-release.jks")
            storePassword = "hyderabad-atlas"
            keyAlias = "atlas"
            keyPassword = "hyderabad-atlas"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("atlas")
        }
        debug {
            signingConfig = signingConfigs.getByName("atlas")
        }
    }

    // The web app in ../web is the single source of truth; package it as assets.
    sourceSets["main"].assets.srcDirs("../../web")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.16.0")
}
