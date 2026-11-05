import groovy.json.JsonGenerator
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.nonverbal.mastery"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nonverbal.mastery"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // No NDK here; ship the AndroidX native libs as built rather than attempting to strip them.
        jniLibs { keepDebugSymbols += "**/*.so" }
    }
    androidResources {
        // Hero briefs (*.md) live beside the images for designers; they do not ship in the APK.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:!*.md"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric fetches its Android runtime at test time; use Google's Maven Central mirror,
            // which serves identical artifacts and is far less prone to rate limiting.
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            it.maxHeapSize = "3g"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Content is authored one level per file under /content so that writing can resume
 * across sessions without anyone editing a single multi-megabyte JSON document.
 * This task stitches the sources into the one pack the app reads: assets/content/pack.json.
 */
abstract class AssembleContentPack : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun assemble() {
        val root = contentDir.get().asFile
        val slurper = JsonSlurper()

        @Suppress("UNCHECKED_CAST")
        val meta = slurper.parse(File(root, "pack.meta.json")) as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val categories = meta["categories"] as List<MutableMap<String, Any?>>
        for (category in categories) {
            val dir = File(root, "levels/${category["id"]}")
            @Suppress("UNCHECKED_CAST")
            val levels = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
                .map { slurper.parse(it) as Map<String, Any?> }
                .sortedBy { (it["index"] as Number).toInt() }
            category["levels"] = levels
        }
        meta["poses"] = slurper.parse(File(root, "poses.json"))

        val json = JsonGenerator.Options().disableUnicodeEscaping().build().toJson(meta)
        val out = File(outputDir.get().asFile, "content/pack.json")
        out.parentFile.mkdirs()
        out.writeText(json)
    }
}

val assembleContentPack = tasks.register<AssembleContentPack>("assembleContentPack") {
    contentDir.set(rootProject.layout.projectDirectory.dir("content"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(assembleContentPack, AssembleContentPack::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
