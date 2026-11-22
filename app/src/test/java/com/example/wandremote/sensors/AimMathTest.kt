package com.example.wandremote.sensors

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
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
}
