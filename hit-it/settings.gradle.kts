@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hit-it"

// :domain is pure Kotlin/JVM and is always part of the build.
include(":domain")

// :app is the Android application module; configuring it requires the Android SDK.
// In SDK-less environments exclude it with -PskipApp, e.g.:  ./gradlew :domain:test -PskipApp
// Normal Android Studio / device builds include it automatically.
if (!providers.gradleProperty("skipApp").isPresent) {
    include(":app")
}
