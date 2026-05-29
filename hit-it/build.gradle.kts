// Root build file for "Hit it".
//
// Note: there is intentionally no `plugins { ... apply false }` block here.
// Each module declares the plugin versions it needs via the version catalog
// (gradle/libs.versions.toml). This avoids putting the Android Gradle Plugin on the
// root classpath, so the pure-JVM :domain module can be built/tested in environments
// without the Android SDK:  ./gradlew :domain:test -PskipApp

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
