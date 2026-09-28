# TUNING

Every gameplay number lives in a `const TUNING = { … }` block at the top of the module that uses it. This file lists them, says what each one does to the feel of the game, and records how they were tuned.

Level-specific numbers (par times, favors, clock speed, suspicion modifier, NPC placements) live in `src/data/levels/*.ts`. Character numbers (vision, hearing, walk speed, talkativeness) live in `src/data/npcs.ts`.

Prop dimensions inside `OfficeKit.ts` builders (a desk leg's width, a monitor's size) are model data, not tuning. They sit inline where the prop is built. The heights that matter for gameplay (divider, desk, copier, wall) are in OfficeKit's `TUNING`.

---

## How the numbers were tuned

No human playtesting was possible in the build environment. The numbers were tuned with a headless autopilot that drives the real simulation. It follows waypoint plans, crouches on cue, stops when confronted, stands still while probed, waits for wanderers to pass, and always picks the cheapest excuse. It ran each level through several routes. Final results:

| Level | Route | Result |
|---|---|---|
| 1 | Crouch along the south wall, hide at the copiers, go when the doors open | Escaped, ~62–75 s (par 85) |
| 1 | Walk straight at the lift through Ramesh sir's sightline | Caught or escorted by the 3rd Ramesh stop |
| 2 | Crouch the north side, jam the copier, sneak to the stairs | Escaped, ~30–42 s |
| 2 | Hide in the supply closet, then go | Escaped, ~55–65 s |
| 2 | Walk straight | Escaped only after two PROBED conversations |
| 2 | Walk straight and ignore Rohit calling you | Caught ("fled") |
| 3 | South wall, time Lakshmi's loop, dash on Kavita's phone call | Escaped ~50% of runs, ~130 s (par 150) |
| 3 | Walk straight / north routes | Mostly escorted or caught |

The bot plays conversations with perfect information: it sees every hidden modifier. A human sees only the risk dots. Expect humans to do somewhat worse in conversations and better at reading patrols.

---

## Detection (`ai/NPCStates.ts`, `ai/Vision.ts`, `ai/NPC.ts`)

| Key | Value | Effect |
|---|---|---|
| `suspiciousAt` | 0.35 | Awareness at which an NPC says "Hm?" and walks toward where they saw or heard you |
| `confrontAt` | 1.0 | Awareness at which they come to talk (dialogue starts on reach) |
| `fillBase` | 0.4 /s | Fill rate for a standing player in full view at max range. Mid-range is about 1.3 s to a confront |
| `nearBonus` | 1.8 | Fill multiplier ramps to 2.8× at point-blank |
| `peripheralMul` | 0.5 | The outer 30% of the FOV (`peripheralFraction`) fills at half rate |
| `crouchMul` | 0.4 | Crouched in the open: about 3 s to get noticed at mid-range. Behind a divider: never |
| `sprintMul` | 1.6 | Running is conspicuous |
| `hydratingMul` | 0.3 | Standing at the watercooler after pressing E |
| `chatterMul` / `chatterRangeMul` | 0.5 / 0.7 | Two NPCs chatting are distracted |
| `distractedRangeMul` / `distractedFovMul` | 0.35 / 0.5 | On the phone. Quiet footsteps are also ignored |
| `decayDelay` / `decayRate` | 2 s / 0.15 /s | How long suspicion lingers once you're out of sight |
| `blockedCap` | 0.92 | While one NPC is confronting you, others can't escalate (no dogpiles) |
| `touchRange` | 1.0 m | Inside this you're noticed whichever way they face |
| `eyeHeight` / `minEyeHeight` | 1.62 / 1.56 m | Standing NPCs always see over a 1.5 m divider |
| `hearGain` | crouch .04, walk .10, sprint .20, bump/door .35 | Awareness added per heard noise event |

The core height relationship, which is the whole stealth game:

| Thing | Height |
|---|---|
| Cubicle divider (`OfficeKit.dividerHeight`) | 1.5 m |
| Crouched head (vision sample) | ~1.02 m, hidden |
| Standing head (vision sample) | ~1.72 m, visible |
| Copier (`copierHeight`) | 1.3 m, hides a crouched player |
| Desk (`deskHeight`) | 0.74 m, hides nothing |

## Pursuit

| Key | Value | Effect |
|---|---|---|
| `confrontSpeedMul` | 1.45× walk | NPC hurrying over to "have a word" |
| `ignoreBeforeChase` | 2 s | Walking away from someone calling your name turns into a chase |
| `chaseTriggerDist` | 3.2 m | Sprinting away beyond this starts a chase immediately |
| `chaseSpeed` | 4.7 m/s | Faster than walking (3.2), slower than sprinting (5.6) |
| `catchReach` | 1.05 m | Caught |
| `chaseGiveUp` | 4 s | Out of sight this long and they give up |
| `confrontTimeout` | 10 s | A confronter who loses you goes back to investigating |

Reaching the exit while anyone is confronting or chasing you is a CAUGHT ("fled").

## Noise (`player/PlayerNoise.ts`)

| Movement | Radius | Stride |
|---|---|---|
| Crouch-walk | 3 m | 0.65 m |
| Walk | 7 m | 0.85 m |
| Sprint | 14 m | 1.25 m |
| Bump furniture (one-shot) | 10 m | — |
| Creaky closet door (one-shot) | 12 m | — |
| Copier jam (not the player) | 20 m (`Game.jamRadius`) | — |

Each NPC's `hearingMul` scales the radius they can hear from: Kavita 1.3, Ramesh 0.8. Walls don't block noise; that is deliberate.

## Player (`player/PlayerController.ts`)

| Key | Value |
|---|---|
| `walkSpeed` / `crouchSpeed` / `sprintSpeed` | 3.2 / 1.55 / 5.6 m/s |
| `radius` | 0.35 m capsule |
| `heightStand` / `heightCrouch` | 1.8 / 1.1 m |
| `eyeStand` / `eyeCrouch` | 1.64 / 0.98 m |
| `bumpMinSpeed` / `bumpCooldown` | 2.4 m/s / 1.2 s |
| `bobWalk` / `bobSprint` / `bobCrouch` | 0.032 / 0.055 / 0.018 m |

## Dialogue (`dialogue/DialogueSystem.ts`, `dialogue/ExcuseRegistry.ts`, `dialogue/OfficeMemory.ts`)

| Key | Value | Effect |
|---|---|---|
| `choiceSeconds` | 6 s | Excuse timer. At zero you blurt the riskiest card on screen |
| `pivotSeconds` | 3 s | Follow-up recovery timer |
| `pivot` | good 0, deflect +1, flounder +2, timeout +3 | Cost of each follow-up answer |
| `followUpBase` + `followUpPerTalk` | 0.3 + 0.55 × talkativeness | Chance they probe (`needs_detail` excuses always probe) |
| `bigHandTalkativeness` | 0.8 | NPCs at or above this give you 4 cards instead of 3 |
| `plausibleRisk` | 3 | Every hand holds at least one unused excuse at or below this risk |
| `showUsedChance` | 0.6 | A `[USED]` excuse is forced into the hand so memory stays visible |
| `repeatStop` | +3 per earlier stop | "Didn't I just see you?" Per NPC, per run |
| `immunePenalty` | +4 | e.g. Ramesh sir hearing "KT session with the other team" |
| `contextMissPenalty` | +2 | e.g. "grabbing a coffee" nowhere near the kitchen |
| `seenCrouching` / `seenSprinting` / `nearExit` | +2 / +1 / +1 | |
| `voluntaryBonus` | −1 | You walked up and pressed E: confidence |
| `panicPenalty` | +1 | Blurted |
| `reusePenalty` | 0, +2, +4, +6 | 1st/2nd/3rd/4th+ use of an excuse on this profile (halved for `repeatable`) |
| `sameNpcPenalty` | +1 | This person heard this excuse from you before, in any session |
| `timesCaughtPenalty` | +0.5 per time caught | Applies to every excuse, forever (until Reset) |
| `Game.maxCharisma` | 2 | "Smooth streak": −1 per PASSED this run, capped |
| Level `suspicionModifier` | L2 +2, L3 +2 | "Nobody goes UP at 3 PM" / "Leaving at 4:10 is a statement" |

Archetype affinities (the `AFFINITY` table in ExcuseRegistry) are listed in the README.

Outcomes, from the spec:

| Final suspicion | Outcome |
|---|---|
| 0–3 | PASSED: 12 s grace (`passGraceSeconds`), +1 charisma |
| 4–6 | PROBED: they walk with you for 8 s (`probeSeconds`). Closing 2.5 m on the exit (`probeViolationDist`) restarts the talk |
| 7–9 | ESCORTED: fade out, back to your desk, −45 s (`escortPenaltySeconds`) |
| 10+ | CAUGHT, unless it's that NPC's first stop and you didn't blurt, which is ESCORTED instead ("first offense") |

## Director (`ai/Director.ts`)

| Key | Value | Effect |
|---|---|---|
| `weightAwareness` / `weightNoise` / `weightExit` | 0.55 / 0.25 / 0.2 | Heat blend |
| `lowHeat` / `lowHeatSpawnAfter` | 0.22 / 20 s | Quiet for 20 s spawns a wanderer on your likely path |
| `highHeat` | 0.6 | Above this, every patrol except the most suspicious NPC freezes |
| `minSpawnDistance` / `minSpawnAngle` | 12 m / 80° | Spawns never happen in view: behind a wall or behind you |
| `firstSpawnNotBefore` | 25 s | |
| Level `directorBudget` | 1 per level | |

## Level mechanics (`core/Game.ts`)

| Key | Value |
|---|---|
| `elevatorCallDelay` / `elevatorOpenSeconds` | 10 s / 10 s |
| `jamSeconds` / `jamHeat` | 10 s distraction / +0.25 heat |
| `hydrateSeconds` / `hydrateReach` | 4 s / 2.2 m |
| `allHandsFovMul` | 2× for the all-hands beat |
| `starSlack` | 3 stars ≤ par, 2 stars ≤ 1.5 × par, else 1 |
| `encounterCooldown` | 3 s after any conversation |

## Presentation

- `DialogueUI`: 30 ms/char typewriter, 100 ms choice stagger, timer turns red under 2 s.
- `Vignette`: heartbeat above 0.6 awareness, one beat every 0.95 → 0.45 s.
- `Lighting`: tint `#e8f0e4`, fog `#cfd6cc` @ 0.015, hemisphere 1.1, key 0.55, fill 0.25.
- Power cut screens: fade out with time constant 0.9 s (afterglow) and come back in 0.35 s with a boot flicker (`screenFadeOut`, `screenFadeIn`).
- `Props`: emergency lights 9 cd, range 7 m, 0.6 s stutter on; call room 20 cd; desk lamp 6 cd; phone 2 cd, lights up for 3.2 s every 7 s.
- Realistic heads (`NPC.ts`): scale 1.18× real, cartoon head beyond 16 m, reactions held 3.5 s, blink every 2.5–6 s (0.16 s), lip-flap jaw 0.28 at 13 Hz. Dialogue close-up (`Game.ts`): 0.8 m of subject in frame, min FOV 18°, ease rate 3.
- Realistic skin light (`Lighting.ts` `realisticAmbient` 1.2, via `RealLight.ts`): extra ambient that only realistic materials receive. Without it the office lighting crushes realistic skin by 15–25 L*. An environment map did the same job but halved the frame rate in the software renderer.
- Skin tones (`tools/gnm/cast.json`): L* 27–62 (mean 43.6, held from before; spread widened from sd 10.0 to about 12). The dark end is already near the floor of human skin, so variance can't triple without shifting the mean.
- Gait (`gnmCast.ts`, `RealBody.Gait`): per-character tempo, lean and arm swing on shared clips. Ramesh 0.9 / −0.06 / 0.7, Kavita 1.12 / +0.03 / 0.8, Rohit 0.94 / −0.03 / 1.35, Lakshmi 0.86 / +0.07 / 0.55.
- `SplatAvatar`: arm drop 1.2 rad, elbow 0.15, breathing 0.25 Hz ± 0.02, leg swing 0.45 rad per m/s at 1.7 Hz, ground offset 0 m. Brightness: night 0.5, theatre 0.4, power cut ×0.35.
- `AudioBus`: hum at 60 Hz ± 0.35 Hz (plus 120 Hz), ducked to 30% with a 350 Hz low-pass during dialogue over 150 ms.

## Chapter mechanics (`core/Game.ts`, `world/LevelBuilder.ts`, level files)

| Key | Value | Effect |
|---|---|---|
| `lookBusySeconds` | 5 s | E on your laptop: detection fill ×0.3 (`hydratingMul`) while you stay within 2.2 m |
| Vending machine / cooler cover | 4 s | Same cover, anchored at the machine |
| `watcher.fillMul` (ch 3) | 1.7× | Everyone fills faster while Ramesh sir is within 1.5 m (`watcherPostReach`) of his post |
| `watcher.value` (ch 3) | +2 | Added to every excuse while he's there |
| `rangeMulTheme` | day 1, festival 1, night 0.8, theatre 0.7 | Vision range by theme |
| `powerCutRangeMul` | 0.5 | Stacks with theme during a power cut |
| `acousticRadius` / `acousticFactor` | 2.6 m / ×0.55 | Your noise radius near acoustic panels |
| `serverRadius` / `serverFactor` | 3.5 m / ×0.4 | Your noise radius near server racks |
| Fire exit | −1 star | Always an escape, never a clean one |
| `objectiveReach` | 1.1 m | How close you must get to tick off an objective |
| Chapter pars | 45–95 s | Set from the autopilot's straight-line time plus room for a stealth route |

Suspicion modifiers by chapter: 1: 0 · 2: +2 (sick leave) · 3: +2 while Ramesh sir is seated · 4: +2 · 5: +2 · 6: +2 · 7: +2 · 8: +2 · 9: +2 · 10: +2 · 11: +2 · 12: +2 · 13: +2.

Autopilot pass over all 13 chapters (walk straight at each objective and exit, stop for every conversation, cheapest excuse): every chapter completes except where a patrol catches it twice (the night shift). The theatre has a verified no-conversation route (side aisle → back row → exit, 26.6 s).
