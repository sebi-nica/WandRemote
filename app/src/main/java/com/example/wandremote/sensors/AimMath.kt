package com.example.wandremote.sensors

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure math for the laser pointer's absolute aiming (no Android imports, so it is unit-testable).
 *
 * World frame is Android's (x = East, y = North, z = Up). The phone is held like a remote, screen up, top edge
 * toward the screen, so the pointing axis is the device +Y axis.
 */

/** Where the pointing axis aims: [yaw] = heading (rad, clockwise from the world Y axis, -PI..PI), [pitch] = elevation (rad, up positive). */
data class Aim(val yaw: Float, val pitch: Float)

/** Wraps an angle into (-PI, PI]. */
fun wrapAngle(a: Float): Float {
    val twoPi = (2 * PI).toFloat()
    var r = a % twoPi
    if (r > PI.toFloat()) r -= twoPi else if (r <= -PI.toFloat()) r += twoPi
    return r
}

/**
 * Direction of the device +Y axis in the world frame for the unit quaternion (w, x, y, z) of a rotation vector sensor.
 * This is the second column of the matrix `SensorManager.getRotationMatrixFromVector` would give.
 */
fun aimFromQuaternion(w: Float, x: Float, y: Float, z: Float): Aim {
    val east = 2f * (x * y - w * z)
    val north = 1f - 2f * (x * x + z * z)
    val up = 2f * (y * z + w * x)
    return Aim(atan2(east, north), asin(up.coerceIn(-1f, 1f)))
}

/** Turns wrapped yaw samples into a continuous angle (no jump when crossing +-PI). */
class YawUnwrapper {
    private var last = 0f
    private var started = false

    fun unwrap(yaw: Float): Float {
        last = if (started) last + wrapAngle(yaw - last) else yaw
        started = true
        return last
    }

    fun reset() {
        started = false
    }
}

/** Averages [Aim] samples; yaw is unwrapped first so samples either side of +-PI average correctly. */
class AimAverager {
    private val unwrapper = YawUnwrapper()
    private var sumYaw = 0.0
    private var sumPitch = 0.0
    private var count = 0

    fun add(aim: Aim) {
        sumYaw += unwrapper.unwrap(aim.yaw)
        sumPitch += aim.pitch
        count++
    }

    val size: Int get() = count

    fun result(): Aim? = if (count == 0) null else Aim(wrapAngle((sumYaw / count).toFloat()), (sumPitch / count).toFloat())
}

/**
 * Short rolling history of aim samples. Tapping the screen jolts the phone, so a capture averages a window that
 * ends [TOUCH_SKIP_NS] BEFORE the touch instead of after it.
 */
class AimHistory(private val keepNs: Long = 1_000_000_000L) {
    private val times = ArrayDeque<Long>()
    private val aims = ArrayDeque<Aim>()

    val latest: Aim? get() = aims.lastOrNull()

    fun add(timeNs: Long, aim: Aim) {
        times.addLast(timeNs)
        aims.addLast(aim)
        while (times.isNotEmpty() && timeNs - times.first() > keepNs) {
            times.removeFirst()
            aims.removeFirst()
        }
    }

    fun clear() {
        times.clear()
        aims.clear()
    }

    /** Mean of samples within [windowNs] ending [skipNs] before [nowNs]; else the newest sample before that, else the newest, else null. */
    fun averageBefore(nowNs: Long, skipNs: Long = TOUCH_SKIP_NS, windowNs: Long = CAPTURE_WINDOW_NS): Aim? {
        val end = nowNs - skipNs
        val avg = AimAverager()
        for (i in times.indices) {
            if (times[i] in (end - windowNs)..end) avg.add(aims[i])
        }
        avg.result()?.let { return it }
        for (i in times.indices.reversed()) if (times[i] <= end) return aims[i]
        return latest
    }

    companion object {
        const val TOUCH_SKIP_NS = 80_000_000L
        const val CAPTURE_WINDOW_NS = 150_000_000L
    }
}

/** Normalized screen position, 0..1 with (0,0) = top-left. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Maps an [Aim] to a screen position using the four corner aims captured during calibration.
 *
 * Corners (in capture order): top-left, top-right, bottom-right, bottom-left, stored as yaw relative to [refYaw]
 * (so the quad never straddles the +-PI wrap) and absolute pitch. The mapping is the inverse of the bilinear
 * interpolation of the quad, which copes with the skew of aiming at a flat screen from an off-axis position.
 *
 * Limitation: the game rotation vector has no compass, so its yaw zero is arbitrary after every sensor restart and
 * drifts slowly (roughly degrees per minute). Pitch is anchored by gravity and does not drift. [recentered] fixes the
 * yaw by shifting [refYaw]; the Pointer screen offers a Re-center button for that.
 */
class ScreenCalibration(val refYaw: Float, val corners: List<Aim>) {
    init {
        require(corners.size == 4) { "need exactly 4 corners" }
    }

    /** True when the corners span a sensible area (not the same point, not collinear). */
    val isValid: Boolean
        get() {
            val c = corners
            var twiceArea = 0f
            for (i in 0 until 4) {
                val p = c[i]
                val q = c[(i + 1) % 4]
                twiceArea += p.yaw * q.pitch - q.yaw * p.pitch
            }
            return abs(twiceArea) * 0.5f > MIN_AREA_RAD2
        }

    private val centerYawRel: Float get() = corners.map { it.yaw }.average().toFloat()

    fun map(aim: Aim): ScreenPoint {
        val rel = Aim(wrapAngle(aim.yaw - refYaw), aim.pitch)
        return inverseBilinear(corners[0], corners[1], corners[2], corners[3], rel)
    }

    /** New calibration for the case where the user aims at the screen centre and the phone reports [centerAim] (yaw shift only). */
    fun recentered(centerAim: Aim): ScreenCalibration {
        val shift = wrapAngle(centerAim.yaw - refYaw) - centerYawRel
        return ScreenCalibration(wrapAngle(refYaw + shift), corners)
    }

    companion object {
        const val MIN_AREA_RAD2 = 0.0005f // about 1.3 x 1.3 degrees

        /** Builds a calibration from the captured corners (TL, TR, BR, BL), or null if they are degenerate. */
        fun fromCorners(captured: List<Aim>): ScreenCalibration? {
            if (captured.size != 4) return null
            val ref = captured[0].yaw
            val rel = captured.map { Aim(wrapAngle(it.yaw - ref), it.pitch) }
            val meanOffset = rel.map { it.yaw }.average().toFloat()
            val cal = ScreenCalibration(wrapAngle(ref + meanOffset), rel.map { Aim(it.yaw - meanOffset, it.pitch) })
            return if (cal.isValid) cal else null
        }

        /**
         * Finds (u, v) with P(u,v) = (1-u)(1-v)A + u(1-v)B + uv C + (1-u)v D equal to [p] (analytic quadratic),
         * clamped to 0..1. Points are (yaw, pitch) in the plane.
         */
        fun inverseBilinear(a: Aim, b: Aim, c: Aim, d: Aim, p: Aim): ScreenPoint {
            val ex = b.yaw - a.yaw
            val ey = b.pitch - a.pitch
            val fx = d.yaw - a.yaw
            val fy = d.pitch - a.pitch
            val gx = a.yaw - b.yaw + c.yaw - d.yaw
            val gy = a.pitch - b.pitch + c.pitch - d.pitch
            val hx = p.yaw - a.yaw
            val hy = p.pitch - a.pitch

            fun cross(x1: Float, y1: Float, x2: Float, y2: Float) = x1 * y2 - y1 * x2
            val k2 = cross(gx, gy, fx, fy)
            val k1 = cross(ex, ey, fx, fy) + cross(hx, hy, gx, gy)
            val k0 = cross(hx, hy, ex, ey)

            val v: Float = if (abs(k2) < EPS * (abs(k1) + EPS)) {
                if (abs(k1) < 1e-12f) return ScreenPoint(0.5f, 0.5f)
                -k0 / k1
            } else {
                val disc = k1 * k1 - 4f * k0 * k2
                if (disc < 0f) {
                    -k1 / (2f * k2) // outside what the quad can reach: nearest point of the parabola
                } else {
                    val s = sqrt(disc)
                    val v1 = (-k1 - s) / (2f * k2)
                    val v2 = (-k1 + s) / (2f * k2)
                    if (distOutside(v1) <= distOutside(v2)) v1 else v2
                }
            }
            val denX = ex + gx * v
            val denY = ey + gy * v
            val u = if (abs(denX) >= abs(denY)) (hx - fx * v) / denX else (hy - fy * v) / denY
            if (u.isNaN() || v.isNaN()) return ScreenPoint(0.5f, 0.5f)
            return ScreenPoint(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        }

        private fun distOutside(v: Float): Float = if (v < 0f) -v else if (v > 1f) v - 1f else 0f
        private const val EPS = 1e-6f
    }
}

/** Light exponential smoothing of the cursor position; [alpha] 1 = none. */
class PointSmoother(private val alpha: Float = 0.6f) {
    private var x = 0f
    private var y = 0f
    private var started = false

    fun next(p: ScreenPoint): ScreenPoint {
        if (!started) {
            x = p.x
            y = p.y
            started = true
        } else {
            x += alpha * (p.x - x)
            y += alpha * (p.y - y)
        }
        return ScreenPoint(x, y)
    }

    fun reset() {
        started = false
    }
}
