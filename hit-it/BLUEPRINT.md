# Hit it — Blueprint

**Hit it** is an offline-first, gamified habit & goal tracker for Android (Kotlin + Jetpack
Compose). It is inspired by the *Loggd.life* concept (habits, a year heatmap, streaks, focus
timer, journaling, goals, and an XP/level/tier game layer) but is its own product with its own
branding and feature names. Everything lives on-device — **no account, no backend, no ads**.

This document is the single source of truth for the architecture, data model, algorithms, and the
phased roadmap. Each future phase has a copy-paste prompt you can hand to Claude.

---

## 0. What is built right now (Phases 1–9)

✅ **Built and shipping in this repo:**
- Two-module Gradle project: `:domain` (pure Kotlin) + `:app` (Android).
- **Reps** (habits) with flexible schedules: Daily / Weekdays / Custom days / Weekly target, plus
  multi-hit-per-day targets.
- **Streak engine** with rest-day skip protection, rest mode (vacation), and week-based streaks for
  weekly targets — **fully unit-tested** (100 tests in `:domain`).
- **Life Flame** — a single animated flame (5 states Dying→Inferno) driven by today's Momentum Score
  + best streak (pure `domain/.../flame/LifeFlame.kt`), shown on the Dashboard hero and Profile;
  count-up Momentum number + animated level bar + column-by-column Grid reveal; a daily WorkManager
  "flame fading" nudge.
- **Interactive onboarding** — first launch drops onto the real seeded Dashboard with a Compose
  **spotlight overlay** (`ui/components/SpotlightOverlay.kt`: scrim + BlendMode.Clear hole + caption,
  always Skippable) instead of a static pager; `POST_NOTIFICATIONS` is requested after the first
  logged hit (gated by `AppPreferences.notifPermissionAsked`).
- **Identity Cards** — pure `domain/.../identity/IdentityCatalog.kt`: named identities (Consistent
  Beast, Deep Thinker, The Closer, Reflective, Unbreakable) unlocked by sustained consistency, each
  granting a permanent **+X% Momentum on every rep** (additive, capped +30%) via
  `MomentumCalculator.hitAward`. Persistent `identities` table (DB **v9**), `IdentityRepository.sync`,
  shown in Profile. `clearHit` now reverses the **exact** awarded amount from the ledger
  (`MomentumTxnDao.sumAwardsForRepSince`) so undo never drifts.
- **Energy Pass (visual + mechanics)**: layered dark surfaces + cyan/violet/magenta hero gradient,
  bold athletic typography, a **Dashboard** home screen (0–100 **Momentum Score**, quick-stats row,
  Main Target, reps, recent-momentum mini-grid), **Perfect Day** 1.5× multiplier with confetti +
  haptics on the rep that completes the day, and **first-launch demo seed** (3 Reps + ~30 days of
  history so The Grid glows; clearable via Profile → "Clear sample data").
- **The Grid** — GitHub-style year heatmap drawn on a Compose `Canvas`; intensity now combines Reps
  hit and Check-Ins per day.
- **Momentum** gamification: earn XP per hit/task/focus minute/check-in/checkpoint/goal, 100 Levels
  on a smooth curve, 12-tier ladder (Rookie → G.O.A.T.), and persistent Trophies (see Phase 6).
- **Hits** (tasks) — *Phase 2*: priorities (High/Med/Low), quick due dates, optional link to a Rep,
  and a single daily **Main Target** surfaced on Today. Completing a Hit awards Momentum by priority
  (and reverses it on undo), all in one transaction.
- **Lock In** (focus timer) — *Phase 3*: presets 15/25/45/60/90 min, selectable **Zones**, optional
  link to a Rep, a real **foreground Service** so the countdown survives navigation/backgrounding,
  pause/resume/stop, and Momentum awarded per focused minute on finish.
- **Check-In** (journal) — *Phase 4*: a daily entry with morning/evening reflections and mood +
  energy (1–10) sliders, surfaced as a card on Today; awards Momentum the first time the morning and
  evening entries get content, and counts toward The Grid.
- **Big Plays** (goals) — *Phase 5*: long-term goals with category + horizon (Quarter/Year/2-Year/
  3-Year), numeric target/current progress, and **Checkpoints**; completing a checkpoint or the whole
  goal awards Momentum. Reached from Profile → Library.
- **The Locker** (notes) — *Phase 5*: a hierarchical note tree (a note with children acts as a
  folder), with title/body editing and cascade delete. Reached from Profile → Library.
- **Trophies + Stats** — *Phase 6*: a persistent `trophies` table with a pure-domain unlock catalog
  (12 Trophies across level/focus/check-in/task/goal/streak milestones); earning a Trophy awards a
  one-time Momentum bonus. Profile shows a **Stats** section (focus minutes, best streak, hits done,
  check-ins) and the Trophy grid reflects real unlock state.
- **Reminders** — *Phase 7*: per-Rep daily reminders via **WorkManager + Hilt**. Each reminder is a
  one-shot `ReminderWorker` delayed to the next HH:mm that re-schedules itself for the next day;
  reminders are skipped during rest mode, cancelled on archive/delete, and re-armed on app start
  (survives reboot/process death). Set the time in Add/Edit Rep.
- **Polish & widget** — *Phase 8*: a first-run **onboarding** pager (gated by a SharedPreferences
  flag), **month labels** on The Grid (aligned, shared-scroll axis), and a **Glance home-screen
  widget** showing today's "done / total reps" that deep-links into the app.
- **Tests** — *Phase 9*: 70 pure-domain JUnit tests (streak edge cases, level-curve invariants, all
  engines) runnable anywhere via `./gradlew :domain:test -PskipApp`, plus **instrumented Room DAO
  tests** (`src/androidTest`) covering the unique-index upsert, FK cascade deletes, the
  BigPlay↔Checkpoint `@Relation`, and reminder/rest-mode queries (run on a device/emulator via
  `./gradlew :app:connectedDebugAndroidTest`).
- Screens: **Onboarding**, **Today**, **Reps**, **Rep detail**, **Add/Edit Rep**, **Hits**,
  **Add/Edit Hit**, **Lock In**, **Check-In**, **Big Plays** (+ detail/edit), **The Locker**,
  **The Grid**, **Profile**, dark + neon Material 3, bottom navigation.
- Room persistence (offline-first, DB v7) wired through Hilt.

🔜 **Not built yet (see roadmap):** Play Store prep (P10), cloud sync.
Linking Reps/Hits to a Big Play with automatic rollup, and Lock In ambient sounds (audio assets),
are also deferred.

> The `:domain` logic is verified by running `./gradlew :domain:test -PskipApp`. The `:app` module
> needs the Android SDK (Android Studio) to build — it was authored against a verified, mutually
> compatible version matrix to make the first Gradle sync clean.

---

## 1. Principles

1. **Offline-first.** Room (SQLite) is the single source of truth. The UI observes the DB; user
   actions write to it. No network is required for any feature.
2. **Gamified, not gimmicky.** Every action earns Momentum; progress is always visible (streaks,
   levels, the year grid).
3. **Privacy by default.** All data stays on the device. The privacy policy is therefore trivial.
4. **Deterministic core.** All hard logic (streaks, levels, grid) is pure Kotlin in `:domain`, takes
   `today` as a parameter, and is unit-tested.

---

## 2. Naming glossary (action / athletic vibe)

| Concept | Name in "Hit it" | Notes |
|---|---|---|
| Habit | **Rep** | a thing you repeat |
| Completing a habit today | **Hit** | "hit it" / "log a hit" |
| Consecutive success run | **Streak** | shown as 🔥 N |
| Skip protection | **Rest days** | per-gap budget (default 2) |
| Vacation mode | **Rest mode** | a date range that pauses a rep |
| Year contribution grid | **The Grid** (a **Heatmap**) | the signature screen |
| XP | **Momentum** ⚡ | earned per hit |
| Levels / 12 tiers | **Levels 1–100** / **Tier ladder** | Rookie, Amateur, Prospect, Contender, Starter, Pro, All-Star, Veteran, Elite, Champion, Legend, G.O.A.T. |
| Badges | **Trophies** | unlocked by milestones |
| Tasks *(future)* | **Hits** · main focus → **Main Target** | |
| Focus timer *(future)* | **Lock In** · themes → **Zones** | |
| Journal *(future)* | **Check-In** (morning/evening) | |
| Goals/milestones *(future)* | **Big Plays** / **Checkpoints** | |
| Notes *(future)* | **The Locker** | |

App id: `com.hitit.app`. Display name: **Hit it**.

---

## 3. Tech stack & versions

Verified mutually-compatible stable versions (2026-05). The catalog is `gradle/libs.versions.toml`.

| Component | Version | Notes |
|---|---|---|
| Android Gradle Plugin | 8.13.0 | stay on 8.x; AGP 9 needs Gradle 9.4+ |
| Gradle wrapper | 8.14.3 | pinned in `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.3.21 | |
| Compose compiler plugin | 2.3.21 | must equal Kotlin version |
| KSP | 2.3.9 | tracks Kotlin; all processors use `ksp(...)` |
| Room | 2.8.4 | **not** Room 3.0 (KMP rewrite) |
| Hilt | 2.57.1 | androidx hilt 1.3.0 |
| Compose BOM | 2026.05.00 | → ui 1.11.2, material3 1.4.0 |
| lifecycle | 2.10.0 | runtime-compose + viewmodel-compose |
| navigation-compose | 2.9.8 | |
| coroutines | 1.11.0 | |
| Glance | 1.1.1 | home-screen widget; Compose BOM overrides its older transitive runtime |
| compileSdk / targetSdk | 36 | |
| minSdk | 26 | `java.time` works natively, no desugaring |
| Java/Kotlin target | 17 | |

**Rules that matter:** the Compose compiler plugin is mandatory on Kotlin 2.x and its version must
equal Kotlin's; keep KSP bumped with Kotlin; do not let Android Studio upgrade AGP to 9.x unless you
also move the wrapper to Gradle 9.4+.

---

## 4. Architecture

Clean Architecture + MVVM, unidirectional data flow.

```
:domain (pure Kotlin/JVM, no Android)         :app (Android)
─────────────────────────────────────        ──────────────────────────────────────
model/      RepCore, ScheduleType,            data/local/    Room entities, DAOs,
            HitDay, StreakResult,                            Converters, HitItDatabase
            ScheduleEvaluator                  data/mapper/   entity ⇄ domain mappers
streak/     StreakCalculator                   data/repository RepRepository, ProfileRepository
momentum/   LevelCurve, TierLadder,            di/            Hilt modules
            MomentumCalculator                 ui/theme,navigation,components
grid/       GridAggregator                     ui/screens/    today, reps, grid, profile
(+ unit tests)                                 ui/model/      RepUi helpers
```

**Data flow:** Compose screen → ViewModel (`StateFlow`) → Repository → Room DAO (Flow). Writes go
back through the Repository; the "log a hit + award Momentum + recompute level/tier" path runs in a
single Room transaction (`RepRepository.logHit` / `clearHit`).

**Why a separate `:domain` module:** it keeps the hard logic Android-free (so accidental Android
imports fail to compile) and lets the test suite run anywhere — including SDK-less CI — via
`./gradlew :domain:test -PskipApp`. `settings.gradle.kts` includes `:app` only when `-PskipApp` is
absent.

---

## 5. Data model (Room)

All `LocalDate` stored as ISO strings; all `Instant` as epoch millis (see `Converters`).

### Current tables
- **`user_profile`** (single row `id="me"`): `momentum: Long`, `level: Int`, `tier: String`,
  `joinDate: LocalDate`. Derived from the Momentum ledger; the header also recomputes live so it is
  correct before this row exists.
- **`reps`**: `id`, `name`, `emoji`, `colorHex`, `scheduleType` (DAILY/WEEKDAYS/CUSTOM/WEEKLY),
  `customDaysCsv` ("1,3,5"), `weeklyTarget`, `targetCount`, `restDaysAllowed`, `restModeStart?`,
  `restModeEnd?`, `reminderEnabled`/`reminderHour`/`reminderMinute` (Phase 7), `isArchived`,
  `sortOrder`, `createdDate`.
- **`rep_hits`**: `id`, `repId` (FK→reps, CASCADE), `date: LocalDate`, `hitCount`, `loggedZone`,
  `timestamp`. **Unique index (repId, date)** + index on `date`.
- **`momentum_txns`** (append-only ledger): `id`, `amount`, `reason`, `repId?`, `timestamp`.
- **`hit_tasks`** (Phase 2): `id`, `title`, `notes`, `priority` (HIGH/MEDIUM/LOW), `dueDate?`,
  `tagsCsv`, `isDone`, `completedAt?`, `mainTargetDate?` (the day it is the Main Target), `repId?`
  (optional link, no FK), `sortOrder`, `createdAt`.
- **`lock_in_sessions`** (Phase 3): `id`, `startTime`, `endTime`, `plannedMinutes`, `focusedMinutes`,
  `repId?`, `taskId?`, `zone`, `completed`, `date`.
- **`check_ins`** (Phase 4): `date` (PK), `morning`, `evening`, `mood?`, `energy?`, `morningAwarded`,
  `eveningAwarded`, `updatedAt`.

- **`big_plays`** (Phase 5): `id`, `title`, `notes`, `category`, `horizon`, `targetValue?`,
  `currentValue`, `unit`, `deadline?`, `isCompleted`, `completedAt?`, `sortOrder`, `createdAt`.
- **`checkpoints`** (Phase 5): `id`, `bigPlayId` (FK→big_plays, CASCADE), `title`, `isDone`,
  `doneAt?`, `sortOrder`.
- **`locker_notes`** (Phase 5): `id`, `parentId?` (self-FK, CASCADE), `title`, `body`, `sortOrder`,
  `updatedAt`, `createdAt`.
- **`trophies`** (Phase 6): `id` (String PK, matches a domain `TrophyDef` id), `unlockedAt`.
- *(Phase 7 adds no new table — reminder fields live on `reps`.)*
- **`sudden_death_penalties`** (High-Stakes Engine): `id`, `repId` (FK→reps, CASCADE), `date`,
  `penalizedAt`; unique (repId, date) so penalties are charged at most once. `reps` also gains
  `isSuddenDeath`.

Database version: **8** (`fallbackToDestructiveMigration` is on for development).

### Reminders (Phase 7)
Per-Rep daily reminders use **WorkManager + Hilt**. `HitItApplication` implements
`Configuration.Provider` with an injected `HiltWorkerFactory`, and the manifest removes the default
`WorkManagerInitializer` (androidx.startup). The domain `reminder/ReminderSchedule.millisUntilNext`
computes the delay to the next HH:mm; `ReminderScheduler` enqueues a unique one-shot
`ReminderWorker` per Rep (`enqueueUniqueWork`, REPLACE). The worker posts the notification (skipping
rest mode) and re-schedules tomorrow's, so the exact time stays precise across days.
`MainActivity` calls `rescheduleAll()` on start to survive reboots/process death. Reminders are
cancelled on archive/delete. `POST_NOTIFICATIONS` (API 33+) is requested when the user opens Lock In;
the reminder still enqueues if denied — the notification just won't show.

### Trophies & Stats (Phase 6)
The domain `trophy/` package holds `TrophyStats` (the cross-feature snapshot) and `TrophyCatalog`
(12 `TrophyDef`s with pure `predicate: (TrophyStats) -> Boolean` rules + a one-time Momentum `bonus`).
`StatsRepository.observeStats(today)` combines the Momentum total, focus minutes, check-in count,
completed-hit count, completed-Big-Play count, and best streak into a `TrophyStats`.
`TrophyRepository.sync(stats, today)` is idempotent (insert-IGNORE + id diff) — it persists newly
earned Trophies, awards their bonus once, and recomputes the profile. `ProfileViewModel` drives
`sync` while Profile is observed.

---

## 6. Algorithms (in `:domain`)

### 6.1 Streak (`StreakCalculator`)
Returns `StreakResult(currentStreak, longestStreak, isResting, isPending)`.

**Day-based (Daily/Weekdays/Custom):**
- Build the set of **scheduled** days, then remove **rest-mode** days entirely (they never count,
  break, or consume budget).
- A day is **met** when `hitCount >= targetCount` (no carry-over between days).
- Walk backward from `today`, stopping at `createdDate`. **Today**, if scheduled but not yet met, is
  **pending** (neutral) so a streak isn't shown broken before the day ends. The current streak
  anchors on the most recent met day.
- **Rest days** = a per-gap budget (default 2) that refills after each met day: up to N consecutive
  missed scheduled days are bridged; a gap of `> N` breaks the streak (N=2 ⇒ breaks on the 3rd
  consecutive miss). Bridged misses do not add to the count.

*Worked examples:* `met, met, miss, met, met` (N=2) → streak 4. `met, met, miss×3, met` (N=2) →
current streak 2 (the 3-miss gap breaks it). Weekend days for a Weekdays rep are invisible, so a
Fri→Mon completion keeps the streak.

**Weekly target:** unit = ISO week (Mon start). A week is met when its summed hits reach
`weeklyTarget`; the streak is consecutive met weeks; the current partial week is pending. The same
budget applies as skip-**weeks**.

**Guards:** future-dated hits ignored; days before `createdDate` ignored; current schedule/target
applies retroactively (documented MVP simplification).

### 6.2 Momentum, Levels, Tiers
- `MomentumCalculator.awardForHit(streak)` = `10 + min(streak, 20)`.
- `LevelCurve`: cumulative XP to reach level L = `round(100 · (L−1)^1.5)`; `levelFor(total)` caps at
  100; `progressToNext` / `momentumToNext` drive the header bar.
- `TierLadder`: 12 contiguous brackets over levels 1–100.

### 6.3 The Grid (`GridAggregator`)
`bucket(activityThatDay)` → intensity 0–4. `yearColumns(year, intensityByDate, today)` lays the
year out as ISO-week columns of 7 cells (Mon–Sun), flagging out-of-year and future cells. The UI
(`ui/components/Heatmap.kt`) renders it on a single horizontally-scrolling `Canvas`. Per-day activity
is combined in `GridViewModel` from distinct Reps hit + a point for a Check-In that day (focus
sessions can be folded in later).

---

## 7. Build & run

### Android Studio (recommended)
1. Open the `hit-it/` folder (not the repo root) in Android Studio (latest stable).
2. Let it create `local.properties` (SDK path) and run Gradle sync.
3. Run the `app` configuration on an emulator or device (API 26+).

### Verify the core logic anywhere (no SDK needed)
```bash
cd hit-it
./gradlew :domain:test -PskipApp
```

### Termux (advanced / optional)
Termux can run Gradle and a JDK, but a full Android SDK + AAPT2/D8 toolchain on Termux is finicky.
The realistic path for building the APK is Android Studio (or a Linux/macOS machine with the SDK).
You can always run the `:domain` tests in Termux.

---

## 8. Roadmap (phased)

| Phase | Theme | Key deliverables |
|---|---|---|
| **P1 ✅** | Reps + Streaks + Grid + Momentum | done |
| **P2 ✅** | **Hits** (tasks) | done — task entity, priorities, quick due dates, **Main Target**, optional Rep link, Momentum on completion |
| **P3 ✅** | **Lock In** (focus timer) | done — presets, foreground Service, **Zones**, Rep link, pause/resume/stop, Momentum per focused minute (ambient sounds deferred) |
| **P4 ✅** | **Check-In** (journal) | done — morning/evening reflections, mood + energy sliders, Momentum per entry, feeds The Grid |
| **P5 ✅** | **Big Plays** (goals) + **The Locker** (notes) | done — goals + checkpoints + numeric progress; hierarchical notes (Rep/Hit→goal linking deferred) |
| **P6 ✅** | Gamification depth | done — persistent Trophies table + pure-domain unlock catalog (12 Trophies w/ Momentum bonuses), Profile Stats section |
| **P7 ✅** | Reminders | done — per-Rep daily reminders via WorkManager + Hilt (self-rescheduling worker, rest-mode aware, re-armed on app start) |
| **P8 ✅** | Polish | done — onboarding pager, month labels on The Grid, Glance home-screen widget (Glance 1.1.1) |
| **P9 ✅** | Testing & hardening | done — domain unit tests (streak/level edge cases) + instrumented Room DAO tests (upsert, cascade, @Relation). Compose UI tests deferred to a device-CI setup |
| **EP ✅** | Energy Pass | done — Dashboard + Momentum Score, Perfect Day multiplier + confetti/haptics, demo seed, bold theme, app-icon refresh |
| **P10** | Publish | signing keystore, Play Console listing, privacy policy, staged rollout |

### Reviewer feature backlog (sequenced, on-brand, offline-safe)
Built so far: Dashboard, Momentum Score, Perfect Day, demo seed, energetic theme, haptics, mini-grid,
"clean slate" (Energy Pass); **Sudden Death, Active Recovery, PR Trophy Case (High-Stakes Engine)**.

✅ **High-Stakes Engine (done):**
- **Sudden Death** — `Rep.isSuddenDeath` flag (toggle in Add/Edit Rep). A missed scheduled day costs
  `MomentumCalculator.suddenDeathPenalty()` (30). Pure `SuddenDeathEvaluator` finds unpenalized
  misses (idempotent via the `sudden_death_penalties` table); `SuddenDeathWorker` runs it on app
  open + daily and fires a "heartbeat" alert on its own high-importance notification channel.
- **Active Recovery** — logging a rep during its Rest-mode window earns reduced recovery Momentum
  (`awardForRecovery()` = 4) and keeps the streak frozen.
- **PR Trophy Case** — Profile "Personal Records 🏆" section (longest streak, focus logged, hits
  crushed, check-ins, lifetime Momentum, tier) + new trophies **Iron Lung** (60-day streak) and
  **Centurion** (100 hits).

Remaining, in priority order:
1. **The Gauntlet** — monthly time-boxed challenge (e.g. "50 reps in June"); `Gauntlet` table +
   screen + countdown + bonus. (Largest remaining piece — its own build.)
2. **Interactive spotlight onboarding** — replace the static pager with a spotlight overlay on the
   seeded Dashboard; tie the `POST_NOTIFICATIONS` prompt to the first logged Hit.
3. **Notification engine upgrade** — interactive "HIT IT ⚡" lock-screen action (mark done from the
   notification via a BroadcastReceiver), positive-reframing copy, Perfect-Day evening wrap-up.
4. **"Comeback Kid" trophy** — needs streak-history persistence (restore a broken streak 7 days
   running); deferred until we track streak breaks, not just the current best.
5. **Rep → Big Play linking with rollup** (carried over from P5 deferral).

### Explicitly NOT built (require infra we agreed to avoid — flag before starting)
These contradict the offline-first, no-account design and need a deliberate decision:
- **Social leaderboards / accountability buddies / share-to-story** — need a backend + accounts.
- **Real-time multi-device sync** — needs a backend.
- **AI Coach / AI insights** — needs a network LLM (Claude API key) or on-device model; adds a
  network dependency. Can be added as an opt-in if desired.
- **Ambient soundscapes** — need licensed audio assets.

---

## 9. Copy-paste prompts for Claude (future phases)

**P2 — Hits (tasks):**
> Add a "Hits" (tasks) module to the Hit it Android app. Create a Room `hit_tasks` entity (title,
> notes, priority HIGH/MEDIUM/LOW, optional dueDate, tags, isDone, completedAt, optional `repId`
> link, and a `mainTargetDate` for the daily "Main Target"). Add a HitTaskDao, repository methods,
> and a `LogTaskUseCase` that awards Momentum via the existing `MomentumCalculator` in a Room
> transaction. Build a Hits screen (list + add/edit) and a "Main Target" card on Today. Reuse the
> existing theme, navigation, and Momentum header. Follow the architecture in BLUEPRINT.md.

**P3 — Lock In (focus timer):**
> Add a "Lock In" focus timer to Hit it. Create a `lock_in_sessions` Room entity (start, end,
> durationMinutes, optional repId/taskId, zone theme). Implement a foreground Service so the
> countdown survives navigation/backgrounding, with presets 15/25/45/60/90 min and a fullscreen
> timer UI with selectable "Zones". On completion, log the session and award Momentum for focus
> minutes. Keep all logic that can be pure in `:domain` with unit tests.

**P4 — Check-In (journal):**
> Add "Check-In" to Hit it: a `check_ins` Room entity keyed by date with morning/evening text, mood
> (1–10) and energy (1–10). Build morning/evening prompt screens and make a completed Check-In count
> toward The Grid intensity for that day. Award Momentum per check-in.

**P5 — Big Plays + The Locker:**
> Add "Big Plays" (goals) and "The Locker" (notes) to Hit it. Big Plays: a `big_plays` entity with
> category, horizon (Quarter/Year/2Y/3Y), target/current values, and `checkpoints` (milestones);
> link reps/hits to a Big Play and roll up progress. The Locker: hierarchical rich-text notes
> (`locker_notes` with a parentId tree). Reuse existing patterns.

**P7 — Reminders:**
> Add per-Rep reminders to Hit it using WorkManager + Hilt (`hilt-work` is already in the catalog).
> Wire a `HiltWorkerFactory` via a `Configuration.Provider` in `HitItApplication`, schedule daily
> notifications based on each rep's schedule, and respect rest mode.

**P10 — Publish:**
> Walk me through releasing Hit it on Google Play: generate a signing keystore, configure
> `signingConfigs`/release build, enable R8, write a privacy policy ("all data stays on device"),
> and prepare the Play Console listing (screenshots, description, data-safety form).

---

## 10. Publishing notes (P10 preview)
- **Keystore:** `keytool -genkey -v -keystore hitit-release.jks -keyalg RSA -keysize 2048 -validity
  10000 -alias hitit`. Never commit the keystore (see `.gitignore`).
- **Signing:** add a `signingConfigs.release` reading from a local `keystore.properties` (gitignored)
  and reference it from `buildTypes.release`; flip `isMinifyEnabled = true` and test R8.
- **Play Console:** $25 one-time account; complete the Data Safety form (declare "no data
  collected / all on device"); provide a privacy policy URL; upload an `.aab` (`./gradlew
  :app:bundleRelease`).

---

## 11. Known limitations / first-sync notes
- The `:app` module could not be compiled in the authoring environment (no Android SDK), so the first
  Android Studio sync may surface a minor tweak (e.g., an icon import). Versions are pinned to
  minimize this.
- Clearing a hit posts an approximate compensating Momentum entry (it reverses the standard award for
  the current streak), so the ledger stays close to balanced.
- Schedule/target edits apply retroactively to history (simplest correct behavior for now).
- **Lock In** keeps the timer running across navigation and while backgrounded (foreground Service),
  but it does not yet survive full process death (the countdown isn't reconstructed from a saved
  start time). Ambient sounds are deferred (no audio assets).
- The Lock In service declares `foregroundServiceType="specialUse"` (subtype `focus_timer`) — fine
  for development; for a Play release confirm Google accepts it or switch to a more specific type.
  `POST_NOTIFICATIONS` is requested when opening Lock In; the timer still runs if it's denied (the
  notification just won't show).
- **Reminders** are exact-ish via WorkManager one-shot delays; WorkManager may defer firing under
  Doze/battery optimization (acceptable for habit nudges, not alarm-grade). The worker re-schedules
  the next day on each fire, and `MainActivity` re-arms all on start, so opening the app keeps them
  on track even if a fire was deferred or the device rebooted. `POST_NOTIFICATIONS` shares the prompt
  triggered from Lock In; if a user only uses reminders they may need to enable notifications in
  system settings. Phase 7 adds the `androidx.hilt:hilt-work` + `hilt-compiler` (KSP) deps and the
  manifest `WorkManagerInitializer` removal — the most likely first-sync touch-points.
