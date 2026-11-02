# Content progress

_Last updated: Phase 3 (Part VI, The Discipline) complete._

## Written

18 of 90 chapters: all of Part VI, plus the three Phase 1 chapters from Part I.

| Chapter | Tier | Words | ≈ Pages |
|---|---|---|---|
| 1.1 The Baseline | Foundation | 5,133 | 11 |
| 1.11 Leakage & Pacifiers | Expert | 4,951 | 11 |
| 1.15 Synthesis — Building Your Own Protocol | Master | 4,680 | 10 |
| 6.1 Building Your Baseline Journal | Foundation | 4,566 | 10 |
| 6.2 The 60-Second Scan | Foundation | 4,479 | 10 |
| 6.3 Video Review of Yourself | Foundation | 4,519 | 10 |
| 6.4 Feedback Loops & Honest Mirrors | Intermediate | 4,521 | 10 |
| 6.5 Calibration — Testing Hypotheses in the Field | Intermediate | 4,707 | 10 |
| 6.6 The Ethics of Reading People | Intermediate | 4,998 | 11 |
| 6.7 Teaching Others — The Test of True Understanding | Advanced | 4,587 | 10 |
| 6.8 Plateaus & Deliberate Practice | Advanced | 4,449 | 10 |
| 6.9 Building a Personal Protocol | Advanced | 4,569 | 10 |
| 6.10 Reading Groups in Real Time | Expert | 4,502 | 10 |
| 6.11 Cross-Cultural Calibration | Expert | 4,508 | 10 |
| 6.12 High-Stakes Environments | Expert | 4,466 | 10 |
| 6.13 Reading Yourself Under Stress | Master | 4,550 | 10 |
| 6.14 Synthesis & Improvisation | Master | 4,525 | 10 |
| 6.15 Mastery — The Quiet Body | Master | 4,435 | 10 |
| **Total** | | **83,145** | **≈185** |

Part VI alone is 68,381 words (≈150 pages). The pack is 620 KB raw and 195 KB gzipped, so the full book should fit the 2 MB budget gzipped.

Also written: the ethics statement *On Reading People* (1,205 words), crisis resources for 11 regions plus a worldwide directory, introductions for all six parts, and the full 90-chapter outline.

Run `./gradlew testDebugUnitTest --tests '*ContentContractTest*' -i` for current counts.

## How Part VI was written

- **Hooks first.** All 15 hooks were written in one pass and each was judged against its chapter's scope before any chapter was drafted. Each one earned its place.
- **Depth.** Every chapter follows the full contract. Teach blocks run 260–350 words, against an average of about 290 in the Phase 1 chapters. There are no stubs.
- **Falsifiability.** Every teach block states what would show it wrong. Most of these are tests the reader can run on their own ledger.
- **Poses.** Every chapter uses only poses that already exist in the library, so none were added. Two chapters use drawn diagrams: 6.10 uses the group-attention diagram and 6.13 the breathing wave.
- **Not repeating Part I.** Part VI does not re-teach the baseline (1.1), leakage (1.11) or the six steps (1.15), and calls back to them where needed.
  - 6.9 is about making the 1.15 steps run under pressure.
  - 6.8 refers to Kahneman and Klein as introduced in 1.15 rather than introducing them again.
- **Not repeating the ethics essay.** 6.6 adds method the essay lacks: contextual integrity, the flows test, hold, ask or act, a bias audit, emotion-recognition technology as a mirror, records, and hard cases.
- **Deception still has one chapter.** 6.7 (Kassin & Fong 1999) and 6.12 (Kassin et al. 2003, 2010) cite interrogation research only as warnings about how presumption and training create false confidence. 1.14 remains the only chapter on deception itself.

## Quiz rule added in Phase 3

The right answer had been visibly the longest option in most questions written so far, Phase 1 included, so a reader could pass a quiz by always picking the longest. Options in 1.1, 1.11, 1.15 and 6.1–6.6 were rewritten, and the contract test now enforces two rules:

1. The right answer may be at most 1.35 times the length of the longest wrong option.
2. The right answer may be the longest option in at most half of a chapter's questions.

The test also rejects words broken by a line-wrap hyphen ("de- escalation").

## Boundaries for chapters not yet written

Part VI now covers ground that some later chapters approach from another angle. Keep them distinct.

| Unwritten chapter | Already covered in | What the later chapter should do instead |
|---|---|---|
| 1.12 Reading the Room | 6.10 Reading Groups: F-formations, gaze convergence, turn-taking, visual dominance, mimicry and its limits, sampling a room | Foundations-level atmosphere and entering a room. Do not re-teach 6.10's research. |
| 1.13 Cultural Variance | 6.11 Cross-Cultural Calibration: Elfenbein & Ambady, Friesen's display rules, Matsumoto 2008, Tsai, WEIRD, Akechi 2013, Masuda 2008, Morris's gestures, cultural humility | The evidence debate on universality (for example Ekman versus Barrett, Jack et al. 2012). Leave the method to 6.11 and the map to 5.11. |
| 2.2 Breath as the Master Signal, 2.14 Interoception, 2.15 Early-Warning System | 6.13 Reading Yourself Under Stress: Garfinkel 2015, affect labelling, emotion differentiation, reappraisal (Jamieson, Brooks), slow breathing (Zaccaro, Balban), Levant | Part II should go deeper on the science and the clinical side. Do not present 6.13's studies as new. |
| 4.11 Interview Signals That Actually Matter | 6.6, on records and structured interviews (Schmidt & Hunter 1998) | Can build on 6.6 and refer back to it. |

Part VI also points forward to chapters that must exist when they are written:

- 3.1, the side-by-side principle (from 6.15).
- 3.3, head nods (from 6.11).
- Part II, on interoception and clinical concerns (from 6.13).
- Part V's working map of cultures (from 6.11).

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
- true facial expression, which the faceless figure cannot show by design. The face chapter (1.7) will need its own drawn plates.

## Still open: the page target and the contract disagree

The brief sets two targets that cannot both be met.

- **The contract.** Each field has a range. With every field at its maximum, a chapter tops out around 6,400 words, about 14 pages. Written at honest depth, the 18 chapters so far average about 4,600 words, about 10 pages.
- **The page target.** 22–25 pages per chapter, 2,000+ pages in total.

At the current depth, 90 chapters come to about **415,000 words, roughly 900–1,000 pages**. Reaching 2,000 pages needs one of three things:

1. **Widen the contract.** For example 2–3 case studies per chapter, 8–10 teach blocks, or drill variations. This needs a schema change (`caseStudy` becomes a list), and it risks padding.
2. **Add chapters.** About 180 would be needed at the current depth. The brief allows 7–8 parts.
3. **Accept about 900–1,000 pages** as the honest size of the book.

Phases 1 and 3 kept to the contract and did not pad.

## Authoring workflow

1. Create `levels/<category-id>/NN-slug.json`. Copy an existing chapter for the shape.
2. Every teach block needs a `poseId` from `poses.json` and a `disconfirm`. If you need a pose that does not exist, add it to `poses.json` first. The test rejects unknown presets and anchors.
3. Use `*asterisks*` for italics: titles of works, and thoughts or speech quoted in prose. Keep them balanced within a paragraph.
4. Write quiz options of similar length, so that the wrong ones are full, plausible sentences.
5. Run the contract test, and fix every line it prints.
6. Fact-check every named study and number. Cite only real sources, with full details in `furtherStudy`.

The JSON files are the source of truth. Edit them directly.

## Next

- Part I: the remaining 12 chapters, respecting the boundaries above.
- Part II, with the 2.13 safety gate: `safetyGate` must be set, and the test requires at least 5 crisis regions.
- Parts III, IV and V.

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
- **Crisis numbers** re-verified before each release, because services change.
