package com.example.wandremote.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsAreAlreadyValid() {
        assertEquals(AppSettings(), AppSettings().coerced())
    }

    @Test
    fun coercedClampsEveryRange() {
        val low = AppSettings(
            sensitivity = -1f, acceleration = -1f, smoothing = -1f, deadZone = -1f,
            scrollDegreesPerNotch = 0f, knobSizeFraction = 0f, vibrationStrength = -1f,
        ).coerced()
        assertEquals(AppSettings.SENSITIVITY_RANGE.start, low.sensitivity, 0f)
        assertEquals(0f, low.acceleration, 0f)
        assertEquals(0f, low.smoothing, 0f)
        assertEquals(0f, low.deadZone, 0f)
        assertEquals(AppSettings.DEGREES_PER_NOTCH_RANGE.start, low.scrollDegreesPerNotch, 0f)
        assertEquals(AppSettings.KNOB_FRACTION_RANGE.start, low.knobSizeFraction, 0f)
        assertEquals(0f, low.vibrationStrength, 0f)

        val high = AppSettings(
            sensitivity = 99f, smoothing = 5f, scrollDegreesPerNotch = 999f, knobSizeFraction = 1f, vibrationStrength = 7f,
        ).coerced()
        assertEquals(AppSettings.SENSITIVITY_RANGE.endInclusive, high.sensitivity, 0f)
        assertEquals(AppSettings.SMOOTHING_RANGE.endInclusive, high.smoothing, 0f)
        assertEquals(AppSettings.DEGREES_PER_NOTCH_RANGE.endInclusive, high.scrollDegreesPerNotch, 0f)
        assertEquals(AppSettings.KNOB_FRACTION_RANGE.endInclusive, high.knobSizeFraction, 0f)
        assertEquals(1f, high.vibrationStrength, 0f)
    }

    @Test
    fun defaultTuningMatchesOriginalPointerFeel() {
        val t = AppSettings().toPointerTuning()
        assertEquals(2500f, t.baseSensitivity, 0.01f)
        assertEquals(0.6f, t.smoothing, 0.0001f)
        assertEquals(0.02f, t.deadZone, 0f)
    }

    @Test
    fun sensitivityScalesBase() {
        assertEquals(5000f, AppSettings(sensitivity = 2f).toPointerTuning().baseSensitivity, 0.01f)
    }

    @Test
    fun withDefaultTuningKeepsHandednessVibrationAndBias() {
        val s = AppSettings(sensitivity = 2f, invertScroll = true, leftHanded = true, vibrationStrength = 0.2f, gyroBiasTimeMs = 5L)
        val r = s.withDefaultTuning()
        assertEquals(AppSettings.DEFAULT_SENSITIVITY, r.sensitivity, 0f)
        assertFalse(r.invertScroll)
        assertTrue(r.leftHanded)
        assertEquals(0.2f, r.vibrationStrength, 0f)
        assertEquals(5L, r.gyroBiasTimeMs)
    }

    @Test
    fun laserRangeDefaultsClampsAndResets() {
        assertEquals(30f, AppSettings().laserRangeDeg, 0f)
        assertEquals(10f, AppSettings(laserRangeDeg = 1f).coerced().laserRangeDeg, 0f)
        assertEquals(90f, AppSettings(laserRangeDeg = 500f).coerced().laserRangeDeg, 0f)
        assertEquals(30f, AppSettings(laserRangeDeg = 60f).withDefaultTuning().laserRangeDeg, 0f)
    }
}
