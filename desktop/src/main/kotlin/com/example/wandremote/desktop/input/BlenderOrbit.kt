package com.example.wandremote.desktop.input

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** What [BlenderOrbit] needs from the OS: middle button, relative mouse motion and wheel notches. */
interface OrbitOutput {
    fun middleButton(down: Boolean)
    fun moveBy(dx: Int, dy: Int)
    /** Positive = wheel down (Robot convention). */
    fun wheel(notches: Int)
}

/**
 * Emulates Blender's middle-mouse orbit from phone orientation updates (relative quaternions, w x y z).
 * The change between two updates, seen from the viewer, is turned into mouse motion: rotation about the
 * viewer's vertical axis gives horizontal motion, about the horizontal axis vertical motion
 * ([PIXELS_PER_DEGREE] each). The middle button is pressed when motion starts and released after
 * [IDLE_RELEASE_NANOS] without significant motion. Zoom becomes wheel notches (zoom in = wheel up).
 * All methods are synchronized: orientation arrives on the UDP thread, [poll] on a timer thread.
 */
class BlenderOrbit(private val out: OrbitOutput) {
    private var prev: DoubleArray? = null
    private var accX = 0.0
    private var accY = 0.0
    private var accWheel = 0.0
    private var middleDown = false
    private var lastMoveNanos = 0L

    @Synchronized
    fun onOrientation(w: Float, x: Float, y: Float, z: Float, nowNanos: Long) {
        val cur = normalized(w, x, y, z) ?: return
        val p = prev
        prev = cur
        if (p == null) return
        // Delta in the viewer's frame: q_cur * conj(q_prev)
        val d = multiply(cur, doubleArrayOf(p[0], -p[1], -p[2], -p[3]))
        val sign = if (d[0] < 0) -1.0 else 1.0
        // Rotation vector (angle * axis) of the shorter arc, in degrees
        val vlen = sqrt(d[1] * d[1] + d[2] * d[2] + d[3] * d[3])
        val angle = Math.toDegrees(2 * atan2(vlen, abs(d[0])))
        val k = if (vlen < 1e-12) 0.0 else angle / vlen * sign
        val rx = d[1] * k
        val ry = d[2] * k
        val magnitude = hypot(rx, ry)
        if (magnitude > MAX_STEP_DEGREES) return // recenter / reconnect jump, not a real movement
        if (magnitude < MIN_STEP_DEGREES) return
        if (!middleDown) {
            out.middleButton(true)
            middleDown = true
        }
        lastMoveNanos = nowNanos
        accX += ry * PIXELS_PER_DEGREE
        accY += rx * PIXELS_PER_DEGREE
        val ix = (accX + ROUNDING_SLACK * Math.signum(accX)).toInt()
        val iy = (accY + ROUNDING_SLACK * Math.signum(accY)).toInt()
        if (ix != 0 || iy != 0) {
            accX -= ix
            accY -= iy
            out.moveBy(ix, iy)
        }
    }

    @Synchronized
    fun onZoom(amount: Float) {
        if (amount.isNaN()) return
        accWheel -= amount // zoom in = wheel up = negative
        val n = accWheel.toInt()
        if (n != 0) {
            accWheel -= n
            out.wheel(n)
        }
    }

    /** Releases the middle button after [IDLE_RELEASE_NANOS] of stillness. Call regularly. */
    @Synchronized
    fun poll(nowNanos: Long) {
        if (middleDown && nowNanos - lastMoveNanos > IDLE_RELEASE_NANOS) releaseMiddle()
    }

    /** Mode change, disconnect or backend change: let go of everything. */
    @Synchronized
    fun reset() {
        releaseMiddle()
        prev = null
        accX = 0.0
        accY = 0.0
        accWheel = 0.0
    }

    private fun releaseMiddle() {
        if (middleDown) {
            middleDown = false
            out.middleButton(false)
        }
    }

    private fun normalized(w: Float, x: Float, y: Float, z: Float): DoubleArray? {
        val n = sqrt((w * w + x * x + y * y + z * z).toDouble())
        if (n < 1e-6 || n.isNaN()) return null
        return doubleArrayOf(w / n, x / n, y / n, z / n)
    }

    private fun multiply(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
        a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
        a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
        a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
    )

    companion object {
        const val PIXELS_PER_DEGREE = 8.0
        const val IDLE_RELEASE_NANOS = 200_000_000L
        /** Per-update rotation below this is treated as sensor noise. */
        const val MIN_STEP_DEGREES = 0.05
        /** Per-update rotation above this is a jump (re-center), not hand motion. */
        const val MAX_STEP_DEGREES = 30.0
        /** Float noise in the quaternions must not turn 8.0 px into 7.9999. */
        private const val ROUNDING_SLACK = 1e-3
    }
}
