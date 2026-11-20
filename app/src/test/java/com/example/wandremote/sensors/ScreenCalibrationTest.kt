package com.example.wandremote.sensors

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCalibrationTest {
    private val pi = PI.toFloat()

    // A screen about 40 degrees wide and 25 degrees tall, centered on yaw 0.
    private val tl = Aim(-0.35f, 0.22f)
    private val tr = Aim(0.35f, 0.22f)
    private val br = Aim(0.35f, -0.22f)
    private val bl = Aim(-0.35f, -0.22f)

    private fun cal(yawShift: Float = 0f) =
        ScreenCalibration.fromCorners(listOf(tl, tr, br, bl).map { Aim(wrapAngle(it.yaw + yawShift), it.pitch) })!!

    private fun assertPoint(x: Float, y: Float, p: ScreenPoint, eps: Float = 1e-3f) {
        assertEquals("x", x, p.x, eps)
        assertEquals("y", y, p.y, eps)
    }

    @Test
    fun cornersMapToUnitSquareCorners() {
        val c = cal()
        assertPoint(0f, 0f, c.map(tl))
        assertPoint(1f, 0f, c.map(tr))
        assertPoint(1f, 1f, c.map(br))
        assertPoint(0f, 1f, c.map(bl))
    }

    @Test
    fun centerMapsToMiddle() {
        assertPoint(0.5f, 0.5f, cal().map(Aim(0f, 0f)))
    }

    @Test
    fun aimingOutsideIsClamped() {
        val c = cal()
        assertPoint(0f, 0f, c.map(Aim(-1f, 1f)))
        assertPoint(1f, 1f, c.map(Aim(1f, -1f)))
        assertPoint(1f, 0.5f, c.map(Aim(0.9f, 0f)))
    }

    @Test
    fun skewedQuadRoundTrips() {
        // A trapezoid like aiming at a screen from the side: the right edge looks shorter than the left one.
        val a = Aim(-0.4f, 0.25f)
        val b = Aim(0.3f, 0.15f)
        val c = Aim(0.3f, -0.12f)
        val d = Aim(-0.4f, -0.25f)
        for (u in listOf(0.1f, 0.5f, 0.9f)) for (v in listOf(0.2f, 0.5f, 0.8f)) {
            val yaw = (1 - u) * (1 - v) * a.yaw + u * (1 - v) * b.yaw + u * v * c.yaw + (1 - u) * v * d.yaw
            val pitch = (1 - u) * (1 - v) * a.pitch + u * (1 - v) * b.pitch + u * v * c.pitch + (1 - u) * v * d.pitch
            assertPoint(u, v, ScreenCalibration.inverseBilinear(a, b, c, d, Aim(yaw, pitch)), 2e-3f)
        }
    }

    @Test
    fun worksAcrossTheYawWrap() {
        val c = cal(yawShift = pi) // screen centered on yaw = PI, so the corners straddle +-PI
        assertPoint(0f, 0f, c.map(Aim(wrapAngle(pi - 0.35f), 0.22f)))
        assertPoint(1f, 0f, c.map(Aim(wrapAngle(pi + 0.35f), 0.22f)))
        assertPoint(0.5f, 0.5f, c.map(Aim(pi, 0f)))
        assertPoint(1f, 1f, c.map(Aim(wrapAngle(pi + 0.35f), -0.22f)))
    }

    @Test
    fun degenerateCornersAreRejected() {
        val same = Aim(0.1f, 0.1f)
        assertNull(ScreenCalibration.fromCorners(listOf(same, same, same, same)))
        assertNull(ScreenCalibration.fromCorners(listOf(tl, tr, tr, tl))) // zero height
        assertNull(ScreenCalibration.fromCorners(listOf(tl, tr)))
        assertNotNull(ScreenCalibration.fromCorners(listOf(tl, tr, br, bl)))
    }

    @Test
    fun recenterShiftsYawOnly() {
        // Sensor restarted: the yaw zero moved by 0.5 rad, so aiming at the centre now reads yaw 0.5.
        val shifted = cal().recentered(Aim(0.5f, 0f))
        assertPoint(0.5f, 0.5f, shifted.map(Aim(0.5f, 0f)))
        assertPoint(0f, 0f, shifted.map(Aim(0.5f - 0.35f, 0.22f)))
        assertPoint(1f, 1f, shifted.map(Aim(0.5f + 0.35f, -0.22f)))
        assertTrue(shifted.isValid)
    }

    @Test
    fun recenterWorksAcrossTheWrap() {
        val shifted = cal().recentered(Aim(wrapAngle(pi + 0.1f), 0f)) // the centre now reads a yaw just past +PI
        assertPoint(0.5f, 0.5f, shifted.map(Aim(wrapAngle(pi + 0.1f), 0f)))
        assertPoint(1f, 0.5f, shifted.map(Aim(wrapAngle(pi + 0.1f + 0.35f), 0f)))
    }

    @Test
    fun smootherStartsAtFirstSampleAndConverges() {
        val s = PointSmoother(alpha = 0.5f)
        assertPoint(0.2f, 0.2f, s.next(ScreenPoint(0.2f, 0.2f)))
        assertPoint(0.6f, 0.6f, s.next(ScreenPoint(1f, 1f)))
    }
}
