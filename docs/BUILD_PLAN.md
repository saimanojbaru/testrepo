# Corporate Dragon: Middle-Class Odyssey — Chapter 1 Android APK

## Context

We are building Chapter 1 of *Corporate Dragon: Middle-Class Odyssey* — a Ghibli-styled narrative life-sim modeled on King's Choice (dynamic stat snowball, retainers, campaigns, dynamic-branching choices) — from a fresh git repo (`claude/corporate-dragon-game-LMg9H` on `saimanojbaru/testrepo`). The repo currently has only Node + Python AI scaffolding and no game code.

Per the extended design conversation, the deliverable is:
- All 12 Chapter 1 episodes (Ages 5–15), each with multiple choices that seed long-term traits (`GrinderPath`, `DreamerPath`, `HighFamilyWarmth`, `PlayfulRebel`, etc.) and adapt later episodes
- Core systems: stats, energy, retainers, festival calendar (real-date driven), memory album, journal, choice-tracker, save/load, in-app update checker
- Built as an **Android APK** from a **headless Linux container** using **Unity 2022.3 LTS** (user explicitly chose Unity, no compromise)
- User will provide a `.ulf` license file via the manual-activation flow when prompted

The intended outcome is a sideloadable APK demonstrating the full Chapter 1 vertical with all systems wired, plus an update-check that prompts users to download future versions from GitHub Releases.

## Approach

Unity Editor for Linux runs fine headless once a `.ulf` is in place. The pipeline is: install editor + Android module + JDK + Android SDK/NDK → activate license (manual ULF round-trip with user) → scaffold project with Ink/TextMeshPro/Newtonsoft/Addressables/2D-sprite packages → implement 14 C# scripts + 12 `.ink` episode files + generated placeholder art → invoke `Unity -batchmode -executeMethod BuildScript.PerformBuild` → publish APK to GitHub Release → commit source. The whole pipeline runs in ~4–9 hours wall-clock; the variability is mostly license round-trip latency.

## Critical Risks (acknowledge upfront)

1. **License round-trip latency** — `.alf` generation is automatic; the user must upload to `license.unity3d.com/manual` and paste back the `.ulf`. Build pauses until then.
2. **Unity download hash drift** — the `UNITY_HASH` for `2022.3.50f1` may have changed; if 404, scrape `https://unity.com/releases/editor/whats-new/2022.3.50` for the current `unityhub://` URL and extract the hash.
3. **NDK version pin** — must be `23.1.7779620` exactly; mismatched NDK breaks IL2CPP. Pin in `sdkmanager` and in Unity Player Settings.
4. **APK >100 MB GitHub limit** — never commit the APK; publish to a Release.
5. **Spine Editor is commercial ($300)** — we cannot author Spine animations. Placeholder art is generated procedurally with naming conventions (`char_<name>_<emotion>.png`) so a real artist can swap 1:1 later.
6. **Disk** — Unity + Android SDK + Library cache ≈ 15 GB. Check `df -h /` first.

## Execution Steps

### 1. Host prep (≈5 min)
Install: `openjdk-17-jdk-headless`, `git-lfs`, `libgl1`, `libglu1-mesa`, `libgtk-3-0`, `libnss3`, `libxtst6`, `libxss1`, `libasound2t64`, `libfuse2`, `unzip`, `xz-utils`, `jq`, `python3-pil` (for art generation).
Export `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`, `ANDROID_NDK_ROOT=$ANDROID_HOME/ndk/23.1.7779620`, `UNITY_PATH=/opt/Unity/2022.3.50f1`.

### 2. Unity 2022.3 LTS install (≈20 min, ~5 GB)
Download editor tarball + Android Build Support module tarball directly from `download.unity3d.com/download_unity/<hash>/...` (skip Unity Hub — needs FUSE/display). Extract to `/opt/Unity/2022.3.50f1`. Symlink `unity` to `/usr/local/bin/unity`.

### 3. Android SDK + NDK install (≈10 min, ~3 GB)
`commandlinetools-linux-11076708_latest.zip` → `$ANDROID_HOME/cmdline-tools/latest`. Then `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;23.1.7779620"`. Auto-accept licenses via `yes | sdkmanager --licenses`.

### 4. Unity license activation (USER INTERACTION REQUIRED — ≈5–60 min)
```
unity -batchmode -nographics -createManualActivationFile -logFile /dev/stdout
```
→ produces `Unity_v2022.x.alf` in CWD. Surface this file to the user via `SendUserFile`. User uploads to `license.unity3d.com/manual`, downloads the `.ulf`, pastes contents back (or attaches file). Drop `.ulf` at `~/.local/share/unity3d/Unity/Unity_lic.ulf` and run:
```
unity -batchmode -nographics -manualLicenseFile <path>.ulf -quit -logFile /dev/stdout
```

### 5. Project scaffold (≈10 min)
```
unity -batchmode -nographics -quit -createProject /home/user/testrepo/CorporateDragon -logFile /dev/stdout
```
Edit `Packages/manifest.json` to add:
- `com.unity.textmeshpro`
- `com.unity.addressables`
- `com.unity.2d.sprite`, `com.unity.2d.animation`
- `com.unity.nuget.newtonsoft-json` 3.2.1
- Ink: `"com.inkle.ink-unity-integration": "https://github.com/inkle/ink-unity-integration.git#upm"`

Folder layout under `Assets/_Project/`: `Scripts/{Core,Systems,Dialogue,UI,Build(Editor),Net}/`, `Ink/Chapter01/`, `Art/Sprites/Placeholder/`, `Scenes/`, `Resources/`.

### 6. C# scripts (14 files, dependency-ordered)

| # | Script | Purpose |
|---|---|---|
| 1 | `EventBus.cs` | Static pub/sub backbone — every system talks through this |
| 2 | `SaveManager.cs` | JSON serialize/deserialize to `Application.persistentDataPath/save.json` |
| 3 | `StatManager.cs` | Discipline, Creativity, FinancialWisdom, FamilyBond, MentalHealth, Politics + hidden traits (PeoplePleaser, DreamerRebel, GuiltMemory); `ApplyChoice(id)` switch |
| 4 | `ChoiceTracker.cs` | `HashSet<string> flags`; `SetFlag/HasFlag`; bound as Ink external function |
| 5 | `EnergyManager.cs` | Energy regen formula (20/hr × multipliers); spend on Grind/Family/Skill/Rest |
| 6 | `RetainerSystem.cs` | Maa/Papa/Nani retainers with Affinity 0–10, passive bonuses, special-event triggers |
| 7 | `FestivalManager.cs` | `DateTime.Now` → upcoming festivals (next 18 mo), push flags on boot, Energy/affinity multipliers |
| 8 | `MemoryAlbumManager.cs` | Capture `AlbumEntry` per episode end; combo detection unlocks permanent perks |
| 9 | `AdaptiveEventSystem.cs` | Listens to flag changes; rewrites Ink knot targets via tag overrides |
| 10 | `DialogueManager.cs` | Wraps `Ink.Runtime.Story`; binds external functions to Stat/Choice/Festival/Album managers; drives `ChoiceUI` |
| 11 | `ChoiceUI.cs` / `HUD.cs` | TMP-based dialogue + choice buttons; HUD shows Energy/Stats/Date |
| 12 | `UpdateChecker.cs` | `UnityWebRequest.Get` for `version.json` on Bootstrap; modal if newer version; opens `Application.OpenURL(apkUrl)` |
| 13 | `GameBootstrap.cs` | Instantiates all managers as `DontDestroyOnLoad` singletons; loads MainMenu scene |
| 14 | `BuildScript.cs` (under `Editor/`) | `PerformBuild()` — sets PlayerSettings, IL2CPP, ARM64, target SDK 34, min 24, debug keystore, calls `BuildPipeline.BuildPlayer` |

`StatManager` + `ChoiceTracker` are the source-of-truth; all others subscribe via `EventBus`. `SaveManager` serializes all five (Stat/Choice/Energy/Retainer/Album).

### 7. Chapter 1 content — 12 Ink episodes

Author 12 `.ink` files in `Assets/_Project/Ink/Chapter01/`, each ~200 lines, encoding the exact dialogue + choices from the prior conversation:
1. `ep01_morning_tiffin.ink` — sets `HighFamilyWarmth` or `PlayfulRebel`
2. `ep02_nani_summer.ink` — adapts based on Ep1 flags; +Family/Creativity/Curiosity
3. `ep03_rainy_homecoming.ink` — adapts; Maa hug warmth varies
4. `ep04_papa_test_pressure.ink` — **major branch**: `GrinderPath` vs `DreamerPath` vs `Balanced`
5. `ep05_power_cut_night.ink` — emotional peak; choices adapt to Ep4 path
6. `ep06_festival_shopping.ink`
7. `ep07_annual_day.ink`
8. `ep08_bicycle.ink`
9. `ep09_helping_parents.ink`
10. `ep10_grandparents_stories.ink`
11. `ep11_final_exam.ink` — Grinder/Dreamer path determines available mini-game
12. `ep12_result_hug.ink` — climax; family reaction dynamic on `HighFamilyWarmth + GrinderPath/DreamerPath`

Shared `_globals.ink` (variables) + `_functions.ink` (external bindings to `SetFlag/HasFlag/AdjustStat`). At least 25 distinct flags across episodes for the adaptive system to be meaningful. Compiled to JSON automatically by Ink Unity Integration on import.

### 8. Placeholder art (≈10 min, scripted Python)
Use `PIL` to generate:
- 16 × 256×256 PNG character sprites (`char_maa_smile.png`, `char_papa_serious.png`, `char_protag_child_happy.png`, etc.) — solid colors + emoji-style face overlays
- 12 × 1080×1920 gradient background PNGs (one per episode)
- 1 × app icon + splash
Drop into `Assets/_Project/Art/Sprites/Placeholder/`; Unity import generates `.meta` on first build.

### 9. Build pipeline
`BuildScript.PerformBuild()` sets:
- `applicationIdentifier = "com.corpdragon.mco"`
- `bundleVersion = "1.0.0"`, `bundleVersionCode = 1`
- `targetSdkVersion = 34`, `minSdkVersion = 24`
- `ScriptingImplementation.IL2CPP`, `AndroidArchitecture.ARM64`
- `useCustomKeystore = false` (Unity auto-signs with debug keystore — fine for sideload)
- Inline `EditorPrefs.SetString("AndroidSdkRoot", ...)`, `AndroidNdkRoot`, `JdkPath`

Build:
```
unity -batchmode -nographics -quit -silent-crashes \
  -projectPath /home/user/testrepo/CorporateDragon \
  -buildTarget Android \
  -executeMethod BuildScript.PerformBuild \
  -logFile /home/user/testrepo/build.log
```
Output: `/home/user/testrepo/CorporateDragon/Builds/Android/CorporateDragon.apk` (~60–120 MB).

### 10. Update-check + delivery
- Commit `version.json` at repo root: `{"version":"1.0.0","apkUrl":"https://github.com/saimanojbaru/testrepo/releases/download/v1.0.0/CorporateDragon.apk","notes":"Chapter 1 release"}`.
- `UpdateChecker.cs` fetches `https://raw.githubusercontent.com/saimanojbaru/testrepo/main/version.json` on boot; modal if `Application.version < remote.version`.
- Use `mcp__github__create_branch` / GitHub MCP to create a release **only if user explicitly approves** (PR creation is gated per the instructions; releases similarly should be confirmed).
- Commit Unity-standard `.gitignore` (excludes `Library/`, `Temp/`, `Logs/`, `Builds/`, `*.apk`).
- Push branch via `git push -u origin claude/corporate-dragon-game-LMg9H` with the retry-backoff convention.
- `SendUserFile` the APK directly to the user.

## Critical Files To Be Created

- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Core/EventBus.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Core/StatManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Core/SaveManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Core/GameBootstrap.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/ChoiceTracker.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/EnergyManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/RetainerSystem.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/FestivalManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/MemoryAlbumManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Systems/AdaptiveEventSystem.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Dialogue/DialogueManager.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/UI/ChoiceUI.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/UI/HUD.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/UI/UpdatePrompt.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Net/UpdateChecker.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Scripts/Build/Editor/BuildScript.cs`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Ink/Chapter01/ep01_morning_tiffin.ink` … `ep12_result_hug.ink`
- `/home/user/testrepo/CorporateDragon/Assets/_Project/Ink/Chapter01/_globals.ink`, `_functions.ink`
- `/home/user/testrepo/CorporateDragon/Packages/manifest.json`
- `/home/user/testrepo/version.json` (repo root, for update-check)
- `/home/user/testrepo/.gitignore` (Unity template)

## Verification

1. **License activated**: `unity -batchmode -nographics -quit -logFile /dev/stdout` exits 0 with no "No valid Unity license found" in log.
2. **Project compiles**: `unity -batchmode -nographics -quit -projectPath CorporateDragon -executeMethod UnityEditor.EditorApplication.Exit -logFile /dev/stdout` shows zero compile errors.
3. **Ink compiles**: `Library/InkCache/` contains 12 compiled `.json` files matching the `.ink` sources.
4. **APK built**: `ls -lh CorporateDragon/Builds/Android/CorporateDragon.apk` shows a file 60–120 MB.
5. **APK installable**: `aapt dump badging` on the APK shows correct package name `com.corpdragon.mco` and version `1.0.0`. User sideloads via `adb install` or file manager.
6. **Update check works**: bump `version.json` to `1.0.1` on `main`, reopen app — modal appears with link.
7. **Branch pushed**: `git log origin/claude/corporate-dragon-game-LMg9H` shows the latest commit with all source.

## Time Estimate

| Phase | Time |
|---|---|
| Host prep + Unity download + Android SDK | ≈45 min |
| License activation (user round-trip) | 5 min – 1 hr |
| Project scaffold + packages | ≈20 min |
| 14 C# scripts | ≈90 min |
| 12 Ink episodes | ≈90 min |
| Placeholder art generation | ≈15 min |
| First batchmode build + iterate | ≈45 min |
| Release publish + push + APK delivery | ≈15 min |
| **Realistic total** | **≈4–6 hr** (worst case 9 hr if license stalls) |
