package com.example.wandremote.pointer

import android.os.SystemClock
import com.example.wandremote.network.PacketSink
import com.example.wandremote.sensors.Aim
import com.example.wandremote.sensors.AimHistory
import com.example.wandremote.sensors.AimSensor
import com.example.wandremote.sensors.PointSmoother
import com.example.wandremote.sensors.ScreenCalibration
import com.example.wandremote.sensors.ScreenPoint
import com.example.wandremote.shared.Packet
import com.example.wandremote.ui.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The laser pointer of the Pointer screen: while it is held, the calibrated aim (game rotation vector mapped through
 * the corner [ScreenCalibration]) is streamed as [Packet.Laser]. Also owns the yaw re-center, because the game
 * rotation vector's yaw zero changes whenever the sensor restarts. Call [start]/[stop] with the screen's resume and
 * pause; everything runs on the main thread.
 */
class LaserController(
    private val aimSensor: AimSensor,
    private val output: PacketSink,
    private val haptics: Haptics,
    private val calibration: () -> ScreenCalibration?,
    private val updateCalibration: (ScreenCalibration) -> Unit,
) {
    private val history = AimHistory()
    private val smoother = PointSmoother()
    private var session: AimSensor.Session? = null
    private var lastPoint = ScreenPoint(0.5f, 0.5f)
    private var startedAtMs = 0L
    private var knownProfileSavedAtMs: Long? = null

    private val _laserHeld = MutableStateFlow(false)
    val laserHeld: StateFlow<Boolean> = _laserHeld.asStateFlow()

    /**
     * True from the moment the screen resumes until the user re-centers (or captures fresh corners): the yaw zero
     * changes on every sensor restart, so a stored calibration points slightly off until then.
     */
    private val _needsRecenter = MutableStateFlow(false)
    val needsRecenter: StateFlow<Boolean> = _needsRecenter.asStateFlow()

    val hasAimSensor: Boolean get() = aimSensor.isAvailable

    /** Opens the aim sensor. [profileSavedAtMs] is the stored calibration's capture time, null if there is none. */
    fun start(profileSavedAtMs: Long?) {
        history.clear()
        smoother.reset()
        startedAtMs = System.currentTimeMillis()
        knownProfileSavedAtMs = profileSavedAtMs
        _needsRecenter.value = calibration() != null
        session?.close()
        session = aimSensor.open { timeNs, aim ->
            history.add(timeNs, aim)
            if (_laserHeld.value) onAim(aim)
        }
    }

    fun stop() {
        setHeld(false)
        session?.close()
        session = null
    }

    /**
     * Call whenever the stored profile changes. A profile captured during this session (the corner wizard) is already
     * in the current yaw frame, so it clears [needsRecenter]; a different older profile (another PC) sets it again.
     */
    fun onProfileChanged(savedAtMs: Long?) {
        if (savedAtMs == knownProfileSavedAtMs) return
        knownProfileSavedAtMs = savedAtMs
        _needsRecenter.value = savedAtMs != null && savedAtMs < startedAtMs
    }

    /** True if a laser can be shown right now: a calibration exists and the aim sensor is running. */
    fun canStream(): Boolean = calibration() != null && session != null

    /** Starts or stops streaming the laser. Returns false if it could not start (no calibration / no sensor). */
    fun setHeld(on: Boolean): Boolean {
        if (on == _laserHeld.value) return true
        if (on) {
            if (!canStream()) return false
            smoother.reset()
            _laserHeld.value = true
            history.latest?.let(::onAim)
        } else {
            _laserHeld.value = false
            output.send(Packet.Laser(false, lastPoint.x, lastPoint.y))
        }
        return true
    }

    /**
     * The user aims at the screen centre and touched down at [touchNs]: shift the yaw so that point becomes the
     * centre. Returns false if there is no calibration or no aim data yet.
     */
    fun recenter(touchNs: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
        val cal = calibration() ?: return false
        val aim = history.averageBefore(touchNs) ?: return false
        updateCalibration(cal.recentered(aim))
        _needsRecenter.value = false
        smoother.reset()
        haptics.pulse(30)
        return true
    }

    private fun onAim(aim: Aim) {
        val cal = calibration() ?: return
        val p = smoother.next(cal.map(aim))
        lastPoint = p
        output.send(Packet.Laser(true, p.x, p.y))
    }
}
