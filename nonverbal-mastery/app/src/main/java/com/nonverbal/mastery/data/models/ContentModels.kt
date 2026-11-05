package com.nonverbal.mastery.data.models

import kotlinx.serialization.Serializable

/*
 * The content pack schema. Every type here mirrors the product specification field for
 * field. A handful of fields are additive (all optional, all defaulted) so that older packs
 * still decode: they are marked "additive" below with the reason they exist.
 */

@Serializable
data class ContentPack(
    val version: Int,
    val generatedAt: String,
    val categories: List<Category>,
    val poses: List<Pose>,
    /** Additive: crisis resources reachable from anywhere, not only from Level 2.13. */
    val crisisResources: SafetyGate? = null,
    /** Additive: the ethics statement travels with the content it governs. */
    val ethics: Essay? = null,
)

@Serializable
data class Category(
    val id: String,
    val title: String,
    val tagline: String,
    val accent: String,           // hex, per category
    val heroAsset: String?,       // filename in assets/heroes/, nullable
    val levels: List<Level>,
    /** Additive: the full table of contents, including chapters not yet written. */
    val outline: List<OutlineEntry> = emptyList(),
    /** Additive: one paragraph that opens the category screen. */
    val introduction: String = "",
)

@Serializable
data class OutlineEntry(
    val index: Int,
    val title: String,
)

@Serializable
data class Level(
    val id: String,
    val categoryId: String,
    val index: Int,
    val tier: Tier,
    val title: String,
    val hook: String,
    val why: String,
    val teach: List<TeachBlock>,
    val poseGallery: List<PosePair>,
    val caseStudy: String,
    val observe: List<String>,
    val drills: List<Drill>,
    val quiz: List<Question>,
    val reflection: Reflection,
    val failureMode: String,
    val furtherStudy: List<Reference>,
    val reviewPrompt: String,
    val estimatedMinutes: Int,
    val safetyGate: SafetyGate? = null,   // only set for 2.13
)

@Serializable
enum class Tier { FOUNDATION, INTERMEDIATE, ADVANCED, EXPERT, MASTER }

@Serializable
data class TeachBlock(
    val h: String,
    val p: String,
    val pullQuote: String? = null,
    val poseId: String? = null,
    /** Additive: the block's disconfirming condition, set as a marginal note. */
    val disconfirm: String? = null,
    /** Additive: an optional drawn plate (see DiagramKind). */
    val diagram: String? = null,
)

@Serializable
data class PosePair(
    val doPoseId: String,
    val dontPoseId: String,
    val doCaption: String,
    val dontCaption: String,
    val annotation: String,
)

@Serializable
data class Drill(
    val name: String,
    val minutes: Int,
    val steps: List<String>,
)

@Serializable
data class Question(
    val q: String,
    val options: List<String>,
    val answer: Int,
    val explain: String,
    val isMisconceptionTest: Boolean = false,
    val isScenario: Boolean = false,
)

@Serializable
data class Reflection(
    val prompt: String,
    val guidance: String,
    val minCharacters: Int = 150,
)

@Serializable
data class Reference(
    val work: String,
    val author: String,
    val note: String? = null,
)

@Serializable
data class SafetyGate(
    val resourceTitle: String,
    val resourceBody: String,
    val regions: List<CrisisResource>,
)

@Serializable
data class CrisisResource(
    val region: String,
    val org: String,
    val contact: String,
)

@Serializable
data class Pose(
    val id: String,
    val desc: String,
    val params: PoseParams,
    val annotations: List<PoseAnnotation> = emptyList(),
    /** Additive: a short plate title for the library ("Arms folded, loose"). */
    val title: String = "",
    /** Additive: library grouping ("stance", "seated", "self-touch", ...). */
    val theme: String = "general",
    /** Additive: other figures in the same scene (greetings, proxemics, groups). Anchors use "b:", "c:", "d:". */
    val companions: List<SceneFigure> = emptyList(),
    /** Additive: draw the main figure's line of gaze. */
    val showGaze: Boolean = false,
    /** Additive: who the main figure is: "woman", "older-woman", "older-man", "hijab", or a cast name. Default: a man. */
    val look: String? = null,
)

/** Additive: a second (third, fourth) figure placed in the same scene as the main one. */
@Serializable
data class SceneFigure(
    val params: PoseParams,
    /** Floor position in cm relative to the main figure: +x is the main figure's left, +z its front. */
    val x: Float = 0f,
    val z: Float = 0f,
    /** Which way this figure faces, degrees: 0 the same way as the main figure, 180 toward it. */
    val facing: Float = 180f,
    /** 1 for an adult; about 0.6 for a young child. */
    val scale: Float = 1f,
    /** Additive: lift off the floor in cm, for a child carried on a hip or a person lying on a bed. */
    val y: Float = 0f,
    /** Additive: tip about the figure's own left-right axis, degrees. -90 lays it on its back, head toward its own rear. */
    val pitch: Float = 0f,
    /** Additive: who this figure is: "woman", "man", "older-woman", "older-man", "girl", "boy", "hijab", or a cast name. */
    val look: String? = null,
)

@Serializable
data class PoseParams(
    val headTilt: Float = 0f,
    val headTurn: Float = 0f,
    val spineLean: Float = 0f,
    val shoulderHeightL: Float = 0f,
    val shoulderHeightR: Float = 0f,
    val shoulderProtractionL: Float = 0f,
    val shoulderProtractionR: Float = 0f,
    val armPoseL: String,
    val armPoseR: String,
    val handShapeL: String,
    val handShapeR: String,
    val hipHeight: Float = 0f,
    val legStanceL: String,
    val legStanceR: String,
    val weightShift: Float = 0f,
    val footAngleL: Float = 0f,
    val footAngleR: Float = 0f,
    /** Additive: camera yaw. "front", "three-quarter", "side", "back", or degrees as text. */
    val view: String = "front",
    /** Additive: chin down (+) or up (-), degrees. Gaze lowering needs it. */
    val headPitch: Float = 0f,
    /** Additive: thoracic flexion, degrees. A slump is not a lean. */
    val spineCurve: Float = 0f,
    /** Additive: upper body yaw relative to the hips, degrees. Positive turns to the figure's left. */
    val torsoTurn: Float = 0f,
    /** Additive: a quiet prop drawn behind the figure: "chair", "stool", "table", "desk", "laptop", "lectern", "bench", "bed", "long_table" (head of a meeting table), or "none" for no chair under a seated figure. */
    val prop: String? = null,
    /** Additive: breath into the upper chest, -1 (emptied) to 1 (full). */
    val chestBreath: Float = 0f,
    /** Additive: breath into the belly, -1 to 1. */
    val bellyBreath: Float = 0f,
)

@Serializable
data class PoseAnnotation(
    val at: String,       // e.g. "left-shoulder"
    val text: String,
    val color: String,    // "accent-warm" | "accent-cool" | "do-green" | "don't-red"
)

/** Additive: a long-form essay, used for the ethics statement. */
@Serializable
data class Essay(
    val title: String,
    val subtitle: String = "",
    val sections: List<EssaySection>,
)

@Serializable
data class EssaySection(
    val heading: String? = null,
    val paragraphs: List<String>,
)
