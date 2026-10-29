# Sortora

**Sort your photos, videos and screenshots with on-device AI. 100% offline.**

Sortora is an Android app in the spirit of "Sort my footage": you pick media, it analyses every file
on your phone, suggests clear names and folders, shows you a preview, and only then copies, moves
or renames the files. Every change is logged and can be undone.

```
VID_20251012_143015.mp4      →  Talking Head/2025-10-12_talkinghead_person_desk.mp4
Screenshot_20251012-1430.png →  Screenshots/Screenshot_Settings.png
IMG_4821.jpg                 →  Pets & Animals/2025-10-12_animal_dog_beach.jpg
IMG_4822.jpg (burst copy)    →  skipped as a near-duplicate of IMG_4821.jpg
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
- **Bundled models only.** ML Kit's "bundled" variants and a TensorFlow Lite model ship inside the
  APK. Nothing is downloaded, not even through Google Play services.
- **No storage permission.** All file access goes through the system file picker (Storage Access
  Framework). Sortora can only see the files and folders you pick.
- **No backups.** App data (settings, analysis cache, face data, history) is excluded from cloud
  backup and device-to-device transfer.
- **Short-lived analysis data.** Labels, OCR keywords, image fingerprints, face crops and face
  embeddings live in app-private storage only while a session is open, and are deleted when you
  start the next one. The permanent history keeps only file names and folders.

No cloud mode, no API keys, no accounts.

## Features

| | |
|---|---|
| Media | Photos (JPG, PNG, HEIC/HEIF, WebP, AVIF, GIF…), videos (MP4, MOV, MKV, WebM…), screenshots |
| On-device AI | ML Kit image labeling, object detection, face detection (with landmarks), text recognition; TensorFlow Lite (LiteRT) image embedder |
| Screenshots | File name, folder, EXIF (`UserComment: Screenshot` on iOS), MediaStore path, exact screen resolution, status-bar clock found by OCR, text density |
| Scenes | Screenshot, selfie, group photo, people, document, landscape, city, food, pets, product |
| Videos | Key frames sampled across the clip → talking head / B-roll / screen recording |
| **Duplicates** | Exact (SHA-256 of files that share a size) and near-duplicates (64-bit perceptual hash, confirmed by the TFLite embedding for looser matches). The best copy is kept; extras are skipped, put in a *Duplicates* folder, or left alone (your choice). "Keep this one instead" per group. |
| **Face groups** | Faces are aligned on the eyes, embedded with TFLite and clustered (mutual nearest neighbours). Adjust strictness live, name a group ("Mom"), send its files to *People/Mom*; names flow into file names and the `{people}` token. |
| **Smart filters** | Has face, Screenshot, Contains text, Landscape, Product, Blurry (variance of the Laplacian), Duplicates, Video. Combinable, with counts. |
| **Batch rename** | Patterns with `{date} {time} {year} {month} {day} {category} {label} {labels} {text} {original} {counter} {counter:N} {folder} {people} {camera}`, live preview, applied to everything shown (all files or the filtered ones). `{counter}` numbers files per folder in capture order. |
| **Three modes** | Copy (default), Move (confirm; deletes originals only after a verified copy), Rename in place (confirm; no folders, no output folder needed) |
| **Undo + history** | Every operation is logged with the original name. Undo a whole batch from History: copies are deleted (unless changed since), moved files go back to their original folder under their original name, renames are reversed. CSV export, plus an optional CSV next to the sorted files. |
| Your folders | Custom categories with keywords; related words are matched too ("Pets" catches dog, cat, bird…; "Receipts" catches *subtotal*, *tax*, *total* in OCR text) |
| Preview | Every new name and folder before anything changes; edit, exclude, filter, bulk actions, see what was detected and why |
| Big batches | WorkManager with a foreground notification; progress survives leaving the app and resumes after interruption |
| UI | Jetpack Compose, Material 3, dynamic colour, dark mode, onboarding, empty states |

## Build and run

Requirements:

- Android Studio with support for **AGP 9.4** (any 2026 release), or the command line with JDK 17+ (21 recommended)
- Android SDK Platform **37** (compileSdk). The app targets API 36 and runs on Android 8.0+ (API 26)

```bash
cd Sortora
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-<abi>-debug.apk
./gradlew testDebugUnitTest      # 75 logic tests + a Robolectric smoke test of the whole UI flow
./gradlew lintDebug
./gradlew verifyReleaseNoInternetPermission
```

Open the `Sortora/` folder (not the repository root) in Android Studio.

APKs are split per CPU type because the bundled models include native code
(`app-arm64-v8a-*.apk` is the one almost every current phone needs). Release size is about
52 MB for arm64; most of that is the on-device models. All native libraries are 16 KB
page-aligned, as Google Play requires for apps targeting Android 15+. `./gradlew bundleRelease`
builds an App Bundle, which Google Play splits per device automatically.

**Without a computer:** every push that touches `Sortora/` runs the *Sortora Android* GitHub Actions
workflow, which runs the tests, lint, the privacy check, and uploads the debug APKs as a build
artifact. Download `app-arm64-v8a-debug.apk` from the workflow run page and install it on your phone.

Release builds use R8 (minify + resource shrinking). Configure your own signing config before
publishing.

## How it works

```
Pick files / folder ──► MediaSourceRepository (SAF + MediaStore) ──► Room session
                                                          │
                     AnalyzeMediaWorker (WorkManager, foreground)
                                                          │
       ┌──────────────── OnDeviceMediaAnalyzer ───────────┴───────────────────┐
       │ Images: decode ≤2k px → labels + objects + faces + OCR (parallel)      │
       │         + dHash, blur score, TFLite embedding, aligned face crops      │
       │ Videos: N key frames  → labels + faces per frame, OCR on 2 frames      │
       │         + frame-to-frame change; middle frame for hash and embedding   │
       └──────────────┬────────────────────────────────────────────────────────┘
                      ▼
   ScreenshotHeuristics → SceneClassifier → TagSelector          (pure Kotlin rules)
   FindDuplicatesUseCase → DuplicateDetector (union-find)         FaceClustering (on demand)
                      ▼
   Preview: SortPlanner = CategoryMatcher + FileNameGenerator + counters + de-duplication
                      ▼  (filters, bulk edits, Copy / Move / Rename, confirm)
   ApplyPlanWorker → SafFileOrganizer (copy → verify → optional delete, or rename) → History log
                      ▼
   UndoBatchWorker → delete copies / restore moves / rename back
```

**Talking head vs B-roll.** For each sampled frame Sortora records whether there is a face, how
many, how large and how centred, plus a tiny luminance fingerprint. A clip is a talking head when
one reasonably large, roughly centred face is present in most frames and consecutive frames barely
change (a locked-off camera). Everything else that isn't a screen recording is B-roll, tagged with
its scene labels.

**Face groups.** ML Kit finds faces and eye positions; each face is rotated level, scaled and
cropped the same way, then turned into a 1024-number vector by the TFLite model. Vectors are
centred on their mean (so the shared "it's a face" part drops out), and two faces are linked only if
each is among the other's nearest neighbours and they clear the strictness threshold.

### Project structure

```
app/src/main/java/com/sortora/app/
├── domain/                 Pure Kotlin, no Android imports, unit-tested
│   ├── model/              MediaFile, AnalysisResult, ContentCategory, settings, sessions, log
│   ├── classify/           SceneClassifier, ScreenshotHeuristics, TagSelector, TextAnalysis,
│   │                       ImageMetrics (dHash, blur), FrameSignature, LabelVocabulary
│   ├── duplicates/         DuplicateDetector
│   ├── faces/              FaceClustering
│   ├── filters/            SmartFilter
│   ├── naming/             FileNameGenerator (tokens), FileNameSanitizer, DateParsing
│   ├── planning/           SortPlanner, CategoryMatcher
│   ├── history/            LogCsv
│   ├── repository/         Interfaces: settings, sessions, faces, history, media, analyzer, organizer, jobs
│   └── usecase/            Start session, run analysis, find/keep duplicates, apply, undo, set destination
├── data/
│   ├── ai/                 MlKitVision, ImageEmbedder (LiteRT), FaceCrops, OnDeviceMediaAnalyzer,
│   │                       BitmapLoader, VideoFrameExtractor, DisplaySizes
│   ├── media/              SafMediaSourceRepository, MetadataReader (EXIF / dates)
│   ├── file/               SafFileOrganizer (copy/move/rename + undo), SafContentHasher, SafTextFileWriter
│   ├── local/              Room database (v2), entities, DAOs, EmbeddingCodec
│   ├── repository/         Room + DataStore implementations, face thumbnail store
│   └── work/               Analyze / Apply / Undo workers, notifications, scheduler
├── di/                     Hilt modules
└── ui/                     Compose screens + ViewModels (MVVM)
    ├── onboarding/ home/ analysis/ preview/ faces/ apply/ history/ settings/
    ├── components/ theme/ navigation/
```

### Extending

- **Teach it new concepts:** edit `LabelVocabulary` (`categoryLabels`, `concepts`, `tagAliases`). It is
  plain data.
- **Better face grouping:** swap `assets/models/image_embedder.tflite` for a dedicated face-embedding
  model you have the rights to ship (e.g. a MobileFaceNet-style model). `ImageEmbedder` reads the
  input size and output length from the model; adjust the pixel normalisation in `embed()` to match
  the model, and re-tune `FaceClustering.DEFAULT_THRESHOLD`.
- **Add a scene model** (e.g. Places365): run it in `OnDeviceMediaAnalyzer` and append its outputs as
  `DetectedLabel`s. The classifier, tag selector and matcher all work on plain labels.
- **New name tokens:** add a case in `FileNameGenerator.renderToken` and list it in `TOKENS`.
- **New smart filter:** add an entry to `SmartFilter` and a label/icon in `ui/components/CategoryUi.kt`.

## Known limitations of on-device AI

- **General labels only.** ML Kit's on-device labeler knows about 450 everyday concepts. It does not
  recognise brands, specific landmarks or fine-grained species. Labels can be wrong; the preview
  shows confidence and flags low-confidence results.
- **Face groups are look-alike groups, not face recognition.** The bundled embedder is a
  general-purpose image model (no license-safe face-recognition model ships with Android). It
  groups faces that *look* similar in the crop: two siblings can merge, and the same person in very
  different light, angle or age can split. Use the strictness slider and name groups yourself.
  Faces under ~40 px, and faces in screenshots, are skipped.
- **Duplicate detection is tuned for safety.** Exact duplicates are certain (byte-identical).
  "Similar" catches re-saves, resizes and most burst shots, but heavy crops or edits are missed,
  and two genuinely different shots of the same static scene can be flagged. Nothing is ever
  deleted because of it: extras are only skipped or set aside, and you can change the keeper.
- **Blur detection is a heuristic.** It measures edge sharpness, so intentionally soft photos
  (bokeh portraits, fog, night shots) can be flagged, and motion blur on small parts of a sharp
  photo may not be.
- **OCR is Latin-script only.** Chinese, Japanese, Korean and Devanagari need their own bundled ML
  Kit models (easy to add, each adds APK size).
- **Talking head / B-roll is a heuristic.** It is visual only (no audio or speech analysis) and
  looks at a handful of frames. Expect mistakes on walk-and-talk vlogs, interviews with cutaways,
  screen shares with a face cam, and static shots of a person. More sampled frames (Settings) helps.
- **Screenshot detection is a heuristic.** Cropped, edited or re-encoded screenshots can be missed,
  and a wallpaper saved at exactly your screen resolution can occasionally be flagged.
- **Thresholds are untuned on real footage.** The values in `SceneClassifier`, `ScreenshotHeuristics`,
  `DuplicateDetector`, `ImageMetrics.BLUR_THRESHOLD` and `FaceClustering` come from reasoning and
  synthetic tests, not a labelled dataset. Expect to tune them after trying real libraries.
- **Speed depends on the device.** Face grouping and duplicate checks add a TFLite pass per image
  (and per face). Turning them off, turning off OCR, or sampling fewer video frames is faster.
- **Older Android versions.** HEIC/HEIF decoding needs Android 9+ and AVIF Android 12+. When pixels
  can't be decoded, Sortora still sorts by metadata (date, screenshot cues) and marks the result.

### Other limitations

- **Undo has limits.** Copies you edited after sorting are kept (their size changed). Moved files
  that were picked one by one (not via a folder) can't go back to their exact folder, because
  Sortora never had access to it; Undo asks you to pick a folder instead. Folders Sortora created
  are left in place, empty. Undo needs the output folder to still be accessible.
- **Rename in place needs a provider that allows it.** Files picked through a folder can always be
  renamed; some single-file sources (e.g. certain gallery providers) refuse, and the result screen
  lists those.
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
- **Face groups are per session.** Names you give faces are not remembered across sessions (by
  design: face data is deleted with the session).
- **Not yet tested on physical devices.** The decision logic has unit tests and the full UI flow
  runs in a Robolectric smoke test, but ML Kit and TFLite only run on a device.

## Testing

- `app/src/test/.../domain/`: 75 JVM tests covering file naming and pattern tokens, counters,
  date parsing, screenshot scoring, scene and video classification, tag selection, OCR summarising,
  custom-category matching, planning and de-duplication, rename-in-place planning, perceptual
  hashing, blur scoring, duplicate grouping and keeper choice, face clustering, smart filters, the
  undo use case (copy / move / rename, unknown folders, already-undone entries) and CSV export.
- `AppSmokeTest`: Robolectric launches the real app (Hilt, Room, DataStore, WorkManager, navigation,
  Compose) and walks onboarding → Home → Settings → History → Preview → edit sheet → Apply guard →
  smart filter → batch rename with a pattern → Rename-mode confirmation → Undo from History (run by
  the real worker).

## Tech stack

Kotlin 2.4 · Jetpack Compose + Material 3 · MVVM + Clean Architecture (domain / data / ui) ·
Hilt · Room · DataStore · WorkManager · Navigation (type-safe routes) · Coil 3 (local-only thumbnails) ·
ML Kit (bundled models) · TensorFlow Lite / LiteRT 1.4 · AGP 9.4 · Gradle 9.7 · version catalog
(`gradle/libs.versions.toml`).

## Third-party models

`app/src/main/assets/models/image_embedder.tflite` is MediaPipe's MobileNet-V3 (small) image
embedder, © Google, licensed under the Apache License 2.0 (see `assets/models/NOTICE`). ML Kit
models are distributed under the ML Kit terms of service.
