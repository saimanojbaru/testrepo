import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sortora.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sortora.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // ML Kit's bundled models ship native code per CPU type (~30 MB each). Build one APK per
    // ABI so a phone only downloads its own; App Bundles (bundleRelease) are split by Play anyway.
    // 32-bit x86 is dropped: no current phone or emulator image needs it.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric smoke test launches the real app on the JVM.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into FileDescriptor internals on JDK 17+.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Thumbnails. Only the core + video decoder: no coil-network module, so no HTTP client ships.
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // On-device AI (bundled models).
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.litert)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Privacy guard: fails the build if any library sneaks the INTERNET permission back into the
 * merged manifest. ML Kit's transitive telemetry library declares it, and the app manifest
 * strips it with tools:node="remove". This task makes sure that stays true forever.
 */
abstract class VerifyNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        if (manifest.contains("android.permission.INTERNET")) {
            throw GradleException(
                "Privacy check failed: the merged manifest requests android.permission.INTERNET. " +
                    "Sortora must stay fully offline. Remove the dependency that adds it, or strip it " +
                    "with tools:node=\"remove\" in AndroidManifest.xml.",
            )
        }
        logger.lifecycle("Privacy check passed: no INTERNET permission in ${mergedManifest.get().asFile.name}")
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verifyTask = tasks.register<VerifyNoInternetPermissionTask>("verify${variantName}NoInternetPermission") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest requests the INTERNET permission."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.configureEach {
            if (name == "assemble$variantName" || name == "bundle$variantName" || name == "check") {
                dependsOn(verifyTask)
            }
        }
    }
}
