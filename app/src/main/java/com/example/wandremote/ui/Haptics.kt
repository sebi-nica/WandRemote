package com.example.wandremote.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.roundToInt

/** Vibration amplitude (1..255) for a 0..1 strength. */
internal fun amplitudeFor(strength: Float): Int = (strength.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(1, 255)

/** The app's only vibration entry point; honors the user's strength setting (0 = off). */
class Haptics(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** 0..1, set from the settings. */
    @Volatile var strength = 1f

    /** A short buzz of [ms] milliseconds. */
    fun pulse(ms: Long) {
        if (strength <= 0f || !vibrator.hasVibrator()) return
        val amplitude = if (vibrator.hasAmplitudeControl()) amplitudeFor(strength) else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
    }

    /** Three long buzzes, used by the presenter timer. */
    fun alert() {
        if (strength <= 0f || !vibrator.hasVibrator()) return
        val timings = longArrayOf(0, 400, 200, 400, 200, 400)
        val effect = if (vibrator.hasAmplitudeControl()) {
            val a = amplitudeFor(strength)
            VibrationEffect.createWaveform(timings, intArrayOf(0, a, 0, a, 0, a), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }

    /** The system's light detent tick (used per scroll notch). */
    fun tick(view: View) {
        if (strength > 0f) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
