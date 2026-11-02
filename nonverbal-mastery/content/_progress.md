# Content progress

_Last updated: Phase 2 (pose library) complete._

## Written

| Chapter | Tier | Words | ≈ Pages | Pose pairs |
|---|---|---|---|---|
| 1.1 The Baseline | Foundation | 5,028 | 11 | 6 |
| 1.11 Leakage & Pacifiers | Expert | 4,856 | 11 | 5 |
| 1.15 Synthesis — Building Your Own Protocol | Master | 4,566 | 10 | 5 |
| **Total** | | **14,450** | **≈32** | 16 |

Also written: the ethics statement *On Reading People* (1,205 words), crisis resources for 11 regions plus a worldwide directory, introductions for all six parts, and the full 90-chapter outline.

Run `./gradlew testDebugUnitTest --tests '*ContentContractTest*' -i` for current counts.

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

Every pose was reviewed visually in Paper and Ink (`./gradlew recordPaparazziDebug --tests '*PoseSheetTest*'` renders all of them, six to a page). The contract test checks presets, anchors (including companion anchors such as `b:head`), descriptions of at least 25 words, known themes, and that no foot goes through the floor.

To add a pose: append it to `poses.json`. Use `view` (`front`, `three-quarter`, `side`, `back`, or degrees) to choose the angle that makes the point visible. Most front-on poses that move along the depth axis, such as pointing or leaning, read better in profile. Then render the sheet and look at it.

Still missing, for later parts: carrying or holding a child; lying down; a seated cross-legged floor posture; a figure using a wheelchair or a cane (Part V needs these, and they should be drawn with care, not as props); and true facial expression, which the faceless figure cannot show by design. The face chapter (1.7) will need its own drawn plates.

## Read this before Phase 3: the page target and the contract disagree

The brief sets two targets that cannot both be met:

- **Contract:** each field has a range. Even with every field at its maximum (7 teach blocks × 400 words, a 400-word case study, 6 questions, 5 drills…), a chapter tops out around **6,400 words, about 14 pages**. Written at honest depth, the three chapters above land at ~4,800 words, about 11 pages.
- **Page target:** 22–25 pages per chapter, 2,000+ pages in total.

90 chapters × 11–14 pages ≈ **1,000–1,300 pages**. Reaching 2,000 means choosing one of:

1. **Widen the contract**: e.g. 2–3 case studies per chapter, 8–10 teach blocks, drill variations. This needs a schema change (`caseStudy` becomes a list), and the risk is padding.
2. **More chapters**: about 165 at the current depth. The brief allows 7–8 parts.
3. **Accept ~1,100–1,300 pages** as the honest size of the book.

Phase 1 kept the contract and did not pad. This needs a decision before Phase 3.

## Authoring workflow

1. Create `levels/<category-id>/NN-slug.json`. Copy an existing chapter for the shape.
2. Every teach block needs a `poseId` from `poses.json` and a `disconfirm`. If you need a pose that doesn't exist, add it to `poses.json` first. The test rejects unknown presets and anchors.
3. Use `*asterisks*` for italics: titles of works, and thoughts or speech quoted in prose. Keep them balanced within a paragraph.
4. Run the contract test. Fix every line it prints.
5. Fact-check every named study and number. Cite only real sources with full details in `furtherStudy`.

## Next

- **Phase 3:** Part VI, The Discipline (15 chapters). Its 6.6 *The Ethics of Reading People* should extend the ethics statement, not repeat it.
- Then Parts I (remaining 12), II (with the 2.13 safety gate: `safetyGate` must be set, and the test enforces ≥5 regions), III, IV and V.

## Needs a human before release

- Clinical review of Part II, and above all 2.13. The brief asks for 2.13 to be "clinician-reviewed in voice". An AI author can write in a clinician's voice, but that is not a clinician's review.
- Scholarly review of Part V (disability studies, queer theory, critical race studies).
- A second check of every citation and statistic: the Phase 12 editorial pass.
- Crisis numbers re-verified before each release; services change.
