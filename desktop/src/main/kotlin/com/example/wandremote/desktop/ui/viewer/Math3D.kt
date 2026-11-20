package com.example.wandremote.desktop.ui.viewer

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin 3D helpers for the viewer. Quaternions are (w, x, y, z); matrices are row-major 3x3 [DoubleArray]s.
 * The phone's frame is x = right, y = top edge, z = out of the screen; the viewer shows that frame with
 * x to the right, y up and z toward the viewer, so identity shows the phone's screen facing you.
 */
object Math3D {
    private val IDENTITY_Q = floatArrayOf(1f, 0f, 0f, 0f)

    /** Unit-length copy of [q] (w, x, y, z); identity for a zero/invalid quaternion. */
    fun normalize(q: FloatArray): DoubleArray {
        val w = q[0].toDouble()
        val x = q[1].toDouble()
        val y = q[2].toDouble()
        val z = q[3].toDouble()
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-9 || n.isNaN()) doubleArrayOf(1.0, 0.0, 0.0, 0.0) else doubleArrayOf(w / n, x / n, y / n, z / n)
    }

    /** Rotation matrix of the (normalized) quaternion. */
    fun rotationMatrix(q: FloatArray): DoubleArray {
        val n = normalize(q)
        val w = n[0]
        val x = n[1]
        val y = n[2]
        val z = n[3]
        return doubleArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
        )
    }

    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += a[i * 3 + k] * b[k * 3 + j]
            r[i * 3 + j] = s
        }
        return r
    }

    fun rotationX(a: Double): DoubleArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c)
    }

    fun rotationY(a: Double): DoubleArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c)
    }

    /** Applies matrix [m] to (x, y, z). */
    fun apply(m: DoubleArray, x: Double, y: Double, z: Double) = doubleArrayOf(
        m[0] * x + m[1] * y + m[2] * z,
        m[3] * x + m[4] * y + m[5] * z,
        m[6] * x + m[7] * y + m[8] * z,
    )

    /** Normalized linear interpolation from [from] toward [to] along the shorter arc. */
    fun nlerp(from: FloatArray, to: FloatArray, t: Float): FloatArray {
        val dot = from[0] * to[0] + from[1] * to[1] + from[2] * to[2] + from[3] * to[3]
        val sign = if (dot < 0f) -1f else 1f
        val r = FloatArray(4) { from[it] + (sign * to[it] - from[it]) * t }
        val n = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3])
        if (n < 1e-6f) return IDENTITY_Q.copyOf()
        return FloatArray(4) { r[it] / n }
    }
}

enum class FaceKind { SCREEN, BACK, TOP, SIDE }

enum class LineKind { GRID, AXIS_X, AXIS_Y, AXIS_Z }

/** A projected quad in pixels. [shade] is 0..1 Lambert brightness; [depth] is the distance from the camera. */
class ProjectedFace(val xs: FloatArray, val ys: FloatArray, val shade: Float, val kind: FaceKind, val depth: Float)

class ProjectedLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val kind: LineKind)

class Scene(val lines: List<ProjectedLine>, val faces: List<ProjectedFace>)

object SceneBuilder {
    const val HALF_WIDTH = 0.45
    const val HALF_HEIGHT = 0.9
    const val HALF_DEPTH = 0.06
    const val CAMERA_DISTANCE = 6.0
    private const val FLOOR_Y = -1.5
    private const val GRID_HALF = 2.0
    private const val GRID_STEP = 0.5

    /** Fixed camera orientation so the thickness and top edge of the phone are visible at identity. */
    private val view = Math3D.multiply(Math3D.rotationX(0.35), Math3D.rotationY(-0.5))
    private val light = doubleArrayOf(-0.35, 0.55, 0.75).let { v ->
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        doubleArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    private class FaceDef(val normal: IntArray, val kind: FaceKind)

    private val faceDefs = listOf(
        FaceDef(intArrayOf(0, 0, 1), FaceKind.SCREEN),
        FaceDef(intArrayOf(0, 0, -1), FaceKind.BACK),
        FaceDef(intArrayOf(0, 1, 0), FaceKind.TOP),
        FaceDef(intArrayOf(0, -1, 0), FaceKind.SIDE),
        FaceDef(intArrayOf(1, 0, 0), FaceKind.SIDE),
        FaceDef(intArrayOf(-1, 0, 0), FaceKind.SIDE),
    )

    private val half = doubleArrayOf(HALF_WIDTH, HALF_HEIGHT, HALF_DEPTH)

    /**
     * Builds the drawable scene for a phone rotated by [q] (w, x, y, z), at [zoom], in a [width] x [height] viewport.
     * Faces are back-face culled and sorted far to near (painter's algorithm).
     */
    fun build(q: FloatArray, zoom: Float, width: Float, height: Float): Scene {
        val scale = min(width, height) * 0.2 * zoom
        val cx = width / 2.0
        val cy = height / 2.0
        val rot = Math3D.rotationMatrix(q)
        val total = Math3D.multiply(view, rot)

        fun project(p: DoubleArray): FloatArray {
            val persp = CAMERA_DISTANCE / max(0.1, CAMERA_DISTANCE - p[2])
            return floatArrayOf((cx + p[0] * scale * persp).toFloat(), (cy - p[1] * scale * persp).toFloat())
        }

        val lines = ArrayList<ProjectedLine>()
        var g = -GRID_HALF
        while (g <= GRID_HALF + 1e-9) {
            val a = project(Math3D.apply(view, g, FLOOR_Y, -GRID_HALF))
            val b = project(Math3D.apply(view, g, FLOOR_Y, GRID_HALF))
            lines += ProjectedLine(a[0], a[1], b[0], b[1], LineKind.GRID)
            val c = project(Math3D.apply(view, -GRID_HALF, FLOOR_Y, g))
            val d = project(Math3D.apply(view, GRID_HALF, FLOOR_Y, g))
            lines += ProjectedLine(c[0], c[1], d[0], d[1], LineKind.GRID)
            g += GRID_STEP
        }
        val origin = project(Math3D.apply(view, 0.0, FLOOR_Y, 0.0))
        val axes = listOf(
            Pair(doubleArrayOf(0.8, 0.0, 0.0), LineKind.AXIS_X),
            Pair(doubleArrayOf(0.0, 0.8, 0.0), LineKind.AXIS_Y),
            Pair(doubleArrayOf(0.0, 0.0, 0.8), LineKind.AXIS_Z),
        )
        for ((v, kind) in axes) {
            val e = project(Math3D.apply(view, v[0], FLOOR_Y + v[1], v[2]))
            lines += ProjectedLine(origin[0], origin[1], e[0], e[1], kind)
        }

        val faces = ArrayList<ProjectedFace>()
        val signs = arrayOf(intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(1, 1), intArrayOf(-1, 1))
        for (def in faceDefs) {
            val n = def.normal
            val nAxis = n.indexOfFirst { it != 0 }
            val nv = Math3D.apply(total, n[0].toDouble(), n[1].toDouble(), n[2].toDouble())
            val centerLocal = DoubleArray(3) { n[it] * half[it] }
            val cv = Math3D.apply(total, centerLocal[0], centerLocal[1], centerLocal[2])
            val toCam = doubleArrayOf(-cv[0], -cv[1], CAMERA_DISTANCE - cv[2])
            if (nv[0] * toCam[0] + nv[1] * toCam[1] + nv[2] * toCam[2] <= 0.0) continue // back-face

            val u = (nAxis + 1) % 3
            val v = (nAxis + 2) % 3
            val xs = FloatArray(4)
            val ys = FloatArray(4)
            for ((i, s) in signs.withIndex()) {
                val local = centerLocal.copyOf()
                local[u] += s[0] * half[u]
                local[v] += s[1] * half[v]
                val p = project(Math3D.apply(total, local[0], local[1], local[2]))
                xs[i] = p[0]
                ys[i] = p[1]
            }
            val lambert = max(0.0, nv[0] * light[0] + nv[1] * light[1] + nv[2] * light[2])
            faces += ProjectedFace(xs, ys, (0.35 + 0.65 * lambert).toFloat(), def.kind, (CAMERA_DISTANCE - cv[2]).toFloat())
        }
        faces.sortByDescending { it.depth }
        return Scene(lines, faces)
    }
}
