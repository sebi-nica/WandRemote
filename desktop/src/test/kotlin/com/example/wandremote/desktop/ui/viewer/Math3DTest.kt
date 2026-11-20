package com.example.wandremote.desktop.ui.viewer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Math3DTest {
    private fun quatAbout(axis: Int, angle: Double): FloatArray {
        val s = sin(angle / 2).toFloat()
        val q = floatArrayOf(cos(angle / 2).toFloat(), 0f, 0f, 0f)
        q[1 + axis] = s
        return q
    }

    @Test
    fun identityGivesIdentityMatrix() {
        val m = Math3D.rotationMatrix(floatArrayOf(1f, 0f, 0f, 0f))
        val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        for (i in 0 until 9) assertEquals(id[i], m[i], 1e-9)
    }

    @Test
    fun ninetyDegreesAboutZMapsXToY() {
        val v = Math3D.apply(Math3D.rotationMatrix(quatAbout(2, PI / 2)), 1.0, 0.0, 0.0)
        assertEquals(0.0, v[0], 1e-6)
        assertEquals(1.0, v[1], 1e-6)
        assertEquals(0.0, v[2], 1e-6)
    }

    @Test
    fun ninetyDegreesAboutXMapsYToZ() {
        val v = Math3D.apply(Math3D.rotationMatrix(quatAbout(0, PI / 2)), 0.0, 1.0, 0.0)
        assertEquals(1.0, v[2], 1e-6)
    }

    @Test
    fun nonUnitQuaternionIsNormalized() {
        val m = Math3D.rotationMatrix(floatArrayOf(2f, 0f, 0f, 0f))
        assertEquals(1.0, m[0], 1e-9)
        val n = Math3D.normalize(floatArrayOf(0f, 0f, 0f, 0f)) // invalid -> identity
        assertEquals(1.0, n[0], 0.0)
        val u = Math3D.normalize(floatArrayOf(1f, 2f, 3f, 4f))
        assertEquals(1.0, sqrt(u.sumOf { it * it }), 1e-9)
    }

    @Test
    fun nlerpTakesShortArcAndStaysUnit() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f, 0f) // same rotation, opposite sign
        val r = Math3D.nlerp(a, b, 0.5f)
        assertEquals(1f, r[0], 1e-6f)
        val c = Math3D.nlerp(a, quatAbout(2, PI / 2), 1f)
        assertEquals(1.0, sqrt(c.sumOf { (it * it).toDouble() }), 1e-6)
    }

    @Test
    fun sceneCullsBackFacesAndSortsFarToNear() {
        val scene = SceneBuilder.build(floatArrayOf(1f, 0f, 0f, 0f), 1f, 800f, 600f)
        assertTrue(scene.faces.size in 1..3, "a convex box shows at most 3 faces, got ${scene.faces.size}")
        assertTrue(scene.faces.any { it.kind == FaceKind.SCREEN })
        assertTrue(scene.faces.none { it.kind == FaceKind.BACK })
        assertEquals(scene.faces.sortedByDescending { it.depth }.map { it.depth }, scene.faces.map { it.depth })
        assertTrue(scene.faces.all { it.shade in 0.35f..1f })
    }

    @Test
    fun flippingThePhoneShowsTheBack() {
        val scene = SceneBuilder.build(quatAbout(1, PI), 1f, 800f, 600f)
        assertTrue(scene.faces.any { it.kind == FaceKind.BACK })
        assertTrue(scene.faces.none { it.kind == FaceKind.SCREEN })
    }

    @Test
    fun zoomScalesProjectedSize() {
        fun extent(zoom: Float): Float {
            val f = SceneBuilder.build(floatArrayOf(1f, 0f, 0f, 0f), zoom, 800f, 600f).faces.first { it.kind == FaceKind.SCREEN }
            return f.ys.max() - f.ys.min()
        }
        assertTrue(extent(2f) > extent(1f) * 1.8f)
        assertTrue(abs(extent(1f)) > 10f)
    }

    @Test
    fun sceneHasGridAndAxes() {
        val scene = SceneBuilder.build(floatArrayOf(1f, 0f, 0f, 0f), 1f, 800f, 600f)
        assertTrue(scene.lines.count { it.kind == LineKind.GRID } >= 10)
        assertEquals(1, scene.lines.count { it.kind == LineKind.AXIS_Y })
    }
}
