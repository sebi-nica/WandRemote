package com.example.wandremote.sensors

/**
 * Relative laser: the dot starts at the screen centre when the laser is pressed and then follows how far the phone
 * has turned since. No Android imports, so it is unit-testable.
 *
 * Conventions: [Aim.yaw] is a heading in radians (clockwise from north), so turning right increases it and the dot
 * moves right; [Aim.pitch] is elevation (up positive), so tilting up moves the dot up (smaller y). Turning by
 * `rangeDeg` degrees sweeps the dot across the whole screen width; the vertical range is `rangeDeg * aspect`
 * degrees, where aspect = screen height / width, so the dot moves the same distance per degree in both axes.
 */
class RelativeLaser {
    private var anchor: Aim? = null

    /** Remembers [aim] as the screen centre. */
    fun press(aim: Aim) {
        anchor = aim
    }

    fun release() {
        anchor = null
    }

    val isActive: Boolean get() = anchor != null

    /** Screen position (0..1, top-left origin) for [aim]; the centre if [press] was not called. */
    fun point(aim: Aim, rangeDeg: Float, aspect: Float): ScreenPoint {
        val a = anchor ?: return ScreenPoint(0.5f, 0.5f)
        return mapLaser(
            dYawRad = wrapAngle(aim.yaw - a.yaw),
            dPitchRad = aim.pitch - a.pitch,
            rangeDeg = rangeDeg,
            aspect = aspect,
        )
    }

    companion object {
        private const val RAD_TO_DEG = 57.29578f

        /** The relative mapping for angle deltas in radians (yaw already wrapped to +-PI), clamped to 0..1. */
        fun mapLaser(dYawRad: Float, dPitchRad: Float, rangeDeg: Float, aspect: Float): ScreenPoint {
            val range = rangeDeg.coerceAtLeast(1f)
            val asp = if (aspect > 0f) aspect else 9f / 16f
            val x = 0.5f + dYawRad * RAD_TO_DEG / range
            val y = 0.5f - dPitchRad * RAD_TO_DEG / (range * asp)
            return ScreenPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }
}
