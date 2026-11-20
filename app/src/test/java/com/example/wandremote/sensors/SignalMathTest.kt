package com.example.wandremote.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SignalMathTest {
    @Test
    fun lowPassConvergesAndPassesThroughAtAlphaOne() {
        val out = FloatArray(3)
        val lp = LowPass3()
        lp.apply(0.5f, 10f, 0f, 0f, out)
        assertEquals(10f, out[0], 0f) // first sample primes the filter
        lp.apply(0.5f, 0f, 0f, 0f, out)
        assertEquals(5f, out[0], 1e-6f)
        lp.apply(1f, 3f, 4f, 5f, out)
        assertEquals(3f, out[0], 1e-6f)
        assertEquals(5f, out[2], 1e-6f)
    }

    @Test
    fun pointerStaysStillInsideDeadZone() {
        assertNull(PointerPipeline().step(0.005f, 0.005f, 0.01f, PointerTuning()))
    }

    @Test
    fun pointerMovesProportionallyToRate() {
        val tuning = PointerTuning(baseSensitivity = 1000f, acceleration = 0f, deadZone = 0f, smoothing = 1f)
        val d = PointerPipeline().step(1f, -0.5f, 0.01f, tuning)
        assertNotNull(d)
        assertEquals(10f, d!!.dx, 1e-4f)
        assertEquals(-5f, d.dy, 1e-4f)
    }

    @Test
    fun accelerationIncreasesGain() {
        val slow = PointerTuning(baseSensitivity = 1000f, acceleration = 0f, deadZone = 0f, smoothing = 1f)
        val fast = slow.copy(acceleration = 1f)
        val a = PointerPipeline().step(2f, 0f, 0.01f, slow)!!
        val b = PointerPipeline().step(2f, 0f, 0.01f, fast)!!
        assertEquals(a.dx * 3f, b.dx, 1e-3f)
    }
}
