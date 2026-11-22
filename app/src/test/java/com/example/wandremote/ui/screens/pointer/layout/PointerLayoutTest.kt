package com.example.wandremote.ui.screens.pointer.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PointerLayoutTest {
    private val w = 1000f
    private val h = 2000f

    private fun layoutOf(vararg specs: ControlSpec) = PointerLayout(specs.toList())

    @Test
    fun circleHitUsesWidthForRadius() {
        val c = ControlShape.Circle(cx = 0.5f, cy = 0.5f, r = 0.1f) // radius 100 px, center (500, 1000)
        assertTrue(c.contains(500f, 1000f, w, h))
        assertTrue(c.contains(500f, 1090f, w, h))
        assertTrue(c.contains(590f, 1000f, w, h))
        assertTrue(!c.contains(500f, 1110f, w, h))
        assertTrue(!c.contains(610f, 1000f, w, h))
    }

    @Test
    fun roundRectHitCutsCorners() {
        val r = ControlShape.RoundRect(0.2f, 0.1f, 0.6f, 0.2f, cornerRadius = 0.05f) // x 200..600, y 200..400, corner 50
        assertTrue(r.contains(400f, 300f, w, h))
        assertTrue(r.contains(201f, 300f, w, h))
        assertTrue(!r.contains(199f, 300f, w, h))
        assertTrue(!r.contains(400f, 401f, w, h))
        assertTrue(!r.contains(201f, 201f, w, h)) // outside the rounded corner
        assertTrue(r.contains(230f, 230f, w, h)) // inside it
    }

    @Test
    fun ringSectorHitChecksRadiusBandAndAngle() {
        // Center (800, 1600), radii 150..350 px, north-west quadrant: 180..270 degrees.
        val s = ControlShape.RingSector(0.8f, 0.8f, 0.15f, 0.35f, startDeg = 180f, sweepDeg = 90f)
        assertTrue(s.contains(800f - 250f, 1600f, w, h)) // west
        assertTrue(s.contains(800f, 1600f - 250f, w, h)) // north
        assertTrue(s.contains(800f - 177f, 1600f - 177f, w, h)) // north-west diagonal
        assertTrue(!s.contains(800f + 250f, 1600f, w, h)) // east: wrong angle
        assertTrue(!s.contains(800f, 1600f + 250f, w, h)) // south
        assertTrue(!s.contains(800f - 100f, 1600f, w, h)) // inside the inner radius
        assertTrue(!s.contains(800f - 400f, 1600f, w, h)) // outside the outer radius
    }

    @Test
    fun ringSectorHandlesAngleWrapAroundZero() {
        // 300..60 degrees: through east (y points down, so 315 is up-right and 45 is down-right).
        val s = ControlShape.RingSector(0.5f, 0.5f, 0.1f, 0.3f, startDeg = 300f, sweepDeg = 120f)
        assertTrue(s.contains(500f + 200f, 1000f, w, h)) // 0 deg
        assertTrue(s.contains(500f + 140f, 1000f - 140f, w, h)) // 315 deg
        assertTrue(s.contains(500f + 140f, 1000f + 140f, w, h)) // 45 deg
        assertTrue(!s.contains(500f - 200f, 1000f, w, h)) // 180 deg
        assertTrue(!s.contains(500f, 1000f + 200f, w, h)) // 90 deg is past the end at 60
    }

    @Test
    fun mirroringTwiceRestoresEveryShape() {
        val layout = DefaultLayouts.rightHanded(0.2f, 1.85f)
        val twice = layout.mirrored().mirrored()
        assertEquals(layout.controls.map { it.id }, twice.controls.map { it.id })
        // Compare by behavior: a dense grid of touch points must hit the same control before and after.
        var y = 0.37f // off the integer grid so no sample sits exactly on a shape edge
        while (y <= 1850f) {
            var x = 0.37f
            while (x <= 1000f) {
                assertEquals(layout.hitTest(x, y, 1000f, 1850f), twice.hitTest(x, y, 1000f, 1850f))
                x += 7f
            }
            y += 7f
        }
    }

    @Test
    fun mirroredCircleAndRectFlipX() {
        assertEquals(ControlShape.Circle(0.3f, 0.4f, 0.1f), ControlShape.Circle(0.7f, 0.4f, 0.1f).mirrored())
        val r = ControlShape.RoundRect(0.1f, 0.2f, 0.4f, 0.3f, 0.02f).mirrored()
        assertEquals(0.6f, r.left, 1e-6f)
        assertEquals(0.9f, r.right, 1e-6f)
        assertEquals(0.2f, r.top, 0f)
    }

    @Test
    fun mirroredRingSectorMirrorsAngles() {
        val s = ControlShape.RingSector(0.8f, 0.8f, 0.1f, 0.3f, startDeg = 180f, sweepDeg = 90f)
        val m = s.mirrored()
        assertEquals(0.2f, m.cx, 1e-6f)
        assertEquals(270f, m.startDeg, 1e-4f) // 180..270 becomes 270..360
        assertEquals(90f, m.sweepDeg, 0f)
        // West (180) became east (0 / 360), north (270) stays north.
        assertTrue(m.contains(200f + 200f, 1600f, w, h))
        assertTrue(m.contains(200f, 1600f - 200f, w, h))
        assertTrue(!m.contains(200f - 200f, 1600f, w, h))
    }

    @Test
    fun mirroredRingSectorAngleWraps() {
        // 100..220 mirrors to 180 - 220 = -40 -> 320 .. 80 (wraps through 0).
        val m = ControlShape.RingSector(0.5f, 0.5f, 0.1f, 0.3f, startDeg = 100f, sweepDeg = 120f).mirrored()
        assertEquals(320f, m.startDeg, 1e-4f)
        assertTrue(m.contains(500f + 200f, 1000f, w, h)) // east (0 deg) is inside the wrapped range
        assertTrue(!m.contains(500f - 200f, 1000f, w, h)) // west is not
    }

    @Test
    fun hitTestPicksTopmostOfOverlappingControls() {
        val under = ControlSpec(ControlId.KNOB, ControlShape.Circle(0.5f, 0.25f, 0.2f))
        val over = ControlSpec(ControlId.LASER, ControlShape.Circle(0.5f, 0.25f, 0.1f))
        assertEquals(ControlId.LASER, layoutOf(under, over).hitTest(500f, 500f, w, h))
        assertEquals(ControlId.KNOB, layoutOf(over, under).hitTest(500f, 500f, w, h))
        assertEquals(ControlId.KNOB, layoutOf(under, over).hitTest(500f + 150f, 500f, w, h))
        assertNull(layoutOf(under, over).hitTest(5f, 5f, w, h))
    }

    @Test
    fun duplicateIdsAreRejected() {
        val c = ControlSpec(ControlId.KNOB, ControlShape.Circle(0.5f, 0.5f, 0.1f))
        assertTrue(runCatching { PointerLayout(listOf(c, c)) }.isFailure)
    }

    @Test
    fun mirroringKeepsArrowDirectionsOrdered() {
        val layout = DefaultLayouts.rightHanded()
        fun cx(l: PointerLayout, id: ControlId): Float {
            val s = l[id]!!.shape as ControlShape.RoundRect
            return (s.left + s.right) / 2f
        }
        for (l in listOf(layout, layout.mirrored())) {
            assertTrue(cx(l, ControlId.ARROW_LEFT) < cx(l, ControlId.ARROW_UP))
            assertTrue(cx(l, ControlId.ARROW_UP) < cx(l, ControlId.ARROW_RIGHT))
        }
        // The cross itself moved to the other side.
        assertTrue(cx(layout, ControlId.ARROW_UP) < 0.5f)
        assertTrue(cx(layout.mirrored(), ControlId.ARROW_UP) > 0.5f)
    }

    @Test
    fun defaultLayoutContainsEveryControl() {
        val layout = DefaultLayouts.rightHanded()
        assertEquals(ControlId.entries.toSet(), layout.controls.map { it.id }.toSet())
    }

    @Test
    fun flippedLayoutIsTheMirroredRightHandedOne() {
        val right = DefaultLayouts.rightHanded(0.2f, 1.85f)
        val left = DefaultLayouts.forScreen(0.2f, 1.85f, flipped = true)
        assertEquals(right.mirrored(), left)
        assertEquals(right, DefaultLayouts.forScreen(0.2f, 1.85f, flipped = false))
        val knobRight = right[ControlId.KNOB]!!.shape as ControlShape.Circle
        val knobLeft = left[ControlId.KNOB]!!.shape as ControlShape.Circle
        assertEquals(1f - knobRight.cx, knobLeft.cx, 1e-6f)
    }

    @Test
    fun everyControlIsHitAtItsAnchorOnAnyAspectAndFlip() {
        for (aspect in listOf(1.33f, 1.6f, 1.85f, 2.05f)) {
            for (flipped in listOf(false, true)) {
                val layout = DefaultLayouts.forScreen(0.2f, aspect, flipped)
                val wp = 1000f
                val hp = wp * aspect
                for (spec in layout.controls) {
                    val (x, y) = anchor(spec.shape, wp, hp)
                    assertEquals("$aspect $flipped ${spec.id}", spec.id, layout.hitTest(x, y, wp, hp))
                }
            }
        }
    }

    @Test
    fun defaultControlsDoNotOverlapAndStayInsideTheArea() {
        for (knob in listOf(0.14f, 0.2f, 0.28f)) {
            for (aspect in listOf(1.45f, 1.6f, 1.85f, 2.05f)) {
                for (flipped in listOf(false, true)) {
                    val layout = DefaultLayouts.forScreen(knob, aspect, flipped)
                    val wp = 1000f
                    val hp = wp * aspect
                    var overlaps = 0
                    var y = 0f
                    while (y <= hp) {
                        var x = 0f
                        while (x <= wp) {
                            if (layout.controls.count { it.shape.contains(x, y, wp, hp) } > 1) overlaps++
                            x += 6f
                        }
                        y += 6f
                    }
                    assertEquals("overlap: knob $knob aspect $aspect flipped $flipped", 0, overlaps)
                    for (spec in layout.controls) {
                        assertTrue("${spec.id} inside: knob $knob aspect $aspect", insideArea(spec.shape, wp, hp))
                    }
                }
            }
        }
    }

    private fun insideArea(shape: ControlShape, w: Float, h: Float): Boolean = when (shape) {
        is ControlShape.Circle ->
            shape.cx * w - shape.r * w >= -0.5f && shape.cx * w + shape.r * w <= w + 0.5f &&
                shape.cy * h - shape.r * w >= -0.5f && shape.cy * h + shape.r * w <= h + 0.5f
        is ControlShape.RoundRect -> shape.left >= 0f && shape.right <= 1f && shape.top >= 0f && shape.bottom <= 1f
        is ControlShape.RingSector -> {
            val angles = listOf(shape.startDeg, shape.startDeg + shape.sweepDeg / 2f, shape.startDeg + shape.sweepDeg)
            angles.all { deg ->
                val a = Math.toRadians(deg.toDouble())
                listOf(shape.innerR, shape.outerR).all { r ->
                    val x = shape.cx * w + cos(a) * r * w
                    val y = shape.cy * h + sin(a) * r * w
                    x >= -0.5 && x <= w + 0.5 && y >= -0.5 && y <= h + 0.5
                }
            }
        }
    }

    /** A point surely inside the shape. */
    private fun anchor(shape: ControlShape, w: Float, h: Float): Pair<Float, Float> = when (shape) {
        is ControlShape.Circle -> shape.cx * w to shape.cy * h
        is ControlShape.RoundRect -> (shape.left + shape.right) / 2f * w to (shape.top + shape.bottom) / 2f * h
        is ControlShape.RingSector -> {
            val a = Math.toRadians((shape.startDeg + shape.sweepDeg / 2f).toDouble())
            val r = (shape.innerR + shape.outerR) / 2f * w
            (shape.cx * w + cos(a) * r).toFloat() to (shape.cy * h + sin(a) * r).toFloat()
        }
    }
}
