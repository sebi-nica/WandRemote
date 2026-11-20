package com.example.wandremote.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroBiasCalibratorTest {
    /** Feeds [n] samples at 100 Hz. */
    private fun feed(c: GyroBiasCalibrator, n: Int, sample: (Int) -> Triple<Float, Float, Float>) {
        for (i in 0 until n) {
            val (x, y, z) = sample(i)
            c.add(i * 10_000_000L, x, y, z)
        }
    }

    @Test
    fun steadyReadingsGiveTheirMeanAsBias() {
        val c = GyroBiasCalibrator()
        feed(c, 301) { i -> Triple(0.01f + (if (i % 2 == 0) 0.001f else -0.001f), -0.02f, 0.005f) }
        assertTrue(c.isDone)
        val r = c.result() as GyroBiasCalibrator.Result.Success
        assertEquals(0.01f, r.bias.x, 1e-4f)
        assertEquals(-0.02f, r.bias.y, 1e-4f)
        assertEquals(0.005f, r.bias.z, 1e-4f)
    }

    @Test
    fun motionIsRejected() {
        val c = GyroBiasCalibrator()
        feed(c, 301) { i -> Triple(if (i % 20 < 10) 0.1f else -0.1f, 0f, 0f) }
        assertEquals(GyroBiasCalibrator.Result.Moved, c.result())
    }

    @Test
    fun singleBumpIsRejected() {
        val c = GyroBiasCalibrator()
        feed(c, 301) { i -> Triple(0f, 0f, if (i == 150) 0.5f else 0f) }
        assertEquals(GyroBiasCalibrator.Result.Moved, c.result())
    }

    @Test
    fun tooFewSamples() {
        val c = GyroBiasCalibrator()
        feed(c, 10) { Triple(0f, 0f, 0f) }
        assertEquals(GyroBiasCalibrator.Result.TooFewSamples, c.result())
    }

    @Test
    fun progressFollowsTimestamps() {
        val c = GyroBiasCalibrator(durationNs = 1_000_000_000L)
        c.add(5_000_000_000L, 0f, 0f, 0f)
        assertEquals(0f, c.progress, 0f)
        c.add(5_500_000_000L, 0f, 0f, 0f)
        assertEquals(0.5f, c.progress, 1e-6f)
        assertTrue(!c.isDone)
        c.add(6_000_000_000L, 0f, 0f, 0f)
        assertTrue(c.isDone)
    }
}
