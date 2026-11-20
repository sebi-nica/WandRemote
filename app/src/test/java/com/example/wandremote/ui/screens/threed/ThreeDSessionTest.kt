package com.example.wandremote.ui.screens.threed

import com.example.wandremote.sensors.AxisLock
import com.example.wandremote.sensors.Quat
import com.example.wandremote.shared.Packet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeDSessionTest {
    private val sent = mutableListOf<Packet>()
    private var streaming = true
    private val session = ThreeDSession({ sent += it }, { streaming })

    private fun about(axis: Int, degrees: Double): Quat {
        val r = Math.toRadians(degrees)
        return when (axis) {
            0 -> Quat.fromAxisAngle(1.0, 0.0, 0.0, r)
            1 -> Quat.fromAxisAngle(0.0, 1.0, 0.0, r)
            else -> Quat.fromAxisAngle(0.0, 0.0, 1.0, r)
        }
    }

    private var t = 1_000_000_000L

    private fun sample(q: Quat) {
        t += 20_000_000L
        session.onSample(q, t)
    }

    private fun lastOrientation() = sent.filterIsInstance<Packet.Orientation>().last()

    @Test
    fun firstSampleBecomesReferenceAndStreamsIdentity() {
        session.onResume()
        sample(about(2, 77.0))
        val p = lastOrientation()
        assertEquals(1f, p.w, 1e-5f)
        assertEquals(0f, p.z, 1e-5f)
    }

    @Test
    fun streamsRelativeRotationAndSkipsTinyChanges() {
        session.onResume()
        sample(about(2, 10.0))
        sample(about(2, 10.0) * about(0, 30.0))
        val n = sent.size
        assertEquals(Math.sin(Math.toRadians(15.0)).toFloat(), lastOrientation().x, 1e-4f)
        sample(about(2, 10.0) * about(0, 30.01)) // 0.01 degrees: below the threshold
        assertEquals(n, sent.size)
    }

    @Test
    fun recenterSendsIdentity() {
        session.onResume()
        sample(Quat.IDENTITY)
        sample(about(0, 40.0))
        session.recenter()
        assertEquals(1f, lastOrientation().w, 1e-6f)
        sample(about(0, 40.0))
        assertEquals(1f, lastOrientation().w, 1e-4f)
    }

    @Test
    fun lockedAxisIsRemovedFromStreamAndPreview() {
        session.onResume()
        sample(Quat.IDENTITY)
        session.setLock(AxisLock(pitch = true))
        sample(about(0, 40.0))
        assertEquals(1f, lastOrientation().w, 1e-5f)
        assertEquals(0.0, session.display.angleTo(Quat.IDENTITY), 1e-6)
    }

    @Test
    fun notStreamingSendsNothingButPreviewUpdates() {
        streaming = false
        session.onResume()
        sample(Quat.IDENTITY)
        sample(about(1, 30.0))
        assertTrue(sent.isEmpty())
        assertEquals(Math.toRadians(30.0), session.display.angleTo(Quat.IDENTITY), 1e-6)
    }

    @Test
    fun zoomFreezesOrientationAndSendsZoomFromTilt() {
        session.onResume()
        sample(Quat.IDENTITY)
        session.beginZoom()
        assertTrue(session.zooming)
        sent.clear()
        sample(about(0, 30.0)) // tilt up 30 degrees
        sample(about(0, 30.0))
        assertTrue(sent.none { it is Packet.Orientation })
        val zooms = sent.filterIsInstance<Packet.Zoom>()
        assertEquals(2, zooms.size)
        assertTrue(zooms.all { it.amount > 0f })
        sample(about(0, -30.0))
        assertTrue(sent.filterIsInstance<Packet.Zoom>().last().amount < 0f)
    }

    @Test
    fun releasingZoomContinuesWithoutJump() {
        session.onResume()
        sample(Quat.IDENTITY)
        sample(about(2, 20.0))
        val frozen = lastOrientation()
        session.beginZoom()
        sample(about(2, 20.0) * about(0, 40.0))
        session.endZoom()
        assertFalse(session.zooming)
        sample(about(2, 20.0) * about(0, 40.0))
        val after = lastOrientation()
        assertEquals(frozen.z, after.z, 1e-4f)
        assertEquals(frozen.w, after.w, 1e-4f)
        assertEquals(0f, after.x, 1e-4f)
    }
}
