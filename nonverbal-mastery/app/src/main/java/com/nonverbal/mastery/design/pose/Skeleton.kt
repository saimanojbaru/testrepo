package com.nonverbal.mastery.design.pose

import com.nonverbal.mastery.data.models.PoseParams

/** A torso cross-section: an ellipse in the horizontal plane of its frame. */
data class Section(val center: V3, val lateral: V3, val depth: V3, val halfWidth: Float, val halfDepth: Float) {
    fun lerp(o: Section, t: Float) = Section(
        center.lerp(o.center, t), lateral.lerp(o.lateral, t).normalized(), depth.lerp(o.depth, t).normalized(),
        halfWidth + (o.halfWidth - halfWidth) * t, halfDepth + (o.halfDepth - halfDepth) * t,
    )
}

data class Hand(val wrist: V3, val dir: V3, val thumb: V3, val shape: HandShape) {
    fun lerp(o: Hand, t: Float) = Hand(
        wrist.lerp(o.wrist, t), dir.lerp(o.dir, t).normalized(), thumb.lerp(o.thumb, t).normalized(),
        if (t < 0.5f) shape else o.shape,
    )
}

data class Foot(val heel: V3, val toe: V3, val ballInner: V3, val ballOuter: V3) {
    fun lerp(o: Foot, t: Float) = Foot(heel.lerp(o.heel, t), toe.lerp(o.toe, t), ballInner.lerp(o.ballInner, t), ballOuter.lerp(o.ballOuter, t))
}

data class Side(
    val shoulder: V3, val elbow: V3, val hand: Hand,
    val hip: V3, val knee: V3, val ankle: V3, val foot: Foot,
) {
    fun lerp(o: Side, t: Float) = Side(
        shoulder.lerp(o.shoulder, t), elbow.lerp(o.elbow, t), hand.lerp(o.hand, t),
        hip.lerp(o.hip, t), knee.lerp(o.knee, t), ankle.lerp(o.ankle, t), foot.lerp(o.foot, t),
    )
}

/** A solved pose: every joint in world space, ready to project from any angle. */
data class Skeleton(
    val pelvis: V3,
    val waist: V3,
    val chest: V3,
    val neck: V3,
    val head: V3,
    val headAxes: M3,
    val chestAxes: M3,
    val upperAxes: M3,
    val lowerAxes: M3,
    val left: Side,
    val right: Side,
    val sections: List<Section>,
    val seated: Boolean,
    val prop: String?,
    val yaw: Float,
) {
    fun lerp(o: Skeleton, t: Float): Skeleton = Skeleton(
        pelvis.lerp(o.pelvis, t), waist.lerp(o.waist, t), chest.lerp(o.chest, t), neck.lerp(o.neck, t), head.lerp(o.head, t),
        headAxes.lerp(o.headAxes, t), chestAxes.lerp(o.chestAxes, t), upperAxes.lerp(o.upperAxes, t), lowerAxes.lerp(o.lowerAxes, t),
        left.lerp(o.left, t), right.lerp(o.right, t),
        sections.zip(o.sections).map { (a, b) -> a.lerp(b, t) },
        if (t < 0.5f) seated else o.seated,
        if (t < 0.5f) prop else o.prop,
        yaw + (o.yaw - yaw) * t,
    )

    /** Named landmarks used by annotations. World space. */
    fun anchor(name: String): V3? {
        val h = headAxes
        fun headPt(x: Number, y: Number, z: Number) = head + h.apply(v(x, y, z))
        fun chestPt(x: Number, y: Number, z: Number) = chest + chestAxes.apply(v(x, y, z))
        fun upperPt(x: Number, y: Number, z: Number) = waist + upperAxes.apply(v(x, y, z))
        fun lowerPt(x: Number, y: Number, z: Number) = pelvis + lowerAxes.apply(v(x, y, z))
        fun handTip(s: Side) = s.hand.wrist + s.hand.dir * 6f
        fun footMid(s: Side) = s.foot.ballInner.lerp(s.foot.ballOuter, 0.5f)
        return when (name.lowercase()) {
            "head", "crown" -> headPt(0, 5, 0)
            "face" -> headPt(0, -1, 8.2)
            "eyes", "gaze", "brow" -> headPt(0, 2, 7.8)
            "mouth", "lips" -> headPt(0, -5, 8)
            "chin", "jaw" -> headPt(0, -8.5, 6)
            "neck" -> neck + h.apply(v(0, 4, 3.8))
            "throat", "neck-dimple", "suprasternal-notch" -> chestPt(0, 2.5, 8.5)
            "nape", "back-of-neck" -> neck + h.apply(v(0, 5, -4))
            "left-shoulder" -> left.shoulder + Y * 4.5f
            "right-shoulder" -> right.shoulder + Y * 4.5f
            "shoulders" -> left.shoulder.lerp(right.shoulder, 0.5f) + Y * 5f
            "chest" -> chestPt(0, -9, 11.5)
            "belly", "stomach", "abdomen" -> upperPt(0, 2, 10.5)
            "upper-back", "spine" -> chestPt(0, -10, -11)
            "lower-back" -> upperPt(0, 0, -10)
            "hips", "pelvis" -> lowerPt(0, 0, 10.5)
            "left-hip" -> lowerPt(16, 0, 0)
            "right-hip" -> lowerPt(-16, 0, 0)
            "left-arm" -> left.shoulder.lerp(left.elbow, 0.5f)
            "right-arm" -> right.shoulder.lerp(right.elbow, 0.5f)
            "arms" -> left.elbow.lerp(right.elbow, 0.5f)
            "left-elbow" -> left.elbow
            "right-elbow" -> right.elbow
            "left-wrist" -> left.hand.wrist
            "right-wrist" -> right.hand.wrist
            "left-hand" -> handTip(left)
            "right-hand" -> handTip(right)
            "hands" -> handTip(left).lerp(handTip(right), 0.5f)
            "left-knee" -> left.knee
            "right-knee" -> right.knee
            "knees" -> left.knee.lerp(right.knee, 0.5f)
            "lap" -> left.hip.lerp(left.knee, 0.5f).lerp(right.hip.lerp(right.knee, 0.5f), 0.5f) + Y * 7f
            "left-foot" -> footMid(left)
            "right-foot" -> footMid(right)
            "feet" -> footMid(left).lerp(footMid(right), 0.5f)
            "left-ankle" -> left.ankle
            "right-ankle" -> right.ankle
            "torso" -> upperPt(0, 14, 0)
            "weight", "center" -> pelvis
            else -> null
        }
    }

    companion object {
        private val Y = V3.Y
    }
}

/** Body measurements, cm. A neutral adult figure, deliberately unsexed. */
private object Body {
    const val UPPER_ARM = 30f
    const val FOREARM = 26f
    const val THIGH = 42f
    const val SHIN = 41f
    const val SHOULDER_HALF = 17.6f
    const val HIP_HALF = 9f
    const val STAND_PELVIS_Y = 95f
    val SEAT_PELVIS = v(0, 54, -4)
}

object PoseSolver {

    fun solve(p: PoseParams, viewOverride: String? = null): Skeleton {
        val armL = ArmPresets.get(p.armPoseL)
        val armR = ArmPresets.get(p.armPoseR)
        val legL = LegPresets.get(p.legStanceL)
        val legR = LegPresets.get(p.legStanceR)
        val seated = legL.seated && legR.seated

        // Pelvis. Weight shift moves it over the loaded leg and lifts that hip (contrapposto).
        val w = if (seated) 0f else p.weightShift.coerceIn(-1f, 1f)
        val pelvisTilt = p.hipHeight + 4.5f * w
        val curve = p.spineCurve
        // A slump is a rounding of the upper back, not a bow: the hips drift forward,
        // the lower trunk tips back a little, and the chest folds forward over it.
        val sway = if (seated) 0f else curve * 0.2f
        val pelvis = (if (seated) Body.SEAT_PELVIS else v(3.8f * w, Body.STAND_PELVIS_Y - 0.8f * kotlin.math.abs(w), 0)) + v(0, 0, sway)
        val lower = M3.rotZ(pelvisTilt)

        // Upper body: turn, then lean at the hips, then the chest's own slump and counter-tilt.
        val upper = M3.rotY(p.torsoTurn) * M3.rotX(p.spineLean - curve * 0.4f) * M3.rotZ(pelvisTilt * 0.35f)
        val shoulderTilt = -pelvisTilt * 0.85f
        val chestAxes = upper * M3.rotX(curve * 1.05f) * M3.rotZ(shoulderTilt)
        val ribAxes = upper * M3.rotX(curve * 0.45f)

        val waist = pelvis + upper.apply(v(0, 14, 0))
        val chest = waist + chestAxes.apply(v(0, 34, 0))
        val neck = chest + chestAxes.apply(v(0, 4, -1.5))

        // Head: follows the trunk partway, then the reader's own tilt, turn and pitch.
        val followPitch = p.spineLean * 0.4f + curve * 0.2f
        val headAxes = M3.rotY(p.torsoTurn + p.headTurn) *
            M3.rotX(followPitch + p.headPitch) *
            M3.rotZ(-p.headTilt)
        val head = neck + headAxes.apply(v(0, 11.6, 1.5))

        // Shoulders: height and protraction are in centimetres.
        fun shoulder(side: Float, height: Float, protraction: Float) =
            chest + chestAxes.apply(v(side * (Body.SHOULDER_HALF - protraction * 0.3f), height - protraction * 0.15f, protraction))
        val shoulderL = shoulder(1f, p.shoulderHeightL, p.shoulderProtractionL)
        val shoulderR = shoulder(-1f, p.shoulderHeightR, p.shoulderProtractionR)

        // Legs first: seated arm presets may reference the knees.
        val hipL = pelvis + lower.apply(v(Body.HIP_HALF, -4, 0))
        val hipR = pelvis + lower.apply(v(-Body.HIP_HALF, -4, 0))
        val legSolveL = solveLeg(hipL, legL, 1f, p.footAngleL)
        val legSolveR = solveLeg(hipR, legR, -1f, p.footAngleR)

        fun frameOf(preset: ArmPreset, shoulder: V3, knee: V3): Pair<V3, M3> = when (preset.frame) {
            Frame.SHOULDER -> shoulder to chestAxes
            Frame.CHEST -> chest to chestAxes
            Frame.HEAD -> head to headAxes
            Frame.PELVIS -> pelvis to lower
            Frame.KNEE -> knee to M3.IDENTITY
        }

        fun arm(preset: ArmPreset, side: Float, shoulder: V3, knee: V3, shapeId: String): Pair<V3, Hand> {
            val (origin, axes) = frameOf(preset, shoulder, knee)
            fun local(p0: V3) = if (side < 0) p0.mirrorX() else p0
            val target = origin + axes.apply(local(preset.wrist))
            val hintFrame = if (preset.frame == Frame.HEAD) chestAxes else axes
            val hint = hintFrame.apply(local(preset.elbowHint))
            val (elbow, wrist) = solveTwoBone(shoulder, target, Body.UPPER_ARM, Body.FOREARM, hint)
            val dir = preset.handDir?.let { axes.apply(local(it)).normalized() } ?: (wrist - elbow).normalized()
            val thumbRaw = preset.thumb?.let { axes.apply(local(it)) } ?: chestAxes.apply(V3.Z)
            val thumb = (thumbRaw - dir * (thumbRaw dot dir)).normalized().let { if (it.length() < 0.5f) V3.Z else it }
            return elbow to Hand(wrist, dir, thumb, HandShape.of(shapeId))
        }

        val (elbowL, handL) = arm(armL, 1f, shoulderL, legSolveL.knee, p.handShapeL)
        val (elbowR, handR) = arm(armR, -1f, shoulderR, legSolveR.knee, p.handShapeR)

        val sections = listOf(
            Section(pelvis + lower.apply(v(0, -3, 0)), lower.xAxis, lower.zAxis, 16.5f, 11.5f),
            Section(pelvis + lower.apply(v(0, 6, 0)), lower.xAxis, lower.zAxis, 15f, 10.5f),
            Section(waist, upper.xAxis, upper.zAxis, 13.2f, 10f),
            Section(waist + ribAxes.apply(v(0, 13, 0.5)), ribAxes.xAxis, ribAxes.zAxis, 14.6f, 11f),
            Section(waist + chestAxes.apply(v(0, 24, 0.5)), chestAxes.xAxis, chestAxes.zAxis, 15.6f, 11.5f),
            Section(waist + chestAxes.apply(v(0, 32, -0.5)), chestAxes.xAxis, chestAxes.zAxis,
                15.8f - (p.shoulderProtractionL + p.shoulderProtractionR) * 0.15f, 9.5f),
        )

        return Skeleton(
            pelvis = pelvis, waist = waist, chest = chest, neck = neck, head = head,
            headAxes = headAxes, chestAxes = chestAxes, upperAxes = upper, lowerAxes = lower,
            left = Side(shoulderL, elbowL, handL, hipL, legSolveL.knee, legSolveL.ankle, legSolveL.foot),
            right = Side(shoulderR, elbowR, handR, hipR, legSolveR.knee, legSolveR.ankle, legSolveR.foot),
            sections = sections,
            seated = seated,
            prop = p.prop ?: if (seated) "chair" else null,
            yaw = yawFor(viewOverride ?: p.view),
        )
    }

    private data class LegSolve(val knee: V3, val ankle: V3, val foot: Foot)

    private fun solveLeg(hip: V3, preset: LegPreset, side: Float, footAngle: Float): LegSolve {
        val target = if (side < 0) preset.ankle.mirrorX() else preset.ankle
        val hint = if (side < 0) preset.kneeHint.mirrorX() else preset.kneeHint
        val (knee, ankle) = solveTwoBone(hip, target, Body.THIGH, Body.SHIN, hint)

        // Foot: points forward, turned out by stance + authored angle (positive = outward).
        val out = (preset.toeOut + footAngle) * side
        val yawM = M3.rotY(out)
        val forward = yawM.apply(V3.Z)
        val lateral = yawM.apply(V3.X * side)
        val lift = preset.heelLift
        val heel = V3(ankle.x, (2.5f + lift).coerceAtLeast(2.5f), ankle.z) - forward * 5f
        val toeY = if (lift > 0f) 1.6f else 2f
        val toe = V3(ankle.x, toeY, ankle.z) + forward * 19f
        val ball = V3(ankle.x, if (lift > 0f) 2.4f else 2.6f, ankle.z) + forward * 12.5f
        return LegSolve(
            knee, ankle,
            Foot(heel, toe, ball - lateral * 3.2f, ball + lateral * 4.2f),
        )
    }
}
