package com.nonverbal.mastery.design.pose

/**
 * Preset libraries. Arm and leg presets name where the hand or foot goes, not joint
 * angles; two-bone IK finds the elbow and knee. Every preset is written for the figure's
 * LEFT side (+x) and mirrored for the right.
 */

/** Which body landmark an arm target is measured from, and in whose orientation. */
enum class Frame { SHOULDER, CHEST, HEAD, PELVIS, KNEE, LEFT_KNEE }

data class ArmPreset(
    val frame: Frame,
    val wrist: V3,
    val elbowHint: V3,
    /** Direction the hand extends from the wrist, in the frame's orientation. Null: follow the forearm. */
    val handDir: V3? = null,
    /** Thumb side of the hand, in the frame's orientation. Null: thumb forward. */
    val thumb: V3? = null,
)

object ArmPresets {
    val all: Map<String, ArmPreset> = mapOf(
        // Hanging
        "hang" to ArmPreset(Frame.SHOULDER, v(3.5, -53.5, 2.5), v(0.3, 0, -1)),
        "hang_tight" to ArmPreset(Frame.SHOULDER, v(-0.5, -52, 1.5), v(0, 0, -1)),
        "hang_open" to ArmPreset(Frame.SHOULDER, v(6.5, -52.5, 4.5), v(0.5, 0, -1), thumb = v(1, 0, 0.3)),
        "hang_forward" to ArmPreset(Frame.SHOULDER, v(2, -50, 9), v(0.2, 0, -1)),
        // Crossed arms: one over, one under; loose and tight variants
        "cross_over" to ArmPreset(Frame.CHEST, v(-10.5, -18, 12.5), v(1, -0.7, 0.1), handDir = v(-0.45, 0.75, -0.35)),
        "cross_under" to ArmPreset(Frame.CHEST, v(-10, -20.5, 9), v(1, -0.7, 0), handDir = v(-0.5, 0.5, -0.7)),
        "cross_tight_over" to ArmPreset(Frame.CHEST, v(-13, -11, 11), v(1, -0.35, 0.3), handDir = v(-0.35, 0.55, -0.5)),
        "cross_tight_under" to ArmPreset(Frame.CHEST, v(-12, -14.5, 8), v(1, -0.35, 0.2), handDir = v(-0.4, 0.6, -0.6)),
        // Pockets, hips, behind the back
        "pocket" to ArmPreset(Frame.PELVIS, v(13.5, -5, 5), v(0.6, 0, -0.9)),
        "hips" to ArmPreset(Frame.PELVIS, v(15.5, 5, 3), v(1, 0.15, -0.35), handDir = v(-0.35, -0.55, 0.75)),
        "behind_back" to ArmPreset(Frame.PELVIS, v(4, 4, -12), v(0.9, 0, -0.45), handDir = v(-1, 0, 0)),
        // Self-touch: neck, face, self-holding
        "neck_front" to ArmPreset(Frame.CHEST, v(6, -6, 11), v(0.6, -1, -0.2), handDir = v(-0.5, 0.86, -0.05), thumb = v(0, 0, 1)),
        "neck_back" to ArmPreset(Frame.HEAD, v(10, -8, -3), v(0.8, 0.8, 0.6), handDir = v(-0.95, -0.25, -0.45)),
        "face_cheek" to ArmPreset(Frame.HEAD, v(8.5, -15, 6.5), v(0.3, -1, 0.25), handDir = v(-0.25, 0.86, 0.35)),
        "chin_rest" to ArmPreset(Frame.HEAD, v(1.5, -18.5, 8.5), v(0.25, -1, 0.6), handDir = v(-0.1, 0.95, 0.15)),
        "mouth_cover" to ArmPreset(Frame.HEAD, v(5.5, -12, 12), v(0.4, -1, 0.4), handDir = v(-0.75, 0.5, 0.1)),
        "self_hold" to ArmPreset(Frame.CHEST, v(-13, -15, 9), v(0.8, -1, 0.1), handDir = v(-0.65, 0.1, -0.55)),
        "self_wrap" to ArmPreset(Frame.CHEST, v(-11, -23, 9.5), v(0.8, -0.6, 0.3), handDir = v(-0.5, 0.25, -0.8)),
        "chest_hand" to ArmPreset(Frame.CHEST, v(-1, -9, 12.5), v(0.8, -1, -0.1), handDir = v(-0.75, 0.6, 0), thumb = v(0, 1, 0)),
        // Hands together in front
        "wring" to ArmPreset(Frame.PELVIS, v(3.5, 13, 15), v(1, -0.35, -0.6), handDir = v(-1, 0.1, 0.25)),
        "clasp_low" to ArmPreset(Frame.PELVIS, v(3.5, -3, 11.5), v(0.6, 0, -1), handDir = v(-1, -0.25, 0.15)),
        "steeple" to ArmPreset(Frame.CHEST, v(6, -22, 21), v(0.8, -0.8, -0.3), handDir = v(-0.35, 0.92, 0.15)),
        // Gesture
        "gesture_open" to ArmPreset(Frame.SHOULDER, v(10, -31, 24), v(0.3, -0.6, -0.8), handDir = v(0.35, 0.05, 1), thumb = v(1, 0.2, 0)),
        "gesture_mid" to ArmPreset(Frame.SHOULDER, v(3, -29, 26), v(0.2, -0.7, -0.7), handDir = v(-0.15, 0.25, 1)),
        "point_forward" to ArmPreset(Frame.SHOULDER, v(1, -6, 53), v(0.3, -1, 0)),
        "phone_hold" to ArmPreset(Frame.SHOULDER, v(-4, -31, 24), v(0.3, -1, -0.4), handDir = v(-0.2, 0.55, 0.8)),
        // Seated
        "thighs_rest" to ArmPreset(Frame.KNEE, v(0, 7.5, -13), v(0.3, 0, -1), handDir = v(0, -0.2, 1)),
        "forearms_knees" to ArmPreset(Frame.KNEE, v(-7, 4, 5), v(0.4, -0.2, -1), handDir = v(-0.35, -0.85, 0.35)),
        "armrest_drape" to ArmPreset(Frame.SHOULDER, v(21, -36, -16), v(1, 0.3, -0.2)),
        "lap_clasp" to ArmPreset(Frame.PELVIS, v(3.5, 6, 22), v(0.8, -0.2, -0.8), handDir = v(-1, 0, 0.3)),
        "grip_seat" to ArmPreset(Frame.PELVIS, v(17, -6, 13), v(0.9, 0.2, -0.5), handDir = v(0.1, -0.9, 0.35)),
        "kneel_rest" to ArmPreset(Frame.PELVIS, v(9, -16, 10), v(0.4, 0, -1), handDir = v(-0.1, -1, 0.15)),
        "thigh_slide" to ArmPreset(Frame.KNEE, v(0, 7, -8), v(0.3, 0.1, -1), handDir = v(0, -0.3, 1)),
        "knee_hug" to ArmPreset(Frame.LEFT_KNEE, v(5, -6, 7), v(0.6, -0.4, -0.5), handDir = v(-1, 0, 0.15)),
        "phone_low" to ArmPreset(Frame.PELVIS, v(3, 15, 25), v(0.6, -0.5, -0.6), handDir = v(-0.2, 0.5, 0.85)),
        "hands_behind_head" to ArmPreset(Frame.HEAD, v(8, 1, -8), v(1, 0.35, 0.2), handDir = v(-1, 0.1, -0.1)),
        // At a table (table top 74 cm; seated pelvis 54 cm)
        "table_rest" to ArmPreset(Frame.PELVIS, v(9, 21, 38), v(0.7, -0.25, -0.6), handDir = v(-0.7, -0.05, 0.6)),
        "steeple_table" to ArmPreset(Frame.PELVIS, v(6, 26, 30), v(0.8, -0.5, -0.4), handDir = v(-0.35, 0.9, 0.2)),
        "write" to ArmPreset(Frame.PELVIS, v(7, 21, 36), v(0.6, -0.3, -0.7), handDir = v(-0.3, -0.25, 0.9)),
        // Gesture and signalling
        "wave" to ArmPreset(Frame.SHOULDER, v(11, 20, 6), v(1, -0.6, -0.2), handDir = v(0.15, 1, 0.1), thumb = v(0, 0, 1)),
        "raise_hand" to ArmPreset(Frame.SHOULDER, v(4, 50, 5), v(0.7, 0, -0.3), handDir = v(0, 1, 0.05)),
        "acknowledge" to ArmPreset(Frame.SHOULDER, v(6, 6, 18), v(0.8, -1, -0.2), handDir = v(0, 1, 0.15), thumb = v(1, 0, 0)),
        "arms_wide" to ArmPreset(Frame.SHOULDER, v(30, -22, 22), v(0.3, -0.5, -1), handDir = v(0.6, 0, 0.8), thumb = v(0, 1, 0)),
        "palm_down_low" to ArmPreset(Frame.SHOULDER, v(3, -33, 29), v(0.3, -0.6, -0.8), handDir = v(-0.2, -0.15, 1), thumb = v(-1, 0, 0)),
        "shrug_palms" to ArmPreset(Frame.SHOULDER, v(14, -36, 18), v(0.2, -1, -0.6), handDir = v(0.55, 0.05, 0.85), thumb = v(1, 0.2, 0)),
        "chop" to ArmPreset(Frame.SHOULDER, v(1, -24, 30), v(0.3, -0.7, -0.6), handDir = v(-0.05, 0.35, 1), thumb = v(0, 1, 0)),
        "watch_check" to ArmPreset(Frame.CHEST, v(-3, -11, 26), v(0.6, -1, -0.3), handDir = v(-1, 0.1, 0.15)),
        "palms_together" to ArmPreset(Frame.CHEST, v(2.5, -13, 16), v(1, -0.8, -0.2), handDir = v(0, 1, 0.05), thumb = v(0, 0, 1)),
        "palms_together_high" to ArmPreset(Frame.CHEST, v(2.5, 3, 15), v(1, -0.9, -0.1), handDir = v(0, 1, 0.05), thumb = v(0, 0, 1)),
        "lectern_hands" to ArmPreset(Frame.PELVIS, v(19, 15, 28), v(0.8, -0.3, -0.5), handDir = v(-0.1, -0.3, 1)),
        // Self-touch, extended
        "eyes_cover" to ArmPreset(Frame.HEAD, v(6, -5, 10), v(0.4, -1, 0.3), handDir = v(-0.75, 0.6, -0.05)),
        "brow_rub" to ArmPreset(Frame.HEAD, v(5, -2, 12), v(0.4, -1, 0.3), handDir = v(-0.5, 0.8, -0.2)),
        "head_scratch" to ArmPreset(Frame.HEAD, v(9, 1, 0), v(1, 0.5, 0.2), handDir = v(-0.45, 0.8, -0.2)),
        "collar" to ArmPreset(Frame.CHEST, v(12, -4, 8), v(0.7, -1, 0), handDir = v(-0.35, 0.9, 0)),
        "forearm_stroke" to ArmPreset(Frame.CHEST, v(-11, -40, 9), v(0.6, -1, 0.2), handDir = v(-0.85, -0.45, -0.1)),
        "grip_other_wrist" to ArmPreset(Frame.PELVIS, v(-2, -2, 13), v(0.6, 0, -1), handDir = v(-1, -0.15, 0)),
        "clasp_low_held" to ArmPreset(Frame.PELVIS, v(4, -7, 12), v(0.6, 0, -1), handDir = v(-0.8, -0.5, 0.1)),
        "head_in_hands" to ArmPreset(Frame.HEAD, v(6, -15, 9), v(0.3, -1, 0.4), handDir = v(-0.25, 0.85, -0.4)),
        // Walking
        "swing_forward" to ArmPreset(Frame.SHOULDER, v(1.5, -50, 15), v(0.2, 0, -1)),
        "swing_back" to ArmPreset(Frame.SHOULDER, v(2, -50, -15), v(0.2, 0, -1)),
        // With another person
        "handshake" to ArmPreset(Frame.SHOULDER, v(-15, -36, 38), v(0.6, -1, -0.2), handDir = v(-0.2, 0, 1), thumb = v(0, 1, 0)),
        "hug_high" to ArmPreset(Frame.CHEST, v(-8, 0, 30), v(1, 0.1, 0.2), handDir = v(-0.9, 0, -0.4)),
        "hug_low" to ArmPreset(Frame.CHEST, v(6, -22, 32), v(1, -0.2, 0), handDir = v(-1, 0, -0.2)),
        "around_shoulders" to ArmPreset(Frame.SHOULDER, v(42, -3, -5), v(0.2, 0.6, -1), handDir = v(0.4, -0.6, 0.6)),
        "on_shoulder" to ArmPreset(Frame.SHOULDER, v(30, -6, 6), v(0.2, -1, -0.3), handDir = v(0.6, -0.5, 0.4)),
        // Greeting between friends
        "fist_bump" to ArmPreset(Frame.SHOULDER, v(-15, -22, 40), v(0.5, -1, -0.3), handDir = v(-0.1, 0.05, 1), thumb = v(0, 1, 0)),
        "clasp_chest" to ArmPreset(Frame.SHOULDER, v(-14, -24, 24), v(0.7, -1, -0.3), handDir = v(-0.35, 0.6, 0.7), thumb = v(0, 1, 0)),
        // Holding and carrying
        "cradle_under" to ArmPreset(Frame.CHEST, v(-12, -22, 22), v(1, -0.7, -0.1), handDir = v(-0.9, 0.1, 0.2)),
        "baby_back" to ArmPreset(Frame.CHEST, v(-6, -8, 22), v(1, -0.5, -0.2), handDir = v(-0.9, 0.2, 0.3)),
        "cradle_support" to ArmPreset(Frame.CHEST, v(-2, -18, 13), v(0.8, -0.8, -0.2), handDir = v(-0.6, 0.2, 0.6)),
        "hip_carry" to ArmPreset(Frame.PELVIS, v(30, 2, 12), v(0.3, -0.5, -1), handDir = v(0.2, 0.1, -1)),
        "reach_up_short" to ArmPreset(Frame.SHOULDER, v(4, 20, 6), v(0.8, -0.2, -0.3), handDir = v(-0.2, 1, 0.1)),
        "hold_hand_low" to ArmPreset(Frame.SHOULDER, v(9, -52, 6), v(0.4, 0, -1), handDir = v(0.2, -0.95, 0.15)),
        // Reaching to someone lying down, and working with the hands at a bench
        "hold_forward_low" to ArmPreset(Frame.SHOULDER, v(-18, -38, 50), v(0.5, -1, -0.2), handDir = v(-0.1, -0.35, 1)),
        "work_low" to ArmPreset(Frame.PELVIS, v(9, -10, 34), v(0.7, -0.4, -0.6), handDir = v(-0.4, -0.35, 0.85)),
    )

    fun get(id: String): ArmPreset = all[id] ?: all.getValue("hang")
}

enum class HandShape {
    RELAXED, OPEN, FLAT, FIST, LOOSE_FIST, POINT, GRIP, HIDDEN, TUCKED, CLASP, STEEPLE, CUP, PINCH, RUB, PALM_UP;

    companion object {
        fun of(id: String): HandShape = when (id.lowercase().replace('-', '_')) {
            "relaxed", "natural" -> RELAXED
            "open", "open_palm", "palm_forward" -> OPEN
            "palm_up" -> PALM_UP
            "flat", "palm_flat" -> FLAT
            "fist" -> FIST
            "loose_fist", "curled" -> LOOSE_FIST
            "point" -> POINT
            "grip" -> GRIP
            "hidden", "pocket" -> HIDDEN
            "tucked" -> TUCKED
            "clasp", "clasped", "interlaced" -> CLASP
            "steeple" -> STEEPLE
            "cup", "cupped" -> CUP
            "pinch" -> PINCH
            "rub", "stroke" -> RUB
            else -> RELAXED
        }

        val ids = listOf(
            "relaxed", "open", "palm_up", "flat", "fist", "loose_fist", "point", "grip",
            "hidden", "tucked", "clasp", "steeple", "cup", "pinch", "rub",
        )
    }
}

/** What the pelvis rests on. */
enum class Base { STAND, SIT, KNEEL, SQUAT }

data class LegPreset(
    /** Ankle position in world space (feet stay planted when the pelvis moves). */
    val ankle: V3,
    val kneeHint: V3,
    val base: Base = Base.STAND,
    /** How far the heel is lifted off the floor, cm. */
    val heelLift: Float = 0f,
    /** Extra toe-out for this stance, degrees. */
    val toeOut: Float = 8f,
    /** Shin on the floor with the foot pointing back, as in kneeling. */
    val footBack: Boolean = false,
) {
    val seated: Boolean get() = base == Base.SIT
}

object LegPresets {
    val all: Map<String, LegPreset> = mapOf(
        "stand" to LegPreset(v(9.5, 8.5, 0), v(0, 0, 1)),
        "stand_wide" to LegPreset(v(18, 8.5, 0), v(0.2, 0, 1), toeOut = 12f),
        "stand_narrow" to LegPreset(v(5.5, 8.5, 1), v(0, 0, 1), toeOut = 4f),
        "stand_weight" to LegPreset(v(7, 8.5, 0), v(0, 0, 1)),
        "stand_relaxed_out" to LegPreset(v(13, 9, 9), v(0.15, 0, 1), toeOut = 18f),
        "stand_step_back" to LegPreset(v(10, 11, -15), v(0, 0, 1), heelLift = 5f),
        "stand_cross" to LegPreset(v(-5, 8.5, 5), v(0, 0, 1), toeOut = -4f),
        "stand_heel_up" to LegPreset(v(9.5, 11.5, 2), v(0, 0, 1), heelLift = 6f),
        "walk_forward" to LegPreset(v(8.5, 8.5, 26), v(0, 0, 1)),
        "walk_back" to LegPreset(v(8.5, 10.5, -24), v(0, 0, 1), heelLift = 6f),
        "sit" to LegPreset(v(11, 8.5, 40), v(0, 0.7, 0.7), base = Base.SIT),
        "sit_wide" to LegPreset(v(22, 8.5, 37), v(0.65, 0.55, 0.6), base = Base.SIT, toeOut = 18f),
        "sit_ankles_locked" to LegPreset(v(-1.5, 9.5, 18), v(0.1, 0.7, 0.7), base = Base.SIT, toeOut = -6f),
        "sit_ankles_locked_under" to LegPreset(v(2, 8.5, 20), v(0.1, 0.7, 0.7), base = Base.SIT, toeOut = -2f),
        "sit_heel_raised" to LegPreset(v(11, 14, 35), v(0, 0.7, 0.7), base = Base.SIT, heelLift = 8f),
        "sit_tucked" to LegPreset(v(10, 9, 19), v(0, 0.7, 0.7), base = Base.SIT),
        "sit_extended" to LegPreset(v(4, 7, 74), v(0, 1, 0.25), base = Base.SIT, toeOut = 14f),
        "sit_extended_cross" to LegPreset(v(-3, 9, 76), v(0, 1, 0.25), base = Base.SIT, toeOut = 10f),
        "sit_cross_over" to LegPreset(v(-8, 27, 44), v(0.15, 1, 0.55), base = Base.SIT, heelLift = 6f, toeOut = 4f),
        "sit_ankle_on_knee" to LegPreset(v(-5, 52, 33), v(1, 0.55, 0.35), base = Base.SIT, toeOut = 60f),
        "sit_forward_lean" to LegPreset(v(13, 8.5, 36), v(0.15, 0.7, 0.7), base = Base.SIT),
        "sit_knee_up" to LegPreset(v(11, 52, 14), v(0.15, 1, 0.35), base = Base.SIT, toeOut = 4f),
        "sit_perch" to LegPreset(v(13, 8.5, 28), v(0.1, 0.7, 0.7), base = Base.SIT, heelLift = 3f),
        "stand_forward_step" to LegPreset(v(10, 8.5, 18), v(0, 0, 1)),
        "walk_long_forward" to LegPreset(v(8.5, 8.5, 34), v(0, 0, 1)),
        "walk_long_back" to LegPreset(v(8.5, 12.5, -31), v(0, 0, 1), heelLift = 9f),
        "walk_short_forward" to LegPreset(v(8.5, 8.5, 15), v(0, 0, 1)),
        "walk_short_back" to LegPreset(v(8.5, 9.5, -13), v(0, 0, 1), heelLift = 4f),
        "kneel" to LegPreset(v(9, 6, -40), v(0, -0.3, 1), base = Base.KNEEL, footBack = true, toeOut = 0f),
        "kneel_down" to LegPreset(v(9, 6, -38), v(0, -0.3, 1), base = Base.KNEEL, footBack = true, toeOut = 0f),
        "kneel_up" to LegPreset(v(11, 8.5, 40), v(0.1, 1, 0.5), base = Base.KNEEL),
        "squat" to LegPreset(v(13, 8.5, 5), v(0.45, 1, 0.9), base = Base.SQUAT, toeOut = 22f),
    )

    fun get(id: String): LegPreset = all[id] ?: all.getValue("stand")
}

/** Named camera yaws. A pose may also give degrees directly, e.g. "60". */
fun yawFor(view: String): Float = when (view.lowercase()) {
    "front" -> 0f
    "three-quarter", "three_quarter", "3/4" -> 35f
    "three-quarter-left" -> -35f
    "side", "profile" -> 90f
    "side-left" -> -90f
    "back" -> 180f
    else -> view.toFloatOrNull() ?: 0f
}
