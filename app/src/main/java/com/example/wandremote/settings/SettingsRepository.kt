package com.example.wandremote.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.wandremote.sensors.GyroBias
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [AppSettings] persisted in SharedPreferences; every change is validated, saved and published at once. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: AppSettings.() -> AppSettings) {
        val updated = _settings.value.transform().coerced()
        save(updated)
        _settings.value = updated
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sensitivity = prefs.getFloat(SENSITIVITY, d.sensitivity),
            acceleration = prefs.getFloat(ACCELERATION, d.acceleration),
            smoothing = prefs.getFloat(SMOOTHING, d.smoothing),
            deadZone = prefs.getFloat(DEAD_ZONE, d.deadZone),
            scrollDegreesPerNotch = prefs.getFloat(DEGREES_PER_NOTCH, d.scrollDegreesPerNotch),
            knobSizeFraction = prefs.getFloat(KNOB_SIZE, d.knobSizeFraction),
            invertScroll = prefs.getBoolean(INVERT_SCROLL, d.invertScroll),
            leftHanded = prefs.getBoolean(LEFT_HANDED, d.leftHanded),
            vibrationStrength = prefs.getFloat(VIBRATION, d.vibrationStrength),
            laserRangeDeg = prefs.getFloat(LASER_RANGE, d.laserRangeDeg),
            gyroBias = GyroBias(
                prefs.getFloat(BIAS_X, 0f),
                prefs.getFloat(BIAS_Y, 0f),
                prefs.getFloat(BIAS_Z, 0f),
            ),
            gyroBiasTimeMs = prefs.getLong(BIAS_TIME, 0L),
        ).coerced()
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putFloat(SENSITIVITY, s.sensitivity)
            .putFloat(ACCELERATION, s.acceleration)
            .putFloat(SMOOTHING, s.smoothing)
            .putFloat(DEAD_ZONE, s.deadZone)
            .putFloat(DEGREES_PER_NOTCH, s.scrollDegreesPerNotch)
            .putFloat(KNOB_SIZE, s.knobSizeFraction)
            .putBoolean(INVERT_SCROLL, s.invertScroll)
            .putBoolean(LEFT_HANDED, s.leftHanded)
            .putFloat(VIBRATION, s.vibrationStrength)
            .putFloat(LASER_RANGE, s.laserRangeDeg)
            .putFloat(BIAS_X, s.gyroBias.x)
            .putFloat(BIAS_Y, s.gyroBias.y)
            .putFloat(BIAS_Z, s.gyroBias.z)
            .putLong(BIAS_TIME, s.gyroBiasTimeMs)
            .apply()
    }

    private companion object {
        const val FILE = "app_settings"
        const val SENSITIVITY = "pointer_sensitivity"
        const val ACCELERATION = "pointer_acceleration"
        const val SMOOTHING = "pointer_smoothing"
        const val DEAD_ZONE = "pointer_dead_zone"
        const val DEGREES_PER_NOTCH = "scroll_degrees_per_notch"
        const val KNOB_SIZE = "knob_size_fraction"
        const val INVERT_SCROLL = "invert_scroll"
        const val LEFT_HANDED = "left_handed"
        const val VIBRATION = "vibration_strength"
        const val LASER_RANGE = "laser_range_deg"
        const val BIAS_X = "gyro_bias_x"
        const val BIAS_Y = "gyro_bias_y"
        const val BIAS_Z = "gyro_bias_z"
        const val BIAS_TIME = "gyro_bias_time_ms"
    }
}
