package com.example.wandremote.sensors

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeLaserTest {
    private val pi = PI.toFloat()
    private fun rad(deg: Float) = deg * pi / 180f

    private fun laser(start: Aim = Aim(0.3f, 0.1f)) = RelativeLaser().also { it.press(start) }

    @Test
    fun startsAtCenterOnPress() {
        val start = Aim(1.2f, -0.4f)
        val p = laser(start).point(start, 30f, 0.5625f)
        assertEquals(0.5f, p.x, 1e-5f)
        assertEquals(0.5f, p.y, 1e-5f)
    }

    @Test
    fun halfRangeTurnReachesTheEdges() {
        val l = laser(Aim(0f, 0f))
        assertEquals(1f, l.point(Aim(rad(15f), 0f), 30f, 0.5625f).x, 1e-4f)
        assertEquals(0f, l.point(Aim(rad(-15f), 0f), 30f, 0.5625f).x, 1e-4f)
        assertEquals(0.75f, l.point(Aim(rad(7.5f), 0f), 30f, 0.5625f).x, 1e-4f)
    }

    @Test
    fun tiltingUpMovesUpAndAspectScalesVerticalRange() {
        val l = laser(Aim(0f, 0f))
        // aspect 0.5, range 40: vertical range = 20 deg, so 10 deg up reaches the top edge.
        assertEquals(0f, l.point(Aim(0f, rad(10f)), 40f, 0.5f).y, 1e-4f)
        assertEquals(1f, l.point(Aim(0f, rad(-10f)), 40f, 0.5f).y, 1e-4f)
        assertEquals(0.25f, l.point(Aim(0f, rad(5f)), 40f, 0.5f).y, 1e-4f)
        // the same tilt moves the dot half as far (in y fraction) on a screen twice as tall
        assertEquals(0.375f, l.point(Aim(0f, rad(5f)), 40f, 1f).y, 1e-4f)
    }

    @Test
    fun yawDeltaWrapsAroundPi() {
        val l = laser(Aim(pi - rad(5f), 0f))
        val p = l.point(Aim(-pi + rad(5f), 0f), 30f, 0.5625f) // turned 10 deg right across +-180
        assertEquals(0.5f + 10f / 30f, p.x, 1e-4f)
    }

    @Test
    fun resultIsClamped() {
        val p = laser(Aim(0f, 0f)).point(Aim(rad(120f), rad(80f)), 30f, 0.5625f)
        assertEquals(1f, p.x, 0f)
        assertEquals(0f, p.y, 0f)
    }

    @Test
    fun unknownAspectFallsBackToSixteenNine() {
        val p = RelativeLaser.mapLaser(0f, rad(5f), 30f, 0f)
        assertEquals(0.5f - 5f / (30f * 9f / 16f), p.y, 1e-4f)
    }

    @Test
    fun withoutPressTheDotIsCentered() {
        val p = RelativeLaser().point(Aim(1f, 1f), 30f, 0.5625f)
        assertEquals(0.5f, p.x, 0f)
        assertEquals(0.5f, p.y, 0f)
    }

    @Test
    fun smootherStartsAtFirstSampleAndConverges() {
        val s = PointSmoother(alpha = 0.5f)
        assertEquals(0.2f, s.next(ScreenPoint(0.2f, 0.2f)).x, 1e-5f)
        val b = s.next(ScreenPoint(1f, 1f))
        assertEquals(0.6f, b.x, 1e-5f)
        assertEquals(0.6f, b.y, 1e-5f)
    }
}
