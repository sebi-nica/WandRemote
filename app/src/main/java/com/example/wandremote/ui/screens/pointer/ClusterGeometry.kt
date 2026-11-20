package com.example.wandremote.ui.screens.pointer

/*
 * Angles are screen angles in degrees: 0 = east, clockwise positive (y points down), range [0, 360).
 * The right-handed cluster sits in the bottom-right corner with the click ring covering west..north;
 * left-handed mode mirrors everything horizontally (angle a becomes 180 - a).
 */

internal enum class RingButton { LEFT, RIGHT }

/** Right-handed ring layout: the button ring spans [RING_START, RING_END], split at [RING_SPLIT]. */
private const val RING_START = 170f
private const val RING_END = 280f
private const val RING_SPLIT = 225f

/** Mirrors a screen angle across the vertical axis. */
internal fun mirrorAngle(deg: Float): Float = (((180f - deg) % 360f) + 360f) % 360f

/** Which ring button a touch at [angleDeg] hits, or null if it is outside the ring's angular span. */
internal fun ringButtonAt(angleDeg: Float, leftHanded: Boolean): RingButton? {
    val a = if (leftHanded) mirrorAngle(angleDeg) else angleDeg
    return when {
        a !in RING_START..RING_END -> null
        a < RING_SPLIT -> RingButton.LEFT // lower half, nearest the screen edge
        else -> RingButton.RIGHT
    }
}

/** Start angle for drawing an arc that spans [start], [start] + [sweep] in right-handed layout. */
internal fun arcStartAngle(start: Float, sweep: Float, leftHanded: Boolean): Float =
    if (leftHanded) mirrorAngle(start + sweep) else start

/** Horizontal center of the knob inside a content area of [width] px. */
internal fun clusterCenterX(width: Float, knobRadius: Float, leftHanded: Boolean): Float =
    if (leftHanded) knobRadius else width - knobRadius
