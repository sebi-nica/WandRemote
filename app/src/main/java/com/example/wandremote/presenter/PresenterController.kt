package com.example.wandremote.presenter

import android.content.Context
import android.hardware.Sensor
import android.os.SystemClock
import com.example.wandremote.network.PacketSink
import com.example.wandremote.sensors.Aim
import com.example.wandremote.sensors.AimHistory
import com.example.wandremote.sensors.AimSensor
import com.example.wandremote.sensors.FlickDetector
import com.example.wandremote.sensors.FlickDirection
import com.example.wandremote.sensors.FlickEvent
import com.example.wandremote.sensors.GyroBias
import com.example.wandremote.sensors.PointSmoother
import com.example.wandremote.sensors.ScreenCalibration
import com.example.wandremote.sensors.ScreenPoint
import com.example.wandremote.sensors.SensorMonitor
import com.example.wandremote.shared.Gesture
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.RemoteMode
import com.example.wandremote.ui.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One recognized gesture, shown in the Sensor debug screen. [confidence] is 0..1. */
data class DetectedGesture(val timeMs: Long, val gesture: Gesture, val confidence: Float)

/**
 * Runs Presenter mode while its screen is resumed: slide flicks from the gyro, and (when calibrated) absolute aim
 * and laser streaming from the game rotation vector. All callbacks arrive on the main thread.
 */
class PresenterController(
    context: Context,
    private val aimSensor: AimSensor,
    private val output: PacketSink,
    private val haptics: Haptics,
    private val setMode: (RemoteMode) -> Unit,
    private val gyroBias: () -> GyroBias,
    private val calibration: () -> ScreenCalibration?,
    private val updateCalibration: (ScreenCalibration) -> Unit,
) {
    private val gyro = SensorMonitor(context, listOf(Sensor.TYPE_GYROSCOPE))
    private val flicks = FlickDetector()
    private val history = AimHistory()
    private val smoother = PointSmoother()
    private var aimSession: AimSensor.Session? = null
    private var lastPoint = ScreenPoint(0.5f, 0.5f)

    private val _aimOn = MutableStateFlow(false)
    val aimOn: StateFlow<Boolean> = _aimOn.asStateFlow()

    private val _laserHeld = MutableStateFlow(false)
    val laserHeld: StateFlow<Boolean> = _laserHeld.asStateFlow()

    private val _flickEnabled = MutableStateFlow(true)
    val flickEnabled: StateFlow<Boolean> = _flickEnabled.asStateFlow()

    /** True from opening the screen until the user re-centers or skips: the yaw zero changes on every sensor restart. */
    private val _needsRecenter = MutableStateFlow(false)
    val needsRecenter: StateFlow<Boolean> = _needsRecenter.asStateFlow()

    private val _gestures = MutableStateFlow<List<DetectedGesture>>(emptyList())

    /** Detected gestures, newest first (at most [MAX_GESTURES]). */
    val gestures: StateFlow<List<DetectedGesture>> = _gestures.asStateFlow()

    val hasAimSensor: Boolean get() = aimSensor.isAvailable

    fun start() {
        setMode(RemoteMode.PRESENTER)
        history.clear()
        smoother.reset()
        flicks.reset()
        _needsRecenter.value = calibration() != null
        aimSession?.close()
        aimSession = aimSensor.open { timeNs, aim ->
            history.add(timeNs, aim)
            onAim(aim)
        }
        gyro.onSample = { _, v, timestampNs ->
            if (_flickEnabled.value) flicks.onSample(timestampNs, v[2] - gyroBias().z)?.let(::onFlick)
        }
        gyro.start()
    }

    fun stop() {
        gyro.stop()
        gyro.onSample = null
        aimSession?.close()
        aimSession = null
        setLaser(false)
        _aimOn.value = false
    }

    fun setAim(on: Boolean) {
        if (on && !canStream()) return
        if (on) smoother.reset()
        _aimOn.value = on
    }

    fun setFlickEnabled(on: Boolean) {
        flicks.reset()
        _flickEnabled.value = on
    }

    fun setLaser(on: Boolean) {
        if (on == _laserHeld.value) return
        if (on) {
            if (!canStream()) return
            smoother.reset()
            _laserHeld.value = true
            history.latest?.let { onAim(it) }
        } else {
            _laserHeld.value = false
            output.send(Packet.Laser(false, lastPoint.x, lastPoint.y))
        }
    }

    /** Prev/Next buttons (same packet the flick sends). */
    fun sendSlide(direction: FlickDirection) {
        haptics.pulse(20)
        output.send(Packet.GestureEvent(gestureFor(direction)))
    }

    /** The user aims at the screen centre and touched down at [touchNs]: shift the yaw so that point becomes the centre. */
    fun recenter(touchNs: Long = SystemClock.elapsedRealtimeNanos()) {
        val cal = calibration() ?: return
        val aim = history.averageBefore(touchNs) ?: return
        updateCalibration(cal.recentered(aim))
        _needsRecenter.value = false
        smoother.reset()
        haptics.pulse(30)
    }

    fun skipRecenter() {
        _needsRecenter.value = false
    }

    private fun canStream() = calibration() != null && !_needsRecenter.value && aimSession != null

    private fun onAim(aim: Aim) {
        val aimOn = _aimOn.value
        val laser = _laserHeld.value
        if (!aimOn && !laser) return
        val cal = calibration() ?: return
        if (_needsRecenter.value) return
        val p = smoother.next(cal.map(aim))
        lastPoint = p
        if (aimOn) output.send(Packet.Absolute(p.x, p.y))
        if (laser) output.send(Packet.Laser(true, p.x, p.y))
    }

    private fun onFlick(e: FlickEvent) {
        val gesture = gestureFor(e.direction)
        output.send(Packet.GestureEvent(gesture))
        haptics.pulse(30)
        _gestures.update { (listOf(DetectedGesture(System.currentTimeMillis(), gesture, e.confidence)) + it).take(MAX_GESTURES) }
    }

    private fun gestureFor(d: FlickDirection) = if (d == FlickDirection.LEFT) Gesture.FLICK_LEFT else Gesture.FLICK_RIGHT

    companion object {
        const val MAX_GESTURES = 20
    }
}
