# CLOCK OUT

A first-person comedy-stealth game about leaving work, set on an IT floor in HITEC City, Hyderabad. You're a Software Engineer II who needs to leave the floor *right now*, and the office is the enemy: coworkers, "quick connects", vision cones and social obligation. Everything is beige.

**Sneak** (cover, crouch, sound discipline) → **get spotted** → **talk your way out** (6-second timed excuses) → **live with it** (the office remembers) → **reach the exit**.

Built with Vite + TypeScript (strict) + three.js. All geometry is procedural primitives, all audio is synthesised with Web Audio, and there are no external assets.

```bash
cd clock-out
npm install
npm run dev            # http://localhost:5173
npm run build          # tsc + vite build, zero TS errors
npm run check:levels   # validates level ASCII: widths, walkable NPC cells, exit reachable
```

### Android APK

The game is wrapped with [Capacitor](https://capacitorjs.com/) (`android/`). It runs landscape and fullscreen and keeps the screen on. On touch devices, on-screen controls replace the mouse and keyboard:
- a floating joystick under your left thumb
- drag with your right thumb to look
- USE, CROUCH and SPRINT buttons, plus II to pause
- tap an excuse to say it; tap the conversation panel to skip or continue

```bash
# needs JDK 21 and the Android SDK (platform 36); set ANDROID_HOME
npm run android:apk    # -> android/app/build/outputs/apk/debug/app-debug.apk
```

The APK is debug-signed, fine for sideloading. Publishing to Play needs a release keystore (`./gradlew bundleRelease` with signing config).

Add `?debug` to the URL to expose `window.__clockout` (the Game instance) and skip auto-pause on pointer-lock loss.

## Controls

| Key | Action |
|---|---|
| WASD / arrows | Move |
| Mouse | Look (click the game to capture the pointer; if the browser refuses pointer lock, drag to look) |
| Shift | Sprint. Loud (14 m), and it stands you up |
| **C** | Toggle crouch. Quiet (3 m), slow, and hidden behind 1.5 m dividers |
| Ctrl (hold) | Crouch while held. **Use C instead:** browsers never let a page intercept Ctrl+W |
| E | Use / talk: jam copier, hydrate, call elevator, open the closet, talk to someone |
| 1–4 | Pick an excuse or pivot line (or mouse-wheel / move the mouse to highlight, then click) |
| Space / click | Skip the typewriter, continue |
| Esc | Pause |

## How it plays

- **Dividers are 1.5 m tall.** Crouched, your head is at about 1.0 m and you are invisible behind them. Standing, you're a head on a stick. Desks (0.74 m) hide nothing; copiers (1.3 m) do.
- **Offices look like offices.** Every floor gets framed team photos (Annual Day, the Wonderla offsite, a colleague who left, faded out), motivational posters that have given up ("WORK-LIFE BALANCE: Work is life. Balance is optional."), a Thought-for-the-Day whiteboard, a sprint board where DONE says "Lunch", a cork notice board (the lost steel tiffin), Employee of the Month (Deepak, again) and birthday banners. There are also tea/coffee premix machines (E: sip slowly, it's an alibi), acoustic panels that soak up your footsteps, server rooms that hum over them, and a reception desk with a small Ganesha. All of it is painted onto canvases at load time (`world/WallDecor.ts`).
- **The `?` over a head is suspicion filling.** At 0.35 they walk over to look. At `!` they come to talk. Every NPC shows a faint vision cone on the floor (toggle in Settings).
- **Footsteps are noise events:** crouch 3 m, walk 7 m, sprint 14 m, bumping furniture 10 m, the supply-closet door 12 m. **Walls don't block noise.** That's a deliberate design choice: simple to read, punishing, and funny when Kavita hears you through drywall.
- **Don't walk away from someone calling your name.** Two seconds of ignoring them and they chase you, faster than you walk and slower than you sprint. Reaching the exit while anyone is confronting or chasing you counts as caught.
- **Conversations** follow the spec: opener → 3–4 excuses on a 6 s fuse → maybe a follow-up with a 3 s pivot → a priced verdict. **Too slow and you blurt the riskiest excuse on screen.** Panic is a mechanic. After every conversation you see the line-item breakdown, which is how you learn the cast.

| Suspicion | Outcome |
|---|---|
| 0–3 | **PASSED**: they let you go. +1 "smooth streak" (max 2) for the rest of the level |
| 4–6 | **PROBED**: they walk beside you for 8 s. Head for the exit and the conversation starts again, worse |
| 7–9 | **ESCORTED**: walked back to your desk, −45 seconds |
| 10+ | **CAUGHT**: "Quick connect" with Ramesh sir, and an HR incident report built from what you actually said |

**Office Memory** (localStorage, survives sessions):
- Reused excuses show **[USED]** and cost +2 / +4 / +6.
- Each NPC remembers which excuses they've personally heard, and opens with "Let me guess, another emergency?"
- Every time you're caught, all excuses cost +0.5 more.
- Your reputation climbs from nobody → flaky → suspicious, or to legend with 3 trophies.
- Absurd excuses that actually work (Rahu kalam, the astrologer, the parrot) become **Legend trophies** on the main menu.
- "The cooker situation has… escalated." only unlocks after you've used the cooker.
- **Reset office memory** is on the main menu.

## Chapters

Thirteen chapters on HITEC City floors. They unlock in order: escape one (any ending) to open the next. Settings → "Unlock all chapters" skips that, and so does `?debug`.

| # | Chapter | Goal | What makes it different |
|---|---|---|---|
| 1 | **The 1 PM Vanishing** | Lift before the 1:15 "quick connect" | Outlook countdown; Ramesh sir leaves his cabin at T+40; lift opens at T+70 or when called |
| 2 | **The Coorg Trip Cover-Up** | Grab your charger, then back out the stairs | You're "on sick leave". **Objective** at your desk. Rohit is lost in reels until your friend's story tags you (T+45): everyone gets warier |
| 3 | **The 6:30 PM Escape** | Lift home | **Looking busy**: while Ramesh sir is at his desk, everyone notices you 1.7× faster and excuses cost +2. E on your laptop to type furiously (cover). He logs off at T+100 |
| 4 | **The Forbidden Floor** | UP stairwell to Ananya on 7 | Rohit snitches, Priya patrols, printer jams (3 favors), creaky supply closet |
| 5 | **The Client Visit Performance** | Lift, without being volunteered | Stage in the middle of the floor; HR needs four dancers. Rehearsal (T+50) pulls everyone to the stage and opens the lift |
| 6 | **The Power Cut Gambit** | Main door or the server-room fire exit | Deepak comes to "verify" your network issue. **Real power cut** at T+35: lights out, vision halved for 25 s. Server racks hide footsteps. **Fire exit** works, but the alarm costs a star |
| 7 | **Pressure Cooker Run** | Leave the building | The cooker excuse is forced into memory; Kavita is parked by the exit; town hall doubles every FOV at T+30; Kavita's phone at T+55 |
| 8 | **The Appraisal Meeting** | Out before they come back | Start in room 4B next to "PIP_final_v3.docx". Ramesh sir and Priya return at T+25. Fire exit right outside |
| 9 | **The 1:30 AM Shift Change** | Lift before the 2:00 AM handover | **Night theme**. Deepak asleep on his keyboard, Srinivas doing rounds, Sanjay on Dallas hours, handover invite countdown |
| 10 | **The Cafeteria Aunty's Gauntlet** | Plate from the counter, then back down the stairs | **Objective** at Lakshmi's counter, Ramesh sir eating facing it, the **1 PM rush** (T+40) queues everyone at the counter |
| 11 | **The 'Patience-Building' Salary Delay** | Leave for your interview | Blazer on a Tuesday (+2). LinkedIn tells Priya she viewed your profile (T+30) |
| 12 | **The Movie Theatre Laptop** | Lobby, to take Ramesh sir's call | **Dark cinema**: seat backs hide a crouch, the usher's torch is Srinivas anna, Rinku is bunking by the exit, **interval** at T+60 turns the lights up and sends everyone to the doors |
| 13 | **The Festival Bonus Escape** | Out for Diwali | **Festival theme**: marigold torans, rangoli and diyas at the door. Priya stands on the rangoli with an attendance sheet until the kaju katli arrives (T+40) |

Endings: **CLEAN** (no conversations, under par) · **ESCAPED** · **LEGEND** (an absurd excuse worked) · **PROMOTION** (passed Ramesh sir with a corporate excuse and finished under par; "Good initiative.") · **CAUGHT** (with an HR incident report).

## The cast

Nine people from across India. Region shows in small, true details (a thin vibhuti line, a kara, a chandanam mark, a few words of home language in the barks), never as costume or caricature. Faces are built by `ai/FaceBuilder.ts` from per-character recipes in `data/npcs.ts`; open `?lineup` to see everyone side by side.

| | Who | Tells | What they believe |
|---|---|---|---|
| **Ramesh Iyer** · Chennai | Senior Manager. Glass cabin. "Quick connect at 6:45, ma." | Thin vibhuti + kumkum, grey temples, gold glasses, half-sleeve shirt, pen pocket | Hates whimsy (absurd +3). Respects process (corporate −1). KT / onsite excuses don't work on him |
| **Kavita Deshpande** · Pune | Admin & Facilities. Runs the floor's WhatsApp groups. Hears everything (×1.3) | Chandrakor bindi, green bangles, mangalsutra, gajra bun, kurti with a Paithani-style border | LOVES romance (−3). Wants details on evasive answers (+2) |
| **Rinku Gogoi** · Jorhat | Fresher, week 3. Still believes in all of it | Gamosa-weave strap on the backpack, company welcome-kit hoodie | Believes absurd excuses (−2). Your gentlest tutorial |
| **Priya Menon** · Thrissur | HR Business Partner. Has a form for the form | Chandanam line, jhumkas, cream kurta with a thin gold border, long braid | Can't ask about medical (−1). Evasive +3. Romance +2 |
| **Rohit Malhotra** · Ludhiana | Senior SE. Tells Ramesh sir everything | Steel kara, gelled hair, beard, rolled sleeves | Spotting you, or talking to you, puts Priya on you |
| **Srinivas Goud** · Secunderabad | Facilities & Security. Knows every access card | Security cap, thick moustache, red kalava thread | "These house owners, no?" (domestic −1). Evasive +3 |
| **Lakshmi Reddy** · Warangal | Cafeteria supervisor, 22 years. "Tinnara?" | Gold bangles, bindi, small nath, apron over a cotton saree | Knows your calendar (corporate +1) |
| **Deepak Hegde** · Mangaluru | Team Lead. Takes the credit, gives the tickets | Red-and-yellow lanyard, stubble, black frames | Delighted by absurdity (−2). Director wanderer |
| **Sanjay Mukherjee** · Kolkata via Dallas | Director. "In Dallas we…" | Receding hairline, the only blazer in Hyderabad in May | Boss rules. Director wanderer |

## Architecture

```
src/core      Game (state machine + orchestration), Input (intent), Clock (fixed 60 Hz step), Events (typed bus), GameState
src/world     WallDecor (canvas-painted team photos, posters, boards, torans),
              OfficeKit (procedural props, merged into a few draw calls with baked vertex-colour AO),
              LevelBuilder (ASCII → scene + colliders + nav), NavGrid (8-dir A*, no corner cutting, string-pulled),
              Colliders (AABB, capsule-as-circle resolution, 3D segment LOS), Lighting
src/player    PlayerController, PlayerNoise, Interactor
src/ai        NPC (body, bubbles, cone, movement), NPCStates (IDLE/PATROL/CHATTER/SUSPICIOUS/INVESTIGATE/CONFRONT/CHASE/ESCORT/RETURN),
              Vision, Hearing, Director
src/dialogue  DialogueSystem (encounter flow), DialogueUI (DOM), ExcuseRegistry (draw + price), OfficeMemory (localStorage)
src/ui        HUD, Vignette, ResultsScreen (+ HR report), MainMenu
src/audio     AudioBus (office hum, footsteps, voice blips, elevator ding, FM sting, copier jam, creak, phone, heartbeat)
src/ai        FaceBuilder (recipe-driven layered-primitive faces)
src/data      types, excuses (37, Indian IT context), dialogueLines, npcs (9, with face recipes), levels/
src/debug     Lineup (?lineup character sign-off page)
```

Every gameplay number is in a `TUNING` block at the top of its module. See [TUNING.md](TUNING.md).

## Design notes and decisions

- **Walls don't block noise.** Spec'd, kept, and leaned into: hearing is how Kavita earns her reputation.
- **Line-item verdicts.** The spec's formula (`risk + memory + archetype − charisma`) is shown to the player as a receipt after every conversation. Learning *why* Kavita bought the romance and Ramesh sir didn't buy the parrot is where the comedy and the strategy meet.
- **Rules added during tuning.** A headless autopilot showed that the spec's numbers alone let you talk your way through every level. These rules came out of that:
  - *Repeat stops* cost +3 per earlier stop by the same person this run.
  - *Levels 2 and 3 add +2 suspicion* ("Nobody goes UP at 3 PM").
  - *Walking away* from a confronter becomes a chase.
  - *Leaving while being called* counts as caught.
  - *First offense* can't go straight to CAUGHT unless you blurted, so a first-time player isn't game-overed by one bad hand.
- **Only one NPC can confront you at a time**, and the Director freezes other patrols when heat is high. Bad moments stay funny instead of turning into a dogpile.
- **Phone calls turn NPCs sideways, not away**, so the window they open doesn't also open the exit for free. On the phone they ignore quiet footsteps.
- **Voice is Indian English, not American.** No "that's so valid". Rinku says "Ho jayega sir, don't worry. Aap jaao, I'll manage." Each character gets at most one home-language word per line.
- **Blurts are on the receipt.** A timed-out answer that still passes shows as "PASSED (barely)"; other outcomes get "(blurted)".
- **Dark levels have points of interest.** The 1:30 AM shift has Sanjay's warm-lit cabin (a Dallas call running late), a security desk lamp, a parked housekeeping cart, and Deepak's phone lighting up on his desk. The power cut switches on red and green emergency light pools, and the monitors fade out with an afterglow instead of snapping to black (`world/Props.ts`, level `props`).
- **Run timer.** The HUD shows the elapsed seconds against par; it turns amber once you're over par.
- **Watercooler "hydrating"** (4 s at 30% detection fill) was added as a low-stakes use for the spec'd watercooler.

## Deviations from the spec

- **Project folder.** The spec assumed an empty folder. This repository already had an unrelated `package.json` at its root, so the game lives in `clock-out/`.
- **tsconfig.** `strict: true` is on. The Vite template's `erasableSyntaxOnly` flag is off, so constructor parameter properties are allowed.
- **Schemas.** The five spec interfaces are implemented as written, with additive extensions:
  - ASCII `G` glass, `L` plant, `O` closet door, `V` vending machine, `A` acoustic wall, `R` reception, `T` stage, `Z` server rack, `B` bean bag, `H` theatre seats, `F` canteen counter, `U` cinema screen, `Q` fire exit.
  - ScriptedBeat types `calendar`, `elevator`, `allHands`, `powerCut`, `gather`, `heat`, `despawn` and `toast`.
  - Optional LevelData fields `favors`, `clock`, `forceUsedExcuses`, `intro`, `directorBudget` and `suspicionModifier`.
  - An `NPCLook` table (visuals and voice) kept separate so `NPCDef` stays exact.
  - A `MENU` game state.
- **`reputation`** is stored as a number, per the spec's type. The label (nobody / flaky / suspicious / legend) is derived from it.
- **Crouch on Ctrl** works, but C is the recommended binding (Ctrl+W can't be blocked by any web page).
- **Commits.** The build was committed as a few milestone commits, not one per build step.
- **Verification.** Verified headlessly: `tsc`, the production build, Playwright smoke runs of all three levels with zero console errors, a divider-cover test (crouched: awareness 0.01 over 25 s; standing: confronted), and autopilot playthroughs. Audio was exercised without errors but not listened to. Mouse feel, readability at a glance and whether the jokes land need a human playtest.

## Experimental: photoreal Ramesh (Gaussian splat)

One character can be swapped for a photoreal Gaussian-splat avatar ([Gaussian-VRM](https://github.com/naruya/gaussian-vrm), MIT). The rest of the office stays low-poly, and that contrast is the point. The NPC still runs the AI, colliders, "?" marker and speech bubbles; the splat only replaces what you see (`src/ai/SplatAvatar.ts`).

**No avatar ships with the repo.** A splat is a scan of a real person, and Ramesh is the antagonist, so use someone who has agreed to be scanned and cast.

1. Scan the person (a phone scanning app that exports a Gaussian-splat `.ply`, standing in an A-pose, full body).
2. Open the [Gaussian-VRM demo](https://naruya.github.io/gaussian-vrm/), load the `.ply`, and save the rigged `.gvrm` it produces.
3. Put it at `public/splat/ramesh.gvrm` (gitignored).
4. Turn on **Settings → Experimental: photoreal Ramesh**, or open the game with `?splat=splat/ramesh.gvrm`.

If the file is missing or fails to load, the regular Ramesh stays and the game carries on.

Things to know:
- **Size.** A `.gvrm` is 25–50 MB. If it's in `public/splat/` when you build the APK, it ships inside the APK. The splat library (about 600 kB) loads only when the feature is on.
- **Lighting.** Splats carry the light they were captured in. A tint patched into the splat shader darkens him for night (×0.5), the theatre (×0.4) and power cuts (×0.35).
- **Animation.** No Mixamo files needed: the arms-down pose, breathing and walking leg swing are driven procedurally from the NPC's movement. The face does not animate; this format has no facial rig.
- **Load time.** About 7.5 s per level in a software-rendered test browser. It hasn't been measured on a phone yet.

## What I'd build next

1. **A human playtest pass** on the numbers in TUNING.md, especially the dialogue economy and Level 3's Linda loop.
2. **Nav-aware noise** as an option (sound through doorways only) to compare against the current rule.
3. **More per-character dialogue trees**: NPC-specific follow-ups ("Which clinic? I go to the one near Kondapur."), and Kavita repeating your last excuse to whoever you meet next.
4. **Readable NPC intent**: footstep sounds for NPCs, head-turn telegraphs before a patrol pivot, and a map of the floor on the pause screen.
5. **Replay ghosts and a daily seed**: one floor, one set of hands, a leaderboard for fastest CLEAN.
6. **Floor 7**: the date. Twelve minutes of sustained eye contact. The same systems, reversed.
