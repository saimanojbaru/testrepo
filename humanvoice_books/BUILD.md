# HumanVoice Books — Build Instructions

This repo contains the full Flutter source for **HumanVoice Books**, an
offline-first EPUB → audiobook generator using:

- **Kokoro-82M** (ONNX, Opset 15) for multi-voice TTS
- **Qwen2.5-1.5B-Instruct Q4_K_M** as the on-device "Director" LLM
- `sherpa_onnx` Flutter plugin (ships prebuilt JNI for arm64 / armv7 / x86_64)
- `ffmpeg_kit_flutter_audio` for `.m4b` muxing with chapter markers

The APK binary cannot be produced inside this CI sandbox because the Flutter
SDK is not installed. To build it on a workstation with Flutter 3.22+ and
Android NDK 27.x:

```bash
cd humanvoice_books

# 1. Generate the missing platform scaffolding (icons + iOS stubs we don't ship)
flutter create . --platforms=android --org com.humanvoice --project-name humanvoice_books

# 2. Restore dependencies
flutter pub get

# 3. Release build (universal APK, ~95MB before models, models stream on first launch)
flutter build apk --release --target-platform android-arm64,android-arm,android-x64

# Output:
#   build/app/outputs/flutter-apk/app-release.apk
```

After install, the app will:
1. Request notification + microphone permissions.
2. Download Kokoro + Qwen from HuggingFace into `getApplicationSupportDirectory()`
   (~600 MB total, one-time).
3. Let you pick an EPUB and produce a `.m4b` saved under
   `appSupportDir/audiobooks/<slug>.m4b`.

## Architecture

```
EPUB ─▶ BookAnalyzer (Director Isolate, Qwen2.5)
        ─▶ Chapter[Segment{text, voice_id, emotion, speed}]
        ─▶ TtsService (Enactor, Kokoro-82M)            ◀─ background isolate prefetch
        ─▶ Per-chapter WAV
        ─▶ M4bGenerator (ffmpeg concat + ffmetadata chapters)
        ─▶ <book>.m4b
```

## OOM safeguards

- LLM KV cache reset every ~2K tokens (see `book_analyzer.dart`).
- Chapter rendering decoupled from playback via Isolates.
- ABI splits disabled but `largeHeap=true` and stripped resources keep peak RSS
  manageable on 4GB devices.
