package com.example.wandremote.desktop.input

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlenderOrbitTest {
    private class Fake : OrbitOutput {
        val log = mutableListOf<String>()
        var dx = 0
        var dy = 0
        var wheel = 0
        var middle = false
        override fun middleButton(down: Boolean) { middle = down; log += if (down) "down" else "up" }
        override fun moveBy(dx: Int, dy: Int) { this.dx += dx; this.dy += dy; log += "move" }
        override fun wheel(notches: Int) { wheel += notches; log += "wheel" }
    }

    private fun aboutY(deg: Double): FloatArray {
        val h = Math.toRadians(deg) / 2
        return floatArrayOf(cos(h).toFloat(), 0f, sin(h).toFloat(), 0f)
    }

    private fun aboutX(deg: Double): FloatArray {
        val h = Math.toRadians(deg) / 2
        return floatArrayOf(cos(h).toFloat(), sin(h).toFloat(), 0f, 0f)
    }

    private fun BlenderOrbit.feed(q: FloatArray, t: Long) = onOrientation(q[0], q[1], q[2], q[3], t)

    @Test
    fun yawBecomesHorizontalDragWithMiddleHeld() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutY(0.0), 0)
        orbit.feed(aboutY(1.0), 10_000_000)
        orbit.feed(aboutY(2.0), 20_000_000)
        assertTrue(out.middle)
        assertEquals("down", out.log.first())
        assertEquals(1, out.log.count { it == "down" })
        assertEquals(16, out.dx, "2 degrees at 8 px/degree")
        assertEquals(0, out.dy)
    }

    @Test
    fun pitchBecomesVerticalDrag() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutX(0.0), 0)
        orbit.feed(aboutX(-1.5), 10_000_000)
        assertEquals(-12, out.dy)
        assertEquals(0, out.dx)
    }

    @Test
    fun fractionsAccumulate() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutY(0.0), 0)
        for (i in 1..10) orbit.feed(aboutY(i * 0.1), i * 10_000_000L) // 0.8 px per step
        assertEquals(8, out.dx)
    }

    @Test
    fun releasesMiddleAfterIdle() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutY(0.0), 0)
        orbit.feed(aboutY(1.0), 10_000_000)
        orbit.poll(100_000_000)
        assertTrue(out.middle)
        orbit.poll(300_000_000)
        assertFalse(out.middle)
        assertEquals("up", out.log.last())
    }

    @Test
    fun jitterAndRecenterJumpsAreIgnored() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutY(0.0), 0)
        orbit.feed(aboutY(0.01), 10_000_000)
        assertTrue(out.log.isEmpty())
        orbit.feed(aboutY(90.0), 20_000_000)
        assertTrue(out.log.isEmpty())
    }

    @Test
    fun resetReleasesAndForgetsReference() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.feed(aboutY(0.0), 0)
        orbit.feed(aboutY(1.0), 10_000_000)
        orbit.reset()
        assertFalse(out.middle)
        val moves = out.log.count { it == "move" }
        orbit.feed(aboutY(5.0), 20_000_000) // first update after reset is only a new reference
        assertEquals(moves, out.log.count { it == "move" })
    }

    @Test
    fun zoomInScrollsWheelUp() {
        val out = Fake()
        val orbit = BlenderOrbit(out)
        orbit.onZoom(0.6f)
        assertEquals(0, out.wheel)
        orbit.onZoom(0.6f)
        assertEquals(-1, out.wheel)
        orbit.onZoom(-2.5f)
        assertEquals(1, out.wheel) // -1 + 2 (0.2 left over)
    }
}
