package com.example.wandremote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Streams the phone's pointing direction ([Aim]) for Presenter mode and corner calibration.
 *
 * Uses the game rotation vector (gyro + accelerometer, no magnetometer, so no compass jumps), falling back to the
 * rotation vector when it is missing. The sensor is registered while at least one session is open, and all sessions
 * share one registration so they see the same yaw zero. Callbacks run on the main thread.
 */
class AimSensor(context: Context) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = sensor != null

    /** Receives each sample with its arrival time ([SystemClock.elapsedRealtimeNanos]). */
    fun interface Listener {
        fun onAim(timeNs: Long, aim: Aim)
    }

    inner class Session(private val listener: Listener) : AutoCloseable {
        internal fun deliver(timeNs: Long, aim: Aim) = listener.onAim(timeNs, aim)

        override fun close() {
            if (sessions.remove(this) && sessions.isEmpty()) sm.unregisterListener(sensorListener)
        }
    }

    private val sessions = CopyOnWriteArrayList<Session>()
    private val quaternion = FloatArray(4)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getQuaternionFromVector(quaternion, e.values)
            val aim = aimFromQuaternion(quaternion[0], quaternion[1], quaternion[2], quaternion[3])
            val now = SystemClock.elapsedRealtimeNanos()
            for (s in sessions) s.deliver(now, aim)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Starts (or joins) the sensor stream; close the returned session when done. */
    fun open(listener: Listener): Session {
        val session = Session(listener)
        if (sessions.isEmpty()) sensor?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        sessions.add(session)
        return session
    }
}
