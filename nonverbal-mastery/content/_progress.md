# Content progress

_Last updated: Part I, Foundations, complete. Parts I and VI are written in full._

## Written

30 of 90 chapters: all of Part I and all of Part VI. That is 135,084 words, about 300 pages.

| Part I | Tier | Words | | Part VI | Tier | Words |
|---|---|---|---|---|---|---|
| 1.1 The Baseline | Foundation | 5,133 | | 6.1 Building Your Baseline Journal | Foundation | 4,566 |
| 1.2 The 1/25th Second | Foundation | 4,411 | | 6.2 The 60-Second Scan | Foundation | 4,479 |
| 1.3 Proxemics | Foundation | 4,317 | | 6.3 Video Review of Yourself | Foundation | 4,519 |
| 1.4 The Gaze | Intermediate | 4,415 | | 6.4 Feedback Loops & Honest Mirrors | Intermediate | 4,521 |
| 1.5 The Spine as Status | Intermediate | 4,298 | | 6.5 Calibration | Intermediate | 4,729 |
| 1.6 Clusters & Congruence | Intermediate | 4,399 | | 6.6 The Ethics of Reading People | Intermediate | 4,998 |
| 1.7 The Face | Advanced | 4,345 | | 6.7 Teaching Others | Advanced | 4,587 |
| 1.8 Voice as Body Language | Advanced | 4,256 | | 6.8 Plateaus & Deliberate Practice | Advanced | 4,449 |
| 1.9 The Taxonomy of Touch | Advanced | 4,344 | | 6.9 Building a Personal Protocol | Advanced | 4,569 |
| 1.10 Space & Territory | Expert | 4,384 | | 6.10 Reading Groups in Real Time | Expert | 4,502 |
| 1.11 Leakage & Pacifiers | Expert | 4,951 | | 6.11 Cross-Cultural Calibration | Expert | 4,508 |
| 1.12 Reading the Room | Expert | 4,324 | | 6.12 High-Stakes Environments | Expert | 4,466 |
| 1.13 Cultural Variance | Master | 4,238 | | 6.13 Reading Yourself Under Stress | Master | 4,550 |
| 1.14 Deception | Master | 4,186 | | 6.14 Synthesis & Improvisation | Master | 4,525 |
| 1.15 Synthesis | Master | 4,680 | | 6.15 Mastery — The Quiet Body | Master | 4,435 |
| **Part I** | | **66,681** | | **Part VI** | | **68,403** |

The pack is 947 KB raw and 300 KB gzipped. At this rate the full book will be about 900 KB gzipped, inside the 2 MB budget.

Also written: the ethics statement *On Reading People* (1,205 words), crisis resources for 11 regions plus a worldwide directory, introductions for all six parts, and the full 90-chapter outline.

Run `./gradlew testDebugUnitTest --tests '*ContentContractTest*' -i` for current counts.

## How Parts I and VI were written

- **Hooks first.** For each part, all hooks were written in one pass and judged against the chapter's scope before any chapter was drafted.
- **Depth.** Every chapter follows the full contract, with teach blocks of 240–350 words. There are no stubs.
- **Falsifiability.** Every teach block states what would show it wrong, usually as a test the reader can run.
- **Balanced quizzes.** Every quiz passes the length rules below.
- **No repetition across chapters.** Later chapters call back to earlier ones rather than re-teaching them. 1.6 goes deeper than the cluster step in 1.15 without repeating it. 1.12 covers a room's atmosphere and leaves group mechanics to 6.10. 1.13 covers the universality debate and leaves the method to 6.11 and the map to 5.11.
- **Deception has one chapter.** 1.14 is the only chapter on lying. 1.2 explicitly leaves lie detection to it. 1.4 mentions the gaze-aversion belief only to point there. 6.7 and 6.12 use interrogation research only as warnings.

## Face plates (added for Part I)

The body figures stay faceless by design. Faces appear only on dedicated plates: neutral, ungendered sketches drawn from FACS action units (the standard coding of facial muscle movements), each with a TalkBack description. There are seven plates:

- `face-smiles`, two smiles;
- `face-brows`, three brows;
- `face-lids`, the lids;
- `face-mouths`, three mouths;
- `face-prototypes`, six posed prototypes;
- `face-micro`, a microexpression time line;
- `face-gaze`, gaze direction.

They are used in 1.2, 1.4, 1.6 and 1.7. Each is drawn from a `FaceSpec` in `FacePlates.kt`, so a new plate is a few lines of code. `DiagramSheetTest` renders every plate in Paper and Ink for review, and the contract test rejects unknown diagram ids.

This settles the earlier open question about faceless figures, but the choice is reversible. If you would rather have faces on the figures themselves, the same `drawFace` could be placed on the pose skeleton's head.

## Quiz rules

The right answer had been visibly the longest option in most early questions, so a reader could pass a quiz by always picking the longest. The contract test now enforces two rules:

1. The right answer may be at most 1.35 times the length of the longest wrong option.
2. The right answer may be the longest option in at most half of a chapter's questions.

It also rejects words broken by a line-wrap hyphen. The stub-marker check now matches whole words only; it had been flagging the surname Todorov.

## Boundaries for chapters not yet written

Parts I and VI cover ground that later chapters approach from other angles. Keep them distinct, and call back rather than re-teach.

| Unwritten chapter | Already covered in | What the later chapter should do |
|---|---|---|
| 2.2 Breath; 2.14 Interoception; 2.15 Early-Warning System | 6.13: Garfinkel 2015, affect labelling, reappraisal, slow breathing (Zaccaro, Balban), Levant | Go deeper on the science and the clinical side. Do not present 6.13's studies as new. |
| 2.3 The "I'm Fine" Mask | 1.8 (a voice that changes before the words do); 1.2 (Ekman and Friesen's patient film) | Build the clinical picture. Refer back for the voice and the history of the idea. |
| 2.8 Trauma & the Freeze Response | 1.9 (freezing when touched is not consent) | Explain freezing in depth. |
| 3.1 The Side-by-Side Principle | 1.3 (Sommer's seating, angles); 6.15 | Develop the principle for men's friendships and hard conversations. |
| 3.3 The Head Nod | 6.11 (gesture dialects) | The full treatment of nods. |
| 3.4 Personal Space Between Men | 1.3 (Hall, Kennedy, Sorokowska, Argyle & Dean) | What is specific to men. Do not repeat the general research. |
| 3.5 The Bro Hug; 3.6 Touch Between Male Friends | 1.9 (Heslin, Suvilehto, consent) | What is specific to men's touch. |
| 3.7 Silence as Communication | 1.8 (pauses, Goldman-Eisler, Clark & Fox Tree) | Silence as a message, not as planning. |
| 3.10 Consent as Nonverbal Negotiation | 1.9 (only a clear yes grants permission) | The full treatment. Keep the same hard line. |
| 4.2 Camera Height, Framing & Perceived Status | 1.5 (Witkower & Tracy on head tilt) | Apply it to cameras. |
| 4.3 Audio as Body Language | 1.8 (McAleer, Puts, Klofstad, Kraus) | Microphones, calls and recordings. |
| 4.5 Power Posing | 1.5 (one paragraph: Ranehill 2015, Carney) | The full evidence. |
| 4.7 Meeting Room Geography | 1.3 (Sommer); 1.10 (Brown & Baer home ground) | Meeting rooms specifically. |
| 4.11 Interview Signals | 6.6 (records, Schmidt & Hunter) | Build on 6.6. |
| 5.1 Neurodivergence | 1.4 (Trevisan on eye contact); 1.8 (stammering) | The full picture. |
| 5.6 Race & the Reading of Black Male Bodies | 6.6 (Hugenberg & Bodenhausen); 1.14 (the Othello error) | The full treatment. |
| 5.11 Cultural Variance — A Working Map | 1.13 (evidence); 6.11 (method) | The map itself, with its limits. |
| 5.12 Religion, Modesty & Touch | 1.9 (a declined handshake as a matter of faith) | The full treatment. |

## Pose library (Phase 2: done)

125 poses, each with a plate title, a theme, a prose description written to stand without the image, parameters and up to four annotations.

| Theme | Poses | | Theme | Poses |
|---|---|---|---|---|
| Stance | 11 | | Walking | 6 |
| Posture | 10 | | Breath | 5 |
| Arms | 10 | | Grief | 5 |
| Self-touch | 13 | | At work | 8 |
| Head & gaze | 10 | | Greeting & culture | 9 |
| Gesture | 9 | | Together (2–3 figures) | 12 |
| Seated | 17 | | | |

Every pose was reviewed visually in Paper and Ink. `./gradlew recordPaparazziDebug --tests '*PoseSheetTest*'` renders all of them, six to a page. The contract test checks:

- presets and anchors, including companion anchors such as `b:head`;
- descriptions of at least 25 words;
- that every theme is a known one;
- that no foot goes through the floor.

To add a pose, append it to `poses.json`. Use `view` (`front`, `three-quarter`, `side`, `back`, or degrees) to pick the angle that makes the point visible. Most front-on poses that move along the depth axis, such as pointing or leaning, read better in profile. Then render the sheet and look at it.

Still missing, for later parts:

- carrying or holding a child;
- lying down;
- sitting cross-legged on the floor;
- a figure using a wheelchair or a cane. Part V needs these, and they should be drawn with care, not as props.

## Still open: the page target and the contract disagree

The brief sets two targets that cannot both be met.

- **The contract.** Each field has a range. With every field at its maximum, a chapter tops out around 6,400 words, about 14 pages. Written at honest depth, the 30 chapters so far average about 4,500 words, about 10 pages.
- **The page target.** 22–25 pages per chapter, 2,000+ pages in total.

At the current depth, 90 chapters come to about **405,000 words, roughly 900 pages**. Reaching 2,000 pages needs one of three things:

1. **Widen the contract.** For example 2–3 case studies per chapter, 8–10 teach blocks, or drill variations. This needs a schema change (`caseStudy` becomes a list), and it risks padding.
2. **Add chapters.** About 180 would be needed at the current depth. The brief allows 7–8 parts.
3. **Accept about 900 pages** as the honest size of the book.

Every chapter so far has kept to the contract without padding. Sixty chapters remain, so this is still the cheapest moment to decide.

## Authoring workflow

1. Create `levels/<category-id>/NN-slug.json`. Copy an existing chapter for the shape.
2. Every teach block needs a `poseId` from `poses.json` and a `disconfirm`. If you need a pose that does not exist, add it to `poses.json` first. The test rejects unknown presets and anchors.
3. Use `*asterisks*` for italics: titles of works, and thoughts or speech quoted in prose. Keep them balanced within a paragraph.
4. Write quiz options of similar length, so that the wrong ones are full, plausible sentences.
5. Run the contract test, and fix every line it prints.
6. Fact-check every named study and number. Cite only real sources, with full details in `furtherStudy`.

The JSON files are the source of truth. Edit them directly.

## Next

- Part II, The Inner World, with the 2.13 safety gate: `safetyGate` must be set, and the test requires at least 5 crisis regions. Part II is the most sensitive part of the book. No chapter may diagnose; every chapter teaches recognition and response.
- Then Parts III, IV and V, respecting the boundaries above.

## Needs a human before release

- **Clinical review** of Part II, above all 2.13. The brief asks for 2.13 to be "clinician-reviewed in voice". An AI author can write in a clinician's voice, but that is not a clinician's review.
- **Scholarly review** of Part V (disability studies, queer theory, critical race studies).
- **A second check of every citation and statistic** (the Phase 12 editorial pass). For Part VI, check these figures first, because they were stated from memory:
  - Macnamara 2014 percentages by domain.
  - Haynes 2009 mortality and complication rates.
  - Lally 2010 (66 days, and the range).
  - Newton 1990 (3 of 120 songs).
  - Stivers 2009 (half-second gaps; the Japanese and Danish extremes).
  - Engel 2022 (use of force down by about a quarter).
  - Tannenbaum & Cerasoli 2013 (about a quarter).
  - Rhue 2018.
  - Dickerson & Kemeny 2004 (more than 200 studies).
  - Schnall 2008.
  - Akechi 2013.
  - Friesen's 1972 display-rule study.
  - Balban 2023.

  For Part I, check these first:
  - Porter & ten Brinke 2008 (number of expressions, share of participants).
  - Yan et al. 2013 (author list, the half-second definition).
  - Sorokowska 2017 (distances by country; the link with climate).
  - Binetti 2016 (preferred durations).
  - Kleinke 1986 and Argyle & Cook 1976 (gaze proportions).
  - Tracy & Matsumoto 2008 (number of nations).
  - Hall, Coats & LeBeau 2005 (number of studies).
  - Nair 2015 (which measures differed).
  - Jones & Yarbrough 1985 (meaning clusters).
  - Suvilehto 2015 (sample, countries).
  - Fernández-Dols & Ruiz-Belda 1995.
  - Gendron 2014 (the emotion-word condition).
  - Cowen 2021 (about 70 percent shared).
  - Totterdell 2000.
  - Serota 2010 (how lying is concentrated in a few people).
  - Blair, Levine & Shaw 2010 (about three in four).
  - The 2003 National Research Council and 2013 GAO conclusions.
- **Crisis numbers** re-verified before each release, because services change.
