import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :domain — pure Kotlin/JVM. No Android dependencies, so it compiles and its unit tests
// run anywhere (including SDK-less CI/containers) via `./gradlew :domain:test -PskipApp`.
// Targets JVM 17 so its bytecode is consumable by the Android :app module (D8/R8).

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
