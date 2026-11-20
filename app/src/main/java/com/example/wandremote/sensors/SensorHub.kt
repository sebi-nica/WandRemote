package com.example.wandremote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.CopyOnWriteArrayList

/** Tunables for relative pointing. Defaults: ~30 degrees of wrist turn at moderate speed ~= 1920 px. */
data class PointerTuning(
    /** Pixels per radian at low speed. */
    val baseSensitivity: Float = BASE_SENSITIVITY,
    /** gain = base * (1 + acceleration * |omega|), omega in rad/s. */
    val acceleration: Float = 0.4f,
    /** Angular speeds below this (rad/s) are ignored. */
    val deadZone: Float = 0.02f,
    /** Exponential smoothing factor per sample (1 = no smoothing). */
    val smoothing: Float = 0.6f,
) {
    companion object {
        const val BASE_SENSITIVITY = 2500f
    }
}

/**
 * Wraps the gyroscope for the pointer pipeline. Every screen that wants pointer movement opens its own
 * [PointerSession]; the sensor is registered while at least one session is open.
 */
class SensorHub(context: Context) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val hasGyroscope: Boolean get() = gyro != null

    /** Read live on every sample, so changes apply immediately. */
    @Volatile var tuning = PointerTuning()

    /** Subtracted from every gyro sample before the pipeline. */
    @Volatile var gyroBias = GyroBias.ZERO

    private val sessions = CopyOnWriteArrayList<PointerSession>()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            for (session in sessions) session.onGyro(e)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Starts delivering pixel deltas to [onMove] on the main thread. Close the session when the screen pauses. */
    fun openPointer(onMove: (dx: Float, dy: Float) -> Unit): PointerSession {
        val session = PointerSession(onMove)
        if (sessions.isEmpty()) gyro?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sessions.add(session)
        return session
    }

    inner class PointerSession internal constructor(private val onMove: (Float, Float) -> Unit) {
        /** Gyro motion is only reported while true. Starts off. */
        @Volatile var movementEnabled = false
            set(value) {
                field = value
                if (!value) pipeline.reset()
            }

        private val pipeline = PointerPipeline()
        private var lastTs = 0L

        internal fun onGyro(e: SensorEvent) {
            val dtNs = if (lastTs == 0L) 0L else e.timestamp - lastTs
            lastTs = e.timestamp
            if (!movementEnabled || dtNs <= 0L || dtNs > MAX_DT_NS) return
            val bias = gyroBias
            // Phone portrait, screen up, pointing forward: yaw = about Z (horizontal), pitch = about X (vertical).
            val wx = -(e.values[2] - bias.z)
            val wy = -(e.values[0] - bias.x)
            pipeline.step(wx, wy, dtNs / 1e9f, tuning)?.let { onMove(it.dx, it.dy) }
        }

        fun close() {
            movementEnabled = false
            if (sessions.remove(this) && sessions.isEmpty()) sm.unregisterListener(listener)
        }
    }

    private companion object {
        const val MAX_DT_NS = 200_000_000L
    }
}
