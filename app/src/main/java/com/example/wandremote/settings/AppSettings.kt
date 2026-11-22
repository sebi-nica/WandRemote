package com.example.wandremote.settings

import com.example.wandremote.sensors.GyroBias
import com.example.wandremote.sensors.PointerTuning

/** User-adjustable options, persisted by [SettingsRepository]. Always valid after [coerced]. */
data class AppSettings(
    /** Multiplier on [PointerTuning]'s base sensitivity (1 = default feel). */
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    /** Extra gain per rad/s of angular speed. */
    val acceleration: Float = DEFAULT_ACCELERATION,
    /** 0 = raw gyro, higher = smoother but laggier. Inverse of [PointerTuning.smoothing]. */
    val smoothing: Float = DEFAULT_SMOOTHING,
    /** Angular speeds below this (rad/s) are ignored. */
    val deadZone: Float = DEFAULT_DEAD_ZONE,
    val scrollDegreesPerNotch: Float = DEFAULT_DEGREES_PER_NOTCH,
    /** Knob radius as a fraction of the content width. */
    val knobSizeFraction: Float = DEFAULT_KNOB_FRACTION,
    val invertScroll: Boolean = false,
    /** Flips the whole Pointer button layout horizontally (stored under the old "left handed" key). */
    val leftHanded: Boolean = false,
    /** 0 = no vibration, 1 = full strength. */
    val vibrationStrength: Float = DEFAULT_VIBRATION,
    /** Hand turn (degrees) that sweeps the laser from one screen edge to the other. */
    val laserRangeDeg: Float = DEFAULT_LASER_RANGE,
    val gyroBias: GyroBias = GyroBias.ZERO,
    /** Epoch millis of the last gyro bias calibration, 0 if never. */
    val gyroBiasTimeMs: Long = 0L,
) {
    fun coerced() = copy(
        sensitivity = sensitivity.coerceIn(SENSITIVITY_RANGE),
        acceleration = acceleration.coerceIn(ACCELERATION_RANGE),
        smoothing = smoothing.coerceIn(SMOOTHING_RANGE),
        deadZone = deadZone.coerceIn(DEAD_ZONE_RANGE),
        scrollDegreesPerNotch = scrollDegreesPerNotch.coerceIn(DEGREES_PER_NOTCH_RANGE),
        knobSizeFraction = knobSizeFraction.coerceIn(KNOB_FRACTION_RANGE),
        laserRangeDeg = laserRangeDeg.coerceIn(LASER_RANGE_DEG_RANGE),
        vibrationStrength = vibrationStrength.coerceIn(0f, 1f),
    )

    fun toPointerTuning() = PointerTuning(
        baseSensitivity = PointerTuning.BASE_SENSITIVITY * sensitivity,
        acceleration = acceleration,
        deadZone = deadZone,
        smoothing = 1f - smoothing,
    )

    /** Resets everything the Config tab edits; keeps layout flip, vibration and gyro bias. */
    fun withDefaultTuning() = copy(
        sensitivity = DEFAULT_SENSITIVITY,
        acceleration = DEFAULT_ACCELERATION,
        smoothing = DEFAULT_SMOOTHING,
        deadZone = DEFAULT_DEAD_ZONE,
        scrollDegreesPerNotch = DEFAULT_DEGREES_PER_NOTCH,
        knobSizeFraction = DEFAULT_KNOB_FRACTION,
        invertScroll = false,
        laserRangeDeg = DEFAULT_LASER_RANGE,
    )

    companion object {
        const val DEFAULT_SENSITIVITY = 1f
        const val DEFAULT_ACCELERATION = 0.4f
        const val DEFAULT_SMOOTHING = 0.4f
        const val DEFAULT_DEAD_ZONE = 0.02f
        const val DEFAULT_DEGREES_PER_NOTCH = 24f
        const val DEFAULT_KNOB_FRACTION = 0.20f
        const val DEFAULT_VIBRATION = 0.7f
        const val DEFAULT_LASER_RANGE = 30f

        val SENSITIVITY_RANGE = 0.25f..3f
        val ACCELERATION_RANGE = 0f..1.5f
        val SMOOTHING_RANGE = 0f..0.9f
        val DEAD_ZONE_RANGE = 0f..0.15f
        val DEGREES_PER_NOTCH_RANGE = 10f..60f
        val KNOB_FRACTION_RANGE = 0.14f..0.28f
        val LASER_RANGE_DEG_RANGE = 10f..90f
    }
}
