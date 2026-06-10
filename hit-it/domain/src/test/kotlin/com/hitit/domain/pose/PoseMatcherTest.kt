package com.hitit.domain.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMatcherTest {

    /** A simple upright T-pose stick figure: straight arms (180° elbows) and legs (180° knees). */
    private fun tPose(): MutableList<PoseLandmark> {
        val lm = MutableList(PoseJoints.LANDMARK_COUNT) { PoseLandmark(0.5f, 0.5f, 0f, 1f) }
        lm[PoseJoints.LEFT_SHOULDER] = PoseLandmark(0.40f, 0.30f)
        lm[PoseJoints.RIGHT_SHOULDER] = PoseLandmark(0.60f, 0.30f)
        lm[PoseJoints.LEFT_ELBOW] = PoseLandmark(0.30f, 0.30f)
        lm[PoseJoints.RIGHT_ELBOW] = PoseLandmark(0.70f, 0.30f)
        lm[PoseJoints.LEFT_WRIST] = PoseLandmark(0.20f, 0.30f)
        lm[PoseJoints.RIGHT_WRIST] = PoseLandmark(0.80f, 0.30f)
        lm[PoseJoints.LEFT_HIP] = PoseLandmark(0.45f, 0.55f)
        lm[PoseJoints.RIGHT_HIP] = PoseLandmark(0.55f, 0.55f)
        lm[PoseJoints.LEFT_KNEE] = PoseLandmark(0.45f, 0.75f)
        lm[PoseJoints.RIGHT_KNEE] = PoseLandmark(0.55f, 0.75f)
        lm[PoseJoints.LEFT_ANKLE] = PoseLandmark(0.45f, 0.95f)
        lm[PoseJoints.RIGHT_ANKLE] = PoseLandmark(0.55f, 0.95f)
        return lm
    }

    @Test
    fun angleMathIsExact() {
        val straight = PoseMatcher.angleDeg(PoseLandmark(0f, 0f), PoseLandmark(1f, 0f), PoseLandmark(2f, 0f))
        assertEquals(180f, straight, 0.01f)
        val right = PoseMatcher.angleDeg(PoseLandmark(1f, 0f), PoseLandmark(0f, 0f), PoseLandmark(0f, 1f))
        assertEquals(90f, right, 0.01f)
    }

    @Test
    fun identicalPoseScoresPerfect() {
        val match = PoseMatcher.match(tPose(), tPose())!!
        assertEquals(100, match.score)
        assertEquals(8, match.anglesUsed)
        assertTrue(match.feedback.isEmpty())
    }

    @Test
    fun bentElbowDropsScoreAndNamesTheJoint() {
        val attempt = tPose()
        // Fold the left forearm: wrist moves from straight-out to straight-up -> elbow 180° -> 90°.
        attempt[PoseJoints.LEFT_WRIST] = PoseLandmark(0.30f, 0.20f)
        val match = PoseMatcher.match(tPose(), attempt)!!
        assertEquals(87, match.score) // 7 full credits + 1 zero credit -> 87.5 -> 87
        assertEquals("left elbow", match.feedback.first().label)
        assertTrue(match.feedback.first().hint.contains("straighten your left elbow"))
    }

    @Test
    fun oppositeDirectionGetsBendHint() {
        // Reference with a bent left elbow; attempt holds it straight -> "bend more".
        val reference = tPose()
        reference[PoseJoints.LEFT_WRIST] = PoseLandmark(0.30f, 0.20f)
        val match = PoseMatcher.match(reference, tPose())!!
        assertTrue(match.feedback.first().hint.contains("bend your left elbow"))
    }

    @Test
    fun lowVisibilitySkipsThatAngle() {
        val attempt = tPose()
        attempt[PoseJoints.LEFT_WRIST] = PoseLandmark(0.30f, 0.20f, visibility = 0.2f) // bent AND barely visible
        val match = PoseMatcher.match(tPose(), attempt)!!
        assertEquals(7, match.anglesUsed) // left elbow excluded, not punished
        assertEquals(100, match.score)
    }

    @Test
    fun tooFewVisibleAnglesRefusesToJudge() {
        val attempt = tPose().map { it.copy(visibility = 0.1f) }
        assertNull(PoseMatcher.match(tPose(), attempt))
        assertNull(PoseMatcher.match(tPose(), tPose().subList(0, 12))) // missing joints entirely
    }

    @Test
    fun smallWobbleKeepsFullCredit() {
        val attempt = tPose()
        // Nudge the wrist slightly: deviation stays under the 10° full-credit window.
        attempt[PoseJoints.LEFT_WRIST] = PoseLandmark(0.20f, 0.315f)
        val match = PoseMatcher.match(tPose(), attempt)!!
        assertEquals(100, match.score)
    }

    @Test
    fun verdictTiers() {
        assertTrue(PoseMatcher.verdict(97).contains("FRAME PERFECT"))
        assertTrue(PoseMatcher.verdict(82).contains("hold"))
        assertTrue(PoseMatcher.verdict(20).contains("bestie"))
    }
}
