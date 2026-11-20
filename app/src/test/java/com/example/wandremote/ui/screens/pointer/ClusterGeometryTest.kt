package com.example.wandremote.ui.screens.pointer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterGeometryTest {
    @Test
    fun mirrorAngleIsAnInvolution() {
        for (a in listOf(0f, 10f, 90f, 180f, 201.5f, 270f, 359f)) {
            assertEquals(a, mirrorAngle(mirrorAngle(a)), 1e-4f)
        }
        assertEquals(0f, mirrorAngle(180f), 1e-4f)
        assertEquals(270f, mirrorAngle(270f), 1e-4f)
    }

    @Test
    fun rightHandedRingSplitsWestAndNorth() {
        assertEquals(RingButton.LEFT, ringButtonAt(180f, false))
        assertEquals(RingButton.LEFT, ringButtonAt(224f, false))
        assertEquals(RingButton.RIGHT, ringButtonAt(250f, false))
        assertNull(ringButtonAt(90f, false))
        assertNull(ringButtonAt(0f, false))
    }

    @Test
    fun leftHandedRingIsMirrored() {
        // West mirrors to east: the half nearest the screen edge is still the left click.
        assertEquals(RingButton.LEFT, ringButtonAt(0f, true))
        assertEquals(RingButton.LEFT, ringButtonAt(340f, true))
        assertEquals(RingButton.RIGHT, ringButtonAt(290f, true))
        assertNull(ringButtonAt(180f, true))
        assertNull(ringButtonAt(90f, true))
    }

    @Test
    fun arcStartsMirror() {
        assertEquals(180f, arcStartAngle(180f, 43f, false), 0f)
        assertEquals(317f, arcStartAngle(180f, 43f, true), 1e-3f)
        assertEquals(270f, arcStartAngle(227f, 43f, true), 1e-3f)
    }

    @Test
    fun clusterSitsInTheChosenCorner() {
        assertEquals(800f, clusterCenterX(1000f, 200f, false), 0f)
        assertEquals(200f, clusterCenterX(1000f, 200f, true), 0f)
    }
}
