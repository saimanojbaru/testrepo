package com.hitit.domain.goal

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressTest {

    @Test
    fun fromValueClampsAndHandlesZeroTarget() {
        assertEquals(0.5f, GoalProgress.fromValue(2.0, 4.0), 0.0001f)
        assertEquals(1f, GoalProgress.fromValue(10.0, 4.0), 0.0001f)
        assertEquals(0f, GoalProgress.fromValue(1.0, 0.0), 0.0001f)
    }

    @Test
    fun fromCheckpointsClampsAndHandlesEmpty() {
        assertEquals(0.75f, GoalProgress.fromCheckpoints(3, 4), 0.0001f)
        assertEquals(0f, GoalProgress.fromCheckpoints(0, 0), 0.0001f)
        assertEquals(1f, GoalProgress.fromCheckpoints(4, 4), 0.0001f)
    }

    @Test
    fun percentRounds() {
        assertEquals(50, GoalProgress.percent(0.5f))
        assertEquals(0, GoalProgress.percent(-1f))
        assertEquals(100, GoalProgress.percent(1.5f))
    }
}
