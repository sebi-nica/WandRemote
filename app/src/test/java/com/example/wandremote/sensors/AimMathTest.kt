package com.example.wandremote.sensors

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AimMathTest {
    private val pi = PI.toFloat()

    private fun aboutZ(theta: Float) = aimFromQuaternion(cos(theta / 2), 0f, 0f, sin(theta / 2))
    private fun aboutX(theta: Float) = aimFromQuaternion(cos(theta / 2), sin(theta / 2), 0f, 0f)

    @Test
    fun identityPointsNorthLevel() {
        val a = aimFromQuaternion(1f, 0f, 0f, 0f)
        assertEquals(0f, a.yaw, 1e-5f)
        assertEquals(0f, a.pitch, 1e-5f)
    }

    @Test
    fun turningRightIncreasesYaw() {
        assertEquals(pi / 2, aboutZ(-pi / 2).yaw, 1e-5f) // clockwise seen from above -> east
        assertEquals(-pi / 2, aboutZ(pi / 2).yaw, 1e-5f)
    }

    @Test
    fun tiltingUpIncreasesPitchAndKeepsYaw() {
        val a = aboutX(0.3f)
        assertEquals(0.3f, a.pitch, 1e-5f)
        assertEquals(0f, a.yaw, 1e-5f)
    }

    @Test
    fun rollAboutThePointingAxisDoesNotMoveTheAim() {
        val t = 0.8f
        val a = aimFromQuaternion(cos(t / 2), 0f, sin(t / 2), 0f) // rotation about device Y = twisting the wrist
        assertEquals(0f, a.yaw, 1e-5f)
        assertEquals(0f, a.pitch, 1e-5f)
    }

    @Test
    fun wrapAngleStaysInRange() {
        assertEquals(-pi + 0.2f, wrapAngle(pi + 0.2f), 1e-5f)
        assertEquals(pi - 0.2f, wrapAngle(-pi - 0.2f), 1e-5f)
        assertEquals(0.5f, wrapAngle(0.5f + 4 * pi), 1e-4f)
    }

    @Test
    fun unwrapperIsContinuousAcrossPi() {
        val u = YawUnwrapper()
        assertEquals(3.0f, u.unwrap(3.0f), 1e-5f)
        assertEquals(3.2f, u.unwrap(3.2f - 2 * pi), 1e-4f) // wrapped sample just past +PI
        assertEquals(3.4f, u.unwrap(3.4f - 2 * pi), 1e-4f)
        assertEquals(3.1f, u.unwrap(3.1f), 1e-4f)
    }

    @Test
    fun averagerHandlesSamplesEitherSideOfPi() {
        val avg = AimAverager()
        avg.add(Aim(pi - 0.1f, 0.2f))
        avg.add(Aim(-pi + 0.1f, 0.4f))
        val r = avg.result()!!
        assertEquals(pi, abs(r.yaw), 1e-4f)
        assertEquals(0.3f, r.pitch, 1e-5f)
        assertNull(AimAverager().result())
    }

    @Test
    fun historyAveragesWindowBeforeTouch() {
        val h = AimHistory()
        for (i in 0..60) h.add(i * 10_000_000L, Aim(if (i < 45) 0.1f else 0.9f, 0f)) // jolt after t = 450 ms
        val r = h.averageBefore(nowNs = 500_000_000L)!!
        assertEquals(0.1f, r.yaw, 1e-5f) // the window ends 80 ms before the touch, so before the jolt
    }

    @Test
    fun historyWithoutSamplesIsNull() {
        assertNull(AimHistory().averageBefore(1_000_000_000L))
    }
}
