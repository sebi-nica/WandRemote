package com.example.wandremote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/** Human-readable name of a `SensorManager.SENSOR_STATUS_*` value (-1 = no reading yet). */
fun accuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
    else -> "No data"
}

class SensorInfo(val name: String, val vendor: String)

/** What the debug screen draws for one sensor. For the rotation vector, [latestRaw] is the quaternion (w, x, y, z). */
class ChannelSnapshot(
    val info: SensorInfo?,
    val accuracy: Int,
    /** [SystemClock.elapsedRealtimeNanos] when taken; the right edge of [window]. */
    val nowNs: Long,
    val window: SampleWindow,
    val latestRaw: FloatArray,
    val latestFiltered: FloatArray,
)

/**
 * Listens to a set of sensors while started and keeps the last [WINDOW_NS] of samples for graphing.
 * Everything runs on the main thread (default sensor handler), so no locking is needed; the UI polls [snapshot].
 */
class SensorMonitor(context: Context, types: List<Int>) {
    private class Channel(val sensor: Sensor?) {
        val buffer = SampleBuffer(CAPACITY)
        val lowPass = LowPass3()
        var accuracy = NO_DATA
        val raw = FloatArray(3)
        val filtered = FloatArray(3)
        val quaternion = FloatArray(4)
    }

    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val channels = types.associateWith { Channel(sm.getDefaultSensor(it)) }

    /** Gyro bias removed before the gyro's "filtered" trace. */
    @Volatile var gyroBias = GyroBias.ZERO

    /** Low-pass factor for the gyro's "filtered" trace (the pointer pipeline's per-sample smoothing; 1 = none). */
    @Volatile var gyroAlpha = 1f

    /** Called for every raw sample (not the rotation vector). */
    var onSample: ((type: Int, values: FloatArray, timestampNs: Long) -> Unit)? = null

    /** Called when a sensor's accuracy changes or first becomes known. */
    var onAccuracy: ((type: Int, accuracy: Int) -> Unit)? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val type = e.sensor.type
            val ch = channels[type] ?: return
            setAccuracy(type, ch, e.accuracy)
            if (type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getQuaternionFromVector(ch.quaternion, e.values)
                return
            }
            val v = e.values
            ch.raw[0] = v[0]
            ch.raw[1] = v[1]
            ch.raw[2] = v[2]
            if (type == Sensor.TYPE_GYROSCOPE) {
                val b = gyroBias
                ch.lowPass.apply(gyroAlpha, v[0] - b.x, v[1] - b.y, v[2] - b.z, ch.filtered)
            } else {
                ch.lowPass.apply(FIELD_ALPHA, v[0], v[1], v[2], ch.filtered)
            }
            ch.buffer.add(e.timestamp, ch.raw, ch.filtered)
            onSample?.invoke(type, v, e.timestamp)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            val type = sensor?.type ?: return
            channels[type]?.let { setAccuracy(type, it, accuracy) }
        }
    }

    private fun setAccuracy(type: Int, ch: Channel, accuracy: Int) {
        if (ch.accuracy == accuracy) return
        ch.accuracy = accuracy
        onAccuracy?.invoke(type, accuracy)
    }

    fun start() {
        for (ch in channels.values) {
            ch.buffer.clear()
            ch.lowPass.reset()
            ch.accuracy = NO_DATA
            ch.sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stop() = sm.unregisterListener(listener)

    fun snapshot(type: Int): ChannelSnapshot {
        val ch = channels.getValue(type)
        val info = ch.sensor?.let { SensorInfo(it.name, it.vendor) }
        val latestRaw = if (type == Sensor.TYPE_ROTATION_VECTOR) ch.quaternion else ch.raw
        val nowNs = SystemClock.elapsedRealtimeNanos()
        return ChannelSnapshot(
            info = info,
            accuracy = ch.accuracy,
            nowNs = nowNs,
            window = ch.buffer.window(nowNs, WINDOW_NS),
            latestRaw = latestRaw.copyOf(),
            latestFiltered = ch.filtered.copyOf(),
        )
    }

    companion object {
        const val WINDOW_NS = 5_000_000_000L
        const val NO_DATA = -2 // SENSOR_STATUS_NO_CONTACT is -1; keep our own marker distinct

        /** Enough for 5 s at 300 Hz. */
        private const val CAPACITY = 1500

        /** Low-pass for accelerometer (gravity) and magnetometer traces. */
        private const val FIELD_ALPHA = 0.1f
    }
}
