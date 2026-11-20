package com.example.wandremote.sensors

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleBufferTest {
    private fun add(b: SampleBuffer, t: Long, v: Float) = b.add(t, floatArrayOf(v, v, v), floatArrayOf(-v, -v, -v))

    @Test
    fun overwritesOldestWhenFull() {
        val b = SampleBuffer(3)
        for (i in 1..5) add(b, i * 10L, i.toFloat())
        assertEquals(3, b.size)
        val w = b.window(nowNs = 50, windowNs = 1000)
        assertArrayEquals(longArrayOf(30, 40, 50), w.timesNs)
        assertEquals(3f, w.raw[0], 0f)
        assertEquals(-5f, w.filtered[6], 0f)
    }

    @Test
    fun windowDropsSamplesOlderThanTheWindow() {
        val b = SampleBuffer(10)
        for (i in 1..5) add(b, i * 10L, i.toFloat())
        val w = b.window(nowNs = 50, windowNs = 20)
        assertArrayEquals(longArrayOf(30, 40, 50), w.timesNs)
    }

    @Test
    fun emptyWindow() {
        assertEquals(0, SampleBuffer(4).window(100, 10).size)
    }
}
