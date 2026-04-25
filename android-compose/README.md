# Mashup Studio — Compose / Material 3 Variant

This is the **path C** Android client: a full Jetpack Compose + Material 3 source
tree intended to be built locally where Google Maven (`maven.google.com`) is
reachable. The build sandbox where Path A (`android/`) was produced cannot
reach Google Maven, so AndroidX / Compose / Material Components could not be
fetched there — that's why two clients exist.

## Build it

```bash
# from this directory (android-compose/)
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Requires:
- JDK 17+
- Android SDK with platform 34 + build-tools 34
- Gradle 8.10+ (the wrapper points at 8.10.2)
- A network that allows `maven.google.com` and Maven Central

If you don't already have an Android SDK, the easiest path is Android Studio
(Hedgehog or newer) — open this directory as a project, accept its prompt to
install the matching SDK, then **Build → Generate Signed APK**.

## What's different vs. Path A (`android/`)

| Aspect | Path A (`android/`) | Path C (`android-compose/`) |
|---|---|---|
| Toolchain | manual `aapt2 + kotlinc + d8` | Gradle + AGP 8.7 |
| UI | XML layouts + `findViewById` | Jetpack Compose 1.7 (BOM 2024.10) |
| Components | framework `Theme.DeviceDefault` | Material 3 (`androidx.compose.material3`) |
| Dynamic colour | colour-defined in `colors.xml` | `dynamicDarkColorScheme()` on Android 12+ |
| Networking | hand-rolled `HttpURLConnection` | Retrofit + OkHttp + kotlinx-serialization |
| Audio playback | framework `MediaPlayer` | Media3 `ExoPlayer` |
| State | activity fields + `Handler` | `ViewModel` + `StateFlow` |
| Navigation | single-Activity visibility toggling | `AnimatedContent` + Compose state |
| Splits | universal APK | unsplit (release builds shrink with R8) |

The two clients hit the **same FastAPI backend** (`backend/server.py`) and use
the same trending-hook detection, compatibility scoring, and mashup pipeline.
You can install both side-by-side only if you change the `applicationId` in
one of them.

## What "AI viral-hook detection" does

When you tap **🔥 Find viral hooks**, the backend (`spotify_mashup/trending_detector.py`):

1. Pulls Spotify's audio analysis for the track (sections / beats / loudness /
   timbre vectors).
2. Scores each section by a blend of:
   - Loudness peak vs. the rest of the track
   - Loudness *jump* from the previous section (drops)
   - Late-track climax position (final-chorus boost)
   - Spotify's tempo / key / time-signature confidence
3. Best-effort augments with external signals:
   - TikTok comment / caption timestamps for the track
   - YouTube search-results timestamps
   - Genius `[Chorus]`/`[Hook]`/`[Drop]` tag positions
4. Returns the top-K sections, ranked by a virality score with reason strings
   (e.g. *"sharp loudness jump from previous section"*, *"high TikTok timestamp
   density"*).

External signals fail gracefully — Spotify alone still produces sensible
results. Add Spotify Developer credentials in `spotify_mashup/spotify_fetcher.py`
or as `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` env vars.

## Project layout

```
android-compose/
├── build.gradle.kts          ← root: AGP / Kotlin / Compose plugins
├── settings.gradle.kts       ← module declaration + repos
├── gradle.properties
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts      ← app deps (Compose, Material3, Retrofit, Media3)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/              ← shares the same icons as path A
        └── java/com/spotifymashup/generator/
            ├── MainActivity.kt
            ├── data/
            │   ├── Models.kt              ← @Serializable DTOs
            │   ├── MashupApi.kt           ← Retrofit interface
            │   └── MashupRepository.kt
            ├── viewmodel/
            │   └── MashupViewModel.kt     ← StateFlow + business logic
            └── ui/
                ├── theme/                  ← Material 3 + dynamic colour
                │   ├── Color.kt
                │   ├── Type.kt
                │   └── Theme.kt
                ├── components/
                │   ├── Waveform.kt         ← Compose Canvas waveform
                │   └── HookCard.kt         ← Material 3 hook card
                └── screens/
                    ├── HomeScreen.kt
                    ├── ProgressScreen.kt
                    └── ResultScreen.kt
```
