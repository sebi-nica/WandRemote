package com.example.wandremote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Delivers the phone's absolute orientation as a [Quat] (device frame -> world frame). Prefers the game rotation
 * vector (no magnetometer, so no heading jumps near magnets) and falls back to the rotation vector.
 * Callbacks run on the main thread.
 */
class OrientationSensor(context: Context, private val onOrientation: (q: Quat, timestampNs: Long) -> Unit) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val buf = FloatArray(4)

    val available get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getQuaternionFromVector(buf, e.values)
            onOrientation(Quat(buf[0].toDouble(), buf[1].toDouble(), buf[2].toDouble(), buf[3].toDouble()), e.timestamp)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(listener)
}
