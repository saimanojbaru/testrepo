# Sortora

**Sort your photos, videos and screenshots with on-device AI. 100% offline.**

Sortora is an Android app in the spirit of "Sort my footage": you pick media, it analyses every file
on your phone, suggests clear names and folders, shows you a preview, and only then copies (or,
if you ask, moves) the files.

```
VID_20251012_143015.mp4      →  Talking Head/2025-10-12_talkinghead_person_desk.mp4
Screenshot_20251012-1430.png →  Screenshots/Screenshot_Settings.png
IMG_4821.jpg                 →  Pets & Animals/2025-10-12_animal_dog_beach.jpg
```

## Privacy, enforced

- **No internet permission.** ML Kit pulls in Google's telemetry library, which declares
  `android.permission.INTERNET`. The app manifest strips it (`tools:node="remove"`), so the OS
  blocks every network socket the app could open.
- **The build checks it.** `verify<Variant>NoInternetPermission` runs before every `assemble`,
  `bundle` and `check` and fails the build if any dependency adds INTERNET back.
- **What the APK does request:** notifications (optional progress), foreground service (long
  batches), plus `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`, which WorkManager
  adds. `ACCESS_NETWORK_STATE` only lets an app *ask* whether the phone is online; without INTERNET
  it cannot send or receive anything.
- **Bundled models only.** The ML Kit libraries used are the "bundled" variants: models ship inside
  the APK. Nothing is downloaded through Google Play services.
- **No storage permission.** All file access goes through the system file picker (Storage Access
  Framework). Sortora can only see the files and folders you pick.
- **No backups.** App data (settings, analysis cache, history) is excluded from cloud backup and
  device-to-device transfer.
- **Short-lived analysis data.** Labels and OCR keywords live in the app's private database only
  while a session is open. They are deleted when you start the next session. The permanent
  history keeps only file names and folders, never image content or text.

No cloud mode, no API keys, no accounts.

## Features

| | |
|---|---|
| Media | Photos (JPG, PNG, HEIC/HEIF, WebP, AVIF, GIF…), videos (MP4, MOV, MKV, WebM…), screenshots |
| On-device AI | ML Kit image labeling, object detection, face detection, text recognition (OCR) |
| Screenshots | File name, folder, EXIF (`UserComment: Screenshot` on iOS), exact screen resolution, status-bar clock found by OCR, text density |
| Scenes | Screenshot, selfie, group photo, people, document, landscape, city, food, pets, product |
| Videos | Key frames sampled across the clip → talking head / B-roll / screen recording |
| Naming | Templates with `{date} {time} {category} {labels} {text} {original}`, presets, live examples, date format, separator, lowercase, number of keywords |
| Your folders | Custom categories with keywords; related words are matched too ("Pets" catches dog, cat, bird…; "Receipts" catches *subtotal*, *tax*, *total* in OCR text) |
| Preview | Every new name and folder before anything changes; edit names and folders, exclude files, filter by folder, see what was detected and why |
| Safety | Copy by default. Move needs explicit confirmation and deletes an original only after its copy is written and the byte count verified. Existing files are never overwritten. |
| Log | Every operation is recorded with the original name (History screen, CSV export, and an optional CSV written next to the sorted files) |
| Big batches | WorkManager with a foreground notification; progress survives leaving the app and resumes after interruption |
| UI | Jetpack Compose, Material 3, dynamic colour, dark mode, onboarding, empty states |

## Build and run

Requirements:

- Android Studio with support for **AGP 9.4** (any 2026 release), or the command line with JDK 17+ (21 recommended)
- Android SDK Platform **37** (compileSdk). The app targets API 36 and runs on Android 8.0+ (API 26)

```bash
cd Sortora
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 52 logic tests + a Robolectric smoke test of the whole UI flow
./gradlew lintDebug
./gradlew verifyReleaseNoInternetPermission
```

Open the `Sortora/` folder (not the repository root) in Android Studio.

APKs are split per CPU type because ML Kit's bundled models include native code
(`app-arm64-v8a-*.apk` is the one almost every current phone needs). Release size is about
44 MB for arm64; most of that is the on-device models. `./gradlew bundleRelease` builds an App
Bundle, which Google Play splits per device automatically.

**Without a computer:** every push that touches `Sortora/` runs the *Sortora Android* GitHub Actions
workflow, which runs the tests, lint, the privacy check, and uploads the debug APKs as a build
artifact. Download `app-arm64-v8a-debug.apk` from the workflow run page and install it on your phone.

Release builds use R8 (minify + resource shrinking). Configure your own signing config before
publishing.

## How it works

```
Pick files / folder ──► MediaSourceRepository (SAF) ──► Room session
                                                          │
                     AnalyzeMediaWorker (WorkManager, foreground)
                                                          │
       ┌──────────────── OnDeviceMediaAnalyzer ───────────┴──────────────┐
       │ Images: decode ≤2k px → labels + objects + faces + OCR (parallel) │
       │ Videos: N key frames  → labels + faces per frame, OCR on 2 frames │
       │         + 16×16 luminance fingerprints for frame-to-frame change  │
       └──────────────┬───────────────────────────────────────────────────┘
                      ▼
   ScreenshotHeuristics → SceneClassifier → TagSelector     (pure Kotlin rules)
                      ▼
   Preview: SortPlanner = CategoryMatcher + FileNameGenerator + de-duplication
                      ▼  (user edits, Copy/Move, confirm)
   ApplyPlanWorker → SafFileOrganizer (copy → verify → optional delete) → History log
```

**Talking head vs B-roll.** For each sampled frame Sortora records whether there is a face, how
many, how large and how centred, plus a tiny luminance fingerprint. A clip is a talking head when
one reasonably large, roughly centred face is present in most frames and consecutive frames barely
change (a locked-off camera). Everything else that isn't a screen recording is B-roll, tagged with
its scene labels.

### Project structure

```
app/src/main/java/com/sortora/app/
├── domain/                 Pure Kotlin, no Android imports, unit-tested
│   ├── model/              MediaFile, AnalysisResult, ContentCategory, settings, sessions
│   ├── classify/           SceneClassifier, ScreenshotHeuristics, TagSelector, TextAnalysis,
│   │                       FrameSignature, LabelVocabulary (all the label knowledge)
│   ├── naming/             FileNameGenerator, FileNameSanitizer, DateParsing
│   ├── planning/           SortPlanner, CategoryMatcher
│   ├── history/            LogCsv
│   ├── repository/         Interfaces: settings, sessions, history, media source, analyzer, organizer, jobs
│   └── usecase/            Start session, run analysis, apply plan, run apply, set destination
├── data/
│   ├── ai/                 MlKitVision, OnDeviceMediaAnalyzer, BitmapLoader, VideoFrameExtractor, DisplaySizes
│   ├── media/              SafMediaSourceRepository, MetadataReader (EXIF / dates)
│   ├── file/               SafFileOrganizer (copy/move), SafTextFileWriter
│   ├── local/              Room database, entities, DAOs
│   ├── repository/         Room + DataStore implementations
│   └── work/               Workers, progress notifications, WorkManager scheduler
├── di/                     Hilt modules
└── ui/                     Compose screens + ViewModels (MVVM)
    ├── onboarding/ home/ analysis/ preview/ apply/ history/ settings/
    ├── components/ theme/ navigation/
```

### Extending

- **Teach it new concepts:** edit `LabelVocabulary` (`categoryLabels`, `concepts`, `tagAliases`). It is
  plain data.
- **Add a TensorFlow Lite model** (e.g. a Places365 scene classifier or a custom footage model):
  run it in `OnDeviceMediaAnalyzer` and append its outputs as `DetectedLabel`s. The classifier,
  tag selector and category matcher all work on plain labels, so nothing else needs to change.
  Bundle the `.tflite` file in `assets/` to stay offline.
- **New name tokens:** add a case in `FileNameGenerator.renderToken`.
- **New category:** add an entry to `ContentCategory`, a rule in `SceneClassifier`, and a label/icon in
  `ui/components/CategoryUi.kt`.

## Known limitations of on-device AI

- **General labels only.** ML Kit's on-device labeler knows about 450 everyday concepts. It does not
  recognise brands, specific landmarks, specific people or fine-grained species. Labels can be
  wrong; the preview shows confidence and flags low-confidence results.
- **Detection, not recognition.** Faces are detected (count, size, position) but never identified.
  Sortora cannot sort by *who* is in a photo.
- **OCR is Latin-script only.** The bundled text recogniser reads Latin script. Chinese, Japanese,
  Korean and Devanagari need their own bundled ML Kit models (easy to add, each adds APK size).
- **Talking head / B-roll is a heuristic.** It is visual only (no audio or speech analysis) and
  looks at a handful of frames. Expect mistakes on walk-and-talk vlogs, interviews with cutaways,
  screen shares with a face cam, and static shots of a person. More sampled frames (Settings) helps.
- **Screenshot detection is a heuristic.** Cropped, edited or re-encoded screenshots can be missed,
  and a wallpaper saved at exactly your screen resolution can occasionally be flagged.
- **Scene rules favour precision.** When signals are weak a file goes to *Other* instead of a wrong
  folder.
- **Speed depends on the device.** Videos take longer than photos because several frames are
  analysed. Turning off OCR or sampling fewer frames makes analysis faster.
- **Older Android versions.** HEIC/HEIF decoding needs Android 9+ and AVIF Android 12+. When pixels
  can't be decoded, Sortora still sorts by metadata (date, screenshot cues) and marks the result.

### Other limitations

- **Copies get a new "modified" date.** The Storage Access Framework can't set file timestamps. EXIF
  inside photos is preserved byte for byte, and the capture date is in the new file name, but some
  gallery apps may list copies by copy time.
- **Move = copy, verify, delete.** It isn't an instant rename, so it needs free space for the
  largest file and takes as long as a copy. If a location doesn't allow deleting (some providers
  don't), the copy is kept, the original stays, and the result screen says so.
- **Cloud providers in the file picker.** If you pick a file from a cloud provider (e.g. Google Drive)
  in the system picker, *that* app may download it to hand it over. Sortora itself never uses the
  network. Pick local files to stay fully offline.
- **Access after a reboot.** Individually picked files are readable until the phone restarts; picked
  folders keep access permanently. If you reboot mid-session, re-pick the files.
- **Not yet tested on physical devices.** The decision logic has unit tests and the full UI flow
  runs in a Robolectric smoke test, but ML Kit itself only runs on a device, and the thresholds in
  `SceneClassifier` and `ScreenshotHeuristics` should be tuned with real footage.

## Testing

- `app/src/test/.../domain/`: 52 JVM tests covering file naming, date parsing (EXIF, video
  containers, camera/screenshot file names), screenshot scoring, scene and video classification,
  tag selection, OCR summarising, custom-category matching, planning and de-duplication, CSV export.
- `AppSmokeTest`: Robolectric launches the real app (Hilt, Room, DataStore, WorkManager, navigation,
  Compose) and walks onboarding → Home → Settings → History → Preview → edit sheet → Apply guard.

## Tech stack

Kotlin 2.4 · Jetpack Compose + Material 3 · MVVM + Clean Architecture (domain / data / ui) ·
Hilt · Room · DataStore · WorkManager · Navigation (type-safe routes) · Coil 3 (local-only thumbnails) ·
ML Kit (bundled models) · AGP 9.4 · Gradle 9.7 · version catalog (`gradle/libs.versions.toml`).
