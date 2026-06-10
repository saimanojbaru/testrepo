package com.hitit.domain.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoseCodecTest {

    @Test
    fun roundTripPreservesLandmarks() {
        val pose = listOf(
            PoseLandmark(0.12345f, 0.5f, -0.25f, 0.9f),
            PoseLandmark(1f, 0f, 0f, 0.5f),
            PoseLandmark(0.33333f, 0.66666f, 0.00001f, 1f),
        )
        val decoded = PoseCodec.decode(PoseCodec.encode(pose))!!
        assertEquals(pose.size, decoded.size)
        pose.zip(decoded).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.0001f)
            assertEquals(a.y, b.y, 0.0001f)
            assertEquals(a.z, b.z, 0.0001f)
            assertEquals(a.visibility, b.visibility, 0.0001f)
        }
    }

    @Test
    fun decodeRejectsGarbage() {
        assertNull(PoseCodec.decode(""))
        assertNull(PoseCodec.decode("not,a,pose"))
        assertNull(PoseCodec.decode("0.1,0.2,0.3,0.4|0.5,oops,0.6,0.7"))
        assertNull(PoseCodec.decode("0.1,0.2,0.3")) // wrong arity
    }

    @Test
    fun holdTrackerAccruesOnlyDuringGoodRuns() {
        val tracker = HoldTracker(thresholdScore = 80)
        tracker.feed(0, 90)      // first good sample — run starts, nothing accrued yet
        tracker.feed(1_000, 90)  // +1000
        tracker.feed(2_000, 85)  // +1000
        assertEquals(2_000, tracker.currentHoldMs)
        tracker.feed(3_000, 40)  // dip — current resets, best kept
        assertEquals(0, tracker.currentHoldMs)
        assertEquals(2_000, tracker.bestHoldMs)
        tracker.feed(4_000, 95)  // recovery sample — run restarts
        tracker.feed(5_000, 95)  // +1000
        assertEquals(1_000, tracker.currentHoldMs)
        assertEquals(2_000, tracker.bestHoldMs)
    }

    @Test
    fun holdTrackerResetClearsEverything() {
        val tracker = HoldTracker()
        tracker.feed(0, 90)
        tracker.feed(1_000, 90)
        tracker.reset()
        assertEquals(0, tracker.currentHoldMs)
        assertEquals(0, tracker.bestHoldMs)
    }
}
