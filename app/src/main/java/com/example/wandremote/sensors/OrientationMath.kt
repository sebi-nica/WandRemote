package com.example.wandremote.sensors

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quaternion (w, x, y, z) in the phone's frame: x = right, y = top edge, z = out of the screen.
 * Pure Kotlin (no Android types) so it can be unit tested on the JVM.
 */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun conjugate() = Quat(w, -x, -y, -z)

    fun norm() = sqrt(w * w + x * x + y * y + z * z)

    /** Unit-length copy; identity if the length is ~0 or not finite. */
    fun normalized(): Quat {
        val n = norm()
        return if (n < 1e-9 || n.isNaN() || n.isInfinite()) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    /** Same rotation with w >= 0 (q and -q are the same rotation); keeps streamed values continuous. */
    fun withPositiveW() = if (w < 0) Quat(-w, -x, -y, -z) else this

    /** Rotation angle between this and [o] in radians (0..PI), ignoring the q/-q ambiguity. */
    fun angleTo(o: Quat): Double {
        val a = normalized()
        val b = o.normalized()
        val dot = abs(a.w * b.w + a.x * b.x + a.y * b.y + a.z * b.z).coerceAtMost(1.0)
        return 2 * acos(dot)
    }

    /** Row-major 3x3 rotation matrix (of the normalized quaternion). */
    fun toMatrix(): DoubleArray {
        val q = normalized()
        val (w, x, y, z) = listOf(q.w, q.x, q.y, q.z)
        return doubleArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
        )
    }

    /**
     * Yaw / pitch / roll for the convention R = Rz(yaw) * Rx(pitch) * Ry(roll), in radians:
     * pitch = about x (tilt the top edge up/down), roll = about y (twist around the long axis),
     * yaw = about z (turn like a steering wheel). Pitch is in -PI/2..PI/2; at +-90 degrees yaw and roll
     * are degenerate and the whole angle is reported as yaw.
     */
    fun toEuler(): Euler {
        val m = toMatrix()
        val pitch = asin(m[7].coerceIn(-1.0, 1.0))
        if (sqrt(m[1] * m[1] + m[4] * m[4]) < 1e-7) return Euler(pitch, 0.0, atan2(m[3], m[0]))
        return Euler(pitch, atan2(-m[6], m[8]), atan2(-m[1], m[4]))
    }

    companion object {
        val IDENTITY = Quat(1.0, 0.0, 0.0, 0.0)

        fun fromAxisAngle(ax: Double, ay: Double, az: Double, radians: Double): Quat {
            val n = sqrt(ax * ax + ay * ay + az * az)
            if (n < 1e-12) return IDENTITY
            val s = sin(radians / 2) / n
            return Quat(cos(radians / 2), ax * s, ay * s, az * s)
        }

        /** Inverse of [toEuler]: Rz(yaw) * Rx(pitch) * Ry(roll). */
        fun fromEuler(e: Euler): Quat =
            fromAxisAngle(0.0, 0.0, 1.0, e.yaw) * fromAxisAngle(1.0, 0.0, 0.0, e.pitch) * fromAxisAngle(0.0, 1.0, 0.0, e.roll)
    }
}

data class Euler(val pitch: Double, val roll: Double, val yaw: Double)

/** Which axes are frozen in 3D mode (as the user sees them, see [Quat.toEuler]). */
data class AxisLock(val pitch: Boolean = false, val roll: Boolean = false, val yaw: Boolean = false) {
    val any get() = pitch || roll || yaw

    /** [q] with the locked rotations removed (normalized). With no lock this is just [q] normalized. */
    fun apply(q: Quat): Quat {
        val n = q.normalized()
        if (!any) return n
        val e = n.toEuler()
        return Quat.fromEuler(
            Euler(
                pitch = if (pitch) 0.0 else e.pitch,
                roll = if (roll) 0.0 else e.roll,
                yaw = if (yaw) 0.0 else e.yaw,
            ),
        ).normalized()
    }
}

/** Orientation relative to a reference: q_rel = q_ref^-1 * q_current (a rotation in the reference device's frame). */
fun relativeTo(reference: Quat, current: Quat): Quat = (reference.normalized().conjugate() * current.normalized()).normalized()

/**
 * "Tilt + hold" zoom. [begin] remembers the orientation at press; [update] returns the zoom amount (positive =
 * zoom in) accumulated since the previous call: pitch change since press (top edge up = positive), minus a
 * dead zone, times [stepsPerSecondPerDegree], times the elapsed time.
 */
class TiltZoom(
    private val deadZoneDegrees: Double = DEAD_ZONE_DEGREES,
    private val stepsPerSecondPerDegree: Double = STEPS_PER_SECOND_PER_DEGREE,
    private val maxExcessDegrees: Double = MAX_EXCESS_DEGREES,
) {
    private var pressed: Quat? = null

    fun begin(current: Quat) {
        pressed = current.normalized()
    }

    fun end() {
        pressed = null
    }

    /** Pitch (degrees, top edge up = positive) of [current] relative to the orientation at [begin]. */
    fun pitchDegrees(current: Quat): Double {
        val p = pressed ?: return 0.0
        return Math.toDegrees(relativeTo(p, current).toEuler().pitch)
    }

    fun update(current: Quat, dtSeconds: Double): Double {
        if (pressed == null) return 0.0
        val pitch = pitchDegrees(current)
        val excess = (abs(pitch) - deadZoneDegrees).coerceIn(0.0, maxExcessDegrees)
        return sign(pitch) * excess * stepsPerSecondPerDegree * dtSeconds.coerceIn(0.0, 0.1)
    }

    companion object {
        const val DEAD_ZONE_DEGREES = 5.0
        /** 0.15 steps/s per degree: 25 degrees past the dead zone = ~3.75 steps/s. */
        const val STEPS_PER_SECOND_PER_DEGREE = 0.15
        const val MAX_EXCESS_DEGREES = 60.0
    }
}
