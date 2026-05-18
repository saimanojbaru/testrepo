# Corporate Dragon: Middle-Class Odyssey

A narrative life-sim + idle RPG prototype in **Unity 6.4** (`6000.4.7f1`).
King's Choice-inspired loop wrapped around a middle-class Indian life story —
Energy tick, family retainers, real-date Indian festival calendar, dynamic
flag-based dialogue branching, and a 12-card memory album. Chapter 1 (12
episodes) is fully scripted; chapters 2–6 are designed but not yet written.

## Open in Unity

1. Launch **Unity Hub** → **Add project from disk** → select this repo's root folder.
2. Hub should detect Editor `6000.4.7f1`; click to open. First import takes
   a few minutes while Library/ is populated.
3. When the Editor opens, the Console may show "no scene loaded". That's fine —
   open `Assets/Scenes/Main.unity` if it exists, or just create an empty scene
   (`File → New Scene → Basic 2D` → save as `Assets/Scenes/Main.unity`).
   `Bootstrap.cs` self-installs the GameManager via `RuntimeInitializeOnLoadMethod`,
   so any scene works.

## Build APK

From the Unity Editor menu:

1. **Tools → Corporate Dragon → Configure Android Build**
   Sets package id (`com.corporatedragon.odyssey`), version, IL2CPP/ARM64,
   min SDK 26, portrait orientation, and switches active target to Android.
2. **Tools → Corporate Dragon → Build APK**
   Choose an output path and Unity builds the APK (5-15 min first time).

Prerequisites in Unity Hub → Installs → ⚙️ Modules: **Android Build Support**,
**Android SDK & NDK Tools**, **OpenJDK**.

## What's Implemented

| System | File | Notes |
|---|---|---|
| Bootstrap | `Assets/Scripts/Core/Bootstrap.cs` | Auto-creates GameManager pre-scene-load |
| Game orchestration | `Assets/Scripts/Core/GameManager.cs` | Owns all managers, save/load |
| Stats | `Assets/Scripts/Core/StatManager.cs` | 8 core stats, clamped 0-100 |
| Energy | `Assets/Scripts/Core/EnergyManager.cs` | 20/hr regen, retainer/festival multipliers |
| Retainers | `Assets/Scripts/Core/RetainerSystem.cs` | Maa/Papa/Nani affinity 0-10 |
| Choices & flags | `Assets/Scripts/Core/ChoiceTracker.cs` | Persistent flag set |
| Adaptive dialogue | `Assets/Scripts/Core/AdaptiveEventSystem.cs` | Filters lines by `ifFlag`/`ifNotFlag` |
| Festivals | `Assets/Scripts/Core/FestivalManager.cs` | Real-date "next occurrence" with per-year overrides |
| Memory album | `Assets/Scripts/Core/MemoryAlbumManager.cs` | 12 cards, unlock on choice |
| Save/Load | `Assets/Scripts/Core/SaveManager.cs` | JSON in `Application.persistentDataPath` |
| Character animation | `Assets/Scripts/Core/KingsChoiceAnimator.cs` | Transform-based breathing + mood lean (Spine-ready) |
| UI | `Assets/Scripts/UI/*.cs` | All Canvases built programmatically in uGUI |
| Data | `Assets/Resources/Data/*.json` | Loaded at startup via `Resources.Load<TextAsset>` |

## What's NOT in this build

- **No Spine/Live2D rigs.** `KingsChoiceAnimator` does transform-based motion so
  the architecture is ready, but no skeletal art is included. Buy a Spine license,
  import the runtime, swap the animator implementation.
- **No art assets.** UI is built from coloured rectangles and the system font.
  Emoji glyphs were replaced with single-letter placeholders since Unity's
  bundled `LegacyRuntime.ttf` has no emoji support — import a TMP sprite asset
  or NotoColorEmoji + switch labels to TextMeshProUGUI to restore them.
- **No audio.** No music, SFX, or voice-over assets included.
- **Chapters 2–6.** Designed in `Assets/Resources/Data/episodes.json` schema —
  add more entries to extend.
- **No campaign battles, mini-games, journal, in-app updater, or push notifications.**

## Adding a new episode

Append an entry to `Assets/Resources/Data/episodes.json`:

```json
{
  "id": "ch2_e1", "chapter": 2, "episode": 1, "age": 16,
  "title": "Leaving Hometown", "bg": "evening", "charEmoji": "T", "mood": "",
  "lines": [
    { "speaker": "Narrator", "text": "Train horn. Steam. Maa at the window.",
      "ifFlag": "", "ifNotFlag": "" },
    { "speaker": "Maa", "text": "Beta, time pe khaana khaana.",
      "ifFlag": "HighFamilyWarmth", "ifNotFlag": "" }
  ],
  "choices": [
    { "text": "Promise to call every day.",
      "effects": [ {"key":"familyBond","delta":10} ],
      "flags": ["DutifulSon"], "memory": "" }
  ],
  "endsChapter": false
}
```

Lines support `ifFlag` (only show if flag is set) and `ifNotFlag` (only show if
flag is NOT set). Both empty means always show.

## Adding a Spine character

1. Buy Spine 2D, import `spine-unity` package.
2. Drag your `.json` + `.atlas` + textures into `Assets/Spine/`.
3. Add `SkeletonAnimation` to the `Char` GameObject inside the EpisodeScreen
   stage (`EpisodeScreen.Build` creates it).
4. Replace `KingsChoiceAnimator` with your own component that drives bones via
   `Skeleton.FindBone()` and animations via `AnimationState.SetAnimation()`.
   Mood mapping is already wired: see `EpisodeScreen.MoodFor`.

## File layout

```
Assets/
  Editor/                  (none — see Scripts/Editor)
  Resources/Data/          *.json data
  Scenes/                  Main.unity (create on first open)
  Scripts/
    Core/                  managers + bootstrap
    Data/                  POCO data models
    Editor/                ProjectInit menu items
    UI/                    uGUI screens
Packages/manifest.json
ProjectSettings/ProjectVersion.txt
```

## Save location

- **Editor (Linux)**: `~/.config/unity3d/Corporate Dragon Studio/Corporate Dragon/save_v1.json`
- **Editor (Windows)**: `%LOCALAPPDATA%\Corporate Dragon Studio\Corporate Dragon\save_v1.json`
- **Android device**: `/sdcard/Android/data/com.corporatedragon.odyssey/files/save_v1.json`

Delete that file to fully wipe state.
