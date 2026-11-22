package com.example.wandremote.ui.screens.pointer.layout

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Geometry of one control, in NORMALIZED coordinates of the Pointer content area:
 *  - x values are a fraction of the area's WIDTH (0 = left edge, 1 = right edge),
 *  - y values are a fraction of the area's HEIGHT (0 = top edge, 1 = bottom edge),
 *  - every length (radius, corner radius, ring radii) is a fraction of the area's WIDTH, so a [Circle] stays a
 *    circle whatever the aspect ratio.
 *
 * Angles are screen angles in degrees: 0 = east, clockwise positive (y points down), 90 = south, 270 = north.
 *
 * Plain data, no Compose types, so a layout can later be stored as JSON.
 */
sealed interface ControlShape {
    /** True if the pixel position ([px], [py]) is inside this shape, for an area of [w] x [h] pixels. */
    fun contains(px: Float, py: Float, w: Float, h: Float): Boolean

    /** This shape flipped left-right (x becomes 1 - x). */
    fun mirrored(): ControlShape

    data class Circle(val cx: Float, val cy: Float, val r: Float) : ControlShape {
        override fun contains(px: Float, py: Float, w: Float, h: Float) =
            hypot(px - cx * w, py - cy * h) <= r * w

        override fun mirrored() = copy(cx = 1f - cx)
    }

    data class RoundRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val cornerRadius: Float,
    ) : ControlShape {
        override fun contains(px: Float, py: Float, w: Float, h: Float): Boolean {
            val l = left * w
            val t = top * h
            val r = right * w
            val b = bottom * h
            if (px < l || px > r || py < t || py > b) return false
            val cr = minOf(cornerRadius * w, (r - l) / 2f, (b - t) / 2f)
            // Distance from the nearest point of the rectangle shrunk by the corner radius.
            val nx = px.coerceIn(l + cr, r - cr)
            val ny = py.coerceIn(t + cr, b - cr)
            return hypot(px - nx, py - ny) <= cr
        }

        override fun mirrored() = copy(left = 1f - right, right = 1f - left)
    }

    /** A slice of a ring: radii [innerR]..[outerR] around ([cx], [cy]), from [startDeg] sweeping clockwise by [sweepDeg]. */
    data class RingSector(
        val cx: Float,
        val cy: Float,
        val innerR: Float,
        val outerR: Float,
        val startDeg: Float,
        val sweepDeg: Float,
    ) : ControlShape {
        override fun contains(px: Float, py: Float, w: Float, h: Float): Boolean {
            val dx = px - cx * w
            val dy = py - cy * h
            val dist = hypot(dx, dy)
            if (dist < innerR * w || dist > outerR * w) return false
            val angle = normalizeDeg(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
            return normalizeDeg(angle - startDeg) <= sweepDeg
        }

        /** Angle a becomes 180 - a, so the sector [start, start + sweep] becomes [180 - start - sweep, 180 - start]. */
        override fun mirrored() = copy(cx = 1f - cx, startDeg = normalizeDeg(180f - startDeg - sweepDeg))
    }
}

/** Maps any angle to [0, 360). */
internal fun normalizeDeg(deg: Float): Float {
    val a = deg % 360f
    return if (a < 0f) a + 360f else a
}
