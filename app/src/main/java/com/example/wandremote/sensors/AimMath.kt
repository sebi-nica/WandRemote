package com.example.wandremote.sensors

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Pure math for the laser pointer's aiming (no Android imports, so it is unit-testable).
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

/** Normalized screen position, 0..1 with (0,0) = top-left. */
data class ScreenPoint(val x: Float, val y: Float)

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
