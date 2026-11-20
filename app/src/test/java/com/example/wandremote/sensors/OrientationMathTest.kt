package com.example.wandremote.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrientationMathTest {
    private val eps = 1e-9

    private fun about(axis: Int, degrees: Double): Quat = when (axis) {
        0 -> Quat.fromAxisAngle(1.0, 0.0, 0.0, Math.toRadians(degrees))
        1 -> Quat.fromAxisAngle(0.0, 1.0, 0.0, Math.toRadians(degrees))
        else -> Quat.fromAxisAngle(0.0, 0.0, 1.0, Math.toRadians(degrees))
    }

    private fun assertSameRotation(a: Quat, b: Quat) {
        assertEquals(0.0, a.angleTo(b), 1e-6)
    }

    private fun deg(r: Double) = Math.toDegrees(r)

    @Test
    fun identityRelativeToItselfIsIdentity() {
        val q = about(0, 33.0) * about(2, -70.0)
        val rel = relativeTo(q, q)
        assertSameRotation(rel, Quat.IDENTITY)
        assertEquals(1.0, abs(rel.w), 1e-9)
    }

    @Test
    fun relativeIsReferenceInverseTimesCurrent() {
        val ref = about(2, 90.0)
        val cur = about(2, 100.0)
        assertSameRotation(relativeTo(ref, cur), about(2, 10.0))
    }

    @Test
    fun eulerOfSingleAxisRotations() {
        val p = about(0, 30.0).toEuler()
        assertEquals(30.0, deg(p.pitch), 1e-6)
        assertEquals(0.0, deg(p.roll), 1e-6)
        assertEquals(0.0, deg(p.yaw), 1e-6)
        val r = about(1, -40.0).toEuler()
        assertEquals(-40.0, deg(r.roll), 1e-6)
        assertEquals(0.0, deg(r.pitch), 1e-6)
        val y = about(2, 120.0).toEuler()
        assertEquals(120.0, deg(y.yaw), 1e-6)
        assertEquals(0.0, deg(y.pitch), 1e-6)
    }

    @Test
    fun eulerRoundTrip() {
        for (pitch in listOf(-80.0, -20.0, 0.0, 45.0, 85.0)) {
            for (roll in listOf(-150.0, -10.0, 70.0, 170.0)) {
                for (yaw in listOf(-170.0, 0.0, 95.0)) {
                    val e = Euler(Math.toRadians(pitch), Math.toRadians(roll), Math.toRadians(yaw))
                    val q = Quat.fromEuler(e)
                    val back = q.toEuler()
                    assertEquals(pitch, deg(back.pitch), 1e-6)
                    assertEquals(roll, deg(back.roll), 1e-6)
                    assertEquals(yaw, deg(back.yaw), 1e-6)
                    assertSameRotation(q, Quat.fromEuler(back))
                }
            }
        }
    }

    @Test
    fun straightUpPitchRoundTripsAsSameRotation() {
        val q = about(2, 20.0) * about(0, 90.0) * about(1, 30.0)
        assertSameRotation(q, Quat.fromEuler(q.toEuler()))
    }

    @Test
    fun noLockReturnsNormalizedInput() {
        val q = about(0, 25.0) * about(2, 40.0)
        assertSameRotation(AxisLock().apply(q), q)
    }

    @Test
    fun lockingPitchKeepsRollAndYaw() {
        val q = Quat.fromEuler(Euler(Math.toRadians(30.0), Math.toRadians(20.0), Math.toRadians(50.0)))
        val e = AxisLock(pitch = true).apply(q).toEuler()
        assertEquals(0.0, deg(e.pitch), 1e-6)
        assertEquals(20.0, deg(e.roll), 1e-6)
        assertEquals(50.0, deg(e.yaw), 1e-6)
    }

    @Test
    fun lockingYawKeepsPitchAndRoll() {
        val q = Quat.fromEuler(Euler(Math.toRadians(-30.0), Math.toRadians(10.0), Math.toRadians(60.0)))
        val e = AxisLock(yaw = true).apply(q).toEuler()
        assertEquals(-30.0, deg(e.pitch), 1e-6)
        assertEquals(10.0, deg(e.roll), 1e-6)
        assertEquals(0.0, deg(e.yaw), 1e-6)
    }

    @Test
    fun lockingTheOnlyRotatedAxisGivesIdentity() {
        assertSameRotation(AxisLock(pitch = true).apply(about(0, 50.0)), Quat.IDENTITY)
        assertSameRotation(AxisLock(roll = true).apply(about(1, 50.0)), Quat.IDENTITY)
        assertSameRotation(AxisLock(yaw = true).apply(about(2, -50.0)), Quat.IDENTITY)
        assertSameRotation(AxisLock(pitch = true, roll = true, yaw = true).apply(about(2, 10.0) * about(0, 20.0)), Quat.IDENTITY)
    }

    @Test
    fun lockingAnotherAxisLeavesRotationAlone() {
        val q = about(0, 40.0)
        assertSameRotation(AxisLock(roll = true, yaw = true).apply(q), q)
    }

    @Test
    fun outputIsAlwaysUnitLength() {
        val big = Quat(3.0, 0.0, 4.0, 0.0)
        assertEquals(1.0, AxisLock().apply(big).norm(), eps)
        assertEquals(1.0, AxisLock(pitch = true).apply(big).norm(), eps)
        assertEquals(1.0, AxisLock(yaw = true, roll = true).apply(Quat(0.1, 0.2, 0.3, 0.4)).norm(), eps)
        assertSameRotation(Quat(0.0, 0.0, 0.0, 0.0).normalized(), Quat.IDENTITY)
        assertSameRotation(Quat(Double.NaN, 0.0, 0.0, 0.0).normalized(), Quat.IDENTITY)
    }

    @Test
    fun negatedQuaternionIsTheSameRotation() {
        val q = about(0, 20.0) * about(2, 80.0)
        val n = Quat(-q.w, -q.x, -q.y, -q.z)
        assertEquals(0.0, q.angleTo(n), 1e-6)
        assertTrue(n.withPositiveW().w >= 0)
        assertEquals(q.toEuler().yaw, n.toEuler().yaw, 1e-9)
    }

    @Test
    fun tiltZoomIgnoresDeadZoneAndOtherAxes() {
        val z = TiltZoom()
        z.begin(about(2, 33.0))
        assertEquals(0.0, z.update(about(2, 33.0) * about(0, 4.0), 0.02), eps) // inside dead zone
        assertEquals(0.0, z.update(about(2, 80.0), 0.02), eps) // only yaw
    }

    @Test
    fun tiltZoomDirectionAndProportionality() {
        val start = about(2, 10.0)
        val z = TiltZoom()
        z.begin(start)
        val up15 = z.update(start * about(0, 15.0), 0.02)
        val up25 = z.update(start * about(0, 25.0), 0.02)
        val down15 = z.update(start * about(0, -15.0), 0.02)
        assertTrue("tilt up zooms in", up15 > 0)
        assertTrue("tilt down zooms out", down15 < 0)
        assertEquals(-up15, down15, 1e-9)
        assertEquals(up15 * 2, up25, 1e-9) // 20 degrees past the dead zone vs 10
        assertEquals(10.0 * TiltZoom.STEPS_PER_SECOND_PER_DEGREE * 0.02, up15, 1e-9)
    }

    @Test
    fun tiltZoomWithoutPressDoesNothing() {
        val z = TiltZoom()
        assertEquals(0.0, z.update(about(0, 45.0), 0.02), eps)
        z.begin(Quat.IDENTITY)
        z.end()
        assertEquals(0.0, z.update(about(0, 45.0), 0.02), eps)
    }
}
