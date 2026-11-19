package com.example.wandremote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.hypot

/** Tunables for relative pointing. Defaults: ~30 degrees of wrist turn at moderate speed ~= 1920 px. */
data class PointerTuning(
    /** Pixels per radian at low speed. */
    val baseSensitivity: Float = 2500f,
    /** gain = base * (1 + acceleration * |omega|), omega in rad/s. */
    val acceleration: Float = 0.4f,
    /** Angular speeds below this (rad/s) are ignored. */
    val deadZone: Float = 0.02f,
    /** Exponential smoothing factor per sample (1 = no smoothing). */
    val smoothing: Float = 0.6f,
)

/** Wraps SensorManager. Register only while the Pointer screen is visible. */
class SensorHub(context: Context) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximity: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val hasGyroscope: Boolean get() = gyro != null
    val hasProximity: Boolean get() = proximity != null

    @Volatile var tuning = PointerTuning()

    /** Set from the UI: thumb is on the clutch area. */
    @Volatile var touchClutch = false

    /** True while the proximity sensor reports "near". */
    @Volatile var proximityNear = false
        private set

    val clutched: Boolean get() = touchClutch || proximityNear

    private var onMove: ((Float, Float) -> Unit)? = null
    private var onProximityChange: ((Boolean) -> Unit)? = null

    private var lastTs = 0L
    private var smX = 0f
    private var smY = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> handleGyro(e)
                Sensor.TYPE_PROXIMITY -> {
                    val near = e.values[0] < e.sensor.maximumRange
                    proximityNear = near
                    onProximityChange?.invoke(near)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Callbacks run on the main thread (default sensor handler); [onMove] gets pixel deltas. */
    fun startPointer(onMove: (dx: Float, dy: Float) -> Unit, onProximityChange: (Boolean) -> Unit = {}) {
        this.onMove = onMove
        this.onProximityChange = onProximityChange
        lastTs = 0L
        smX = 0f
        smY = 0f
        gyro?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        proximity?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stopPointer() {
        sm.unregisterListener(listener)
        onMove = null
        onProximityChange = null
        proximityNear = false
        touchClutch = false
    }

    private fun handleGyro(e: SensorEvent) {
        val t = tuning
        val dtNs = if (lastTs == 0L) 0L else e.timestamp - lastTs
        lastTs = e.timestamp
        if (!clutched) {
            smX = 0f
            smY = 0f
            return
        }
        if (dtNs <= 0L || dtNs > 200_000_000L) return
        val dt = dtNs / 1e9f

        // Phone portrait, screen up, pointing forward: yaw = about Z (horizontal), pitch = about X (vertical).
        val wx = -e.values[2]
        val wy = -e.values[0]
        if (hypot(wx, wy) < t.deadZone) {
            smX = 0f
            smY = 0f
            return
        }
        smX += t.smoothing * (wx - smX)
        smY += t.smoothing * (wy - smY)
        val gain = t.baseSensitivity * (1f + t.acceleration * hypot(smX, smY))
        val dx = smX * dt * gain
        val dy = smY * dt * gain
        if (dx != 0f || dy != 0f) onMove?.invoke(dx, dy)
    }
}
