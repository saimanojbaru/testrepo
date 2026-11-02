# Content progress

_Last updated: Phase 1 (engine) complete._

## Written

| Chapter | Tier | Words | ≈ Pages | Pose pairs |
|---|---|---|---|---|
| 1.1 The Baseline | Foundation | 5,028 | 11 | 6 |
| 1.11 Leakage & Pacifiers | Expert | 4,856 | 11 | 5 |
| 1.15 Synthesis — Building Your Own Protocol | Master | 4,566 | 10 | 5 |
| **Total** | | **14,450** | **≈32** | 16 |

Also written: the ethics statement *On Reading People* (1,205 words), crisis resources for 11 regions plus a worldwide directory, introductions for all six parts, and the full 90-chapter outline.

Run `./gradlew testDebugUnitTest --tests '*ContentContractTest*' -i` for current counts.

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

- **Phase 2:** expand `poses.json` from 30 to ~120. Needed first: walking, two-figure and group scenes (proxemics, greetings, side-by-side), breathing states, grief postures, video-call framing, cultural variants. The leg-preset library needs ~10 more stances for the ~30 the brief asks for.
- **Phase 3:** Part VI, The Discipline (15 chapters). Its 6.6 *The Ethics of Reading People* should extend the ethics statement, not repeat it.
- Then Parts I (remaining 12), II (with the 2.13 safety gate: `safetyGate` must be set, and the test enforces ≥5 regions), III, IV and V.

## Needs a human before release

- Clinical review of Part II, and above all 2.13. The brief asks for 2.13 to be "clinician-reviewed in voice". An AI author can write in a clinician's voice, but that is not a clinician's review.
- Scholarly review of Part V (disability studies, queer theory, critical race studies).
- A second check of every citation and statistic: the Phase 12 editorial pass.
- Crisis numbers re-verified before each release; services change.
