package com.example.gpsspeedometer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpsLogicTest {

    @Test
    fun smoothSpeedShouldUseAdaptiveEmaAndStayPositive() {
        val filter = SpeedFilter()
        val values = listOf(0.0, 12.0, 18.0, 24.0, 32.0, 29.0)
        val smoothed = values.map { filter.update(it, 2.0, 1.0) }

        assertTrue(smoothed[0] >= 0.0)
        assertTrue(smoothed[1] > 0.0)
        assertTrue(smoothed.last() <= 32.0)
        assertTrue(smoothed.last() >= 20.0)
    }

    @Test
    fun invalidLocationShouldBeRejectedForDistance() {
        val first = DistanceSample(0.0, 0.0, 0L, 10.0f, 1.0f)
        val jump = DistanceSample(0.5, 0.0, 1_000L, 5.0f, 1.0f)
        val stale = DistanceSample(0.0, 0.0, 0L, 10.0f, 1.0f)

        assertFalse(DistanceValidator.isValidDelta(first, jump, 1_000L, 10.0f, 1.0f))
        assertFalse(DistanceValidator.isValidDelta(first, stale, 0L, 10.0f, 1.0f))
    }

    @Test
    fun validMovementShouldBeAccepted() {
        val previous = DistanceSample(0.0, 0.0, 0L, 5.0f, 1.0f)
        val current = DistanceSample(0.0001, 0.0, 1_000L, 5.0f, 1.0f)

        assertTrue(DistanceValidator.isValidDelta(previous, current, 1_000L, 10.0f, 1.0f))
        assertEquals(11.1, DistanceValidator.deltaMeters(previous, current), 3.0)
    }
}
