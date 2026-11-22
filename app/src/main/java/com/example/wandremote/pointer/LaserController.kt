package com.example.wandremote.pointer

import com.example.wandremote.network.PacketSink
import com.example.wandremote.sensors.Aim
import com.example.wandremote.sensors.AimSensor
import com.example.wandremote.sensors.PointSmoother
import com.example.wandremote.sensors.RelativeLaser
import com.example.wandremote.sensors.ScreenPoint
import com.example.wandremote.shared.Packet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The laser pointer of the Pointer screen: while it is held, the dot starts at the screen centre and follows how far
 * the phone has turned since the press ([RelativeLaser], game rotation vector), streamed as [Packet.Laser].
 * Call [start]/[stop] with the screen's resume and pause; everything runs on the main thread.
 *
 * [rangeDeg] is the hand turn (degrees) that sweeps the dot across the screen width; [aspect] is the PC screen's
 * height / width.
 */
class LaserController(
    private val aimSensor: AimSensor,
    private val output: PacketSink,
    private val rangeDeg: () -> Float,
    private val aspect: () -> Float,
) {
    private val laser = RelativeLaser()
    private val smoother = PointSmoother()
    private var session: AimSensor.Session? = null
    private var latest: Aim? = null
    private var lastPoint = ScreenPoint(0.5f, 0.5f)

    private val _laserHeld = MutableStateFlow(false)
    val laserHeld: StateFlow<Boolean> = _laserHeld.asStateFlow()

    val hasAimSensor: Boolean get() = aimSensor.isAvailable

    /** Opens the aim sensor. */
    fun start() {
        latest = null
        session?.close()
        session = aimSensor.open { _, aim ->
            latest = aim
            if (_laserHeld.value) onAim(aim)
        }
    }

    fun stop() {
        setHeld(false)
        session?.close()
        session = null
        latest = null
    }

    /** Starts or stops streaming the laser. Returns false if it could not start (no sensor data yet). */
    fun setHeld(on: Boolean): Boolean {
        if (on == _laserHeld.value) return true
        if (on) {
            val aim = latest ?: return false
            laser.press(aim)
            smoother.reset()
            lastPoint = smoother.next(ScreenPoint(0.5f, 0.5f))
            _laserHeld.value = true
            output.send(Packet.Laser(true, lastPoint.x, lastPoint.y))
        } else {
            _laserHeld.value = false
            laser.release()
            output.send(Packet.Laser(false, lastPoint.x, lastPoint.y))
        }
        return true
    }

    private fun onAim(aim: Aim) {
        val p = smoother.next(laser.point(aim, rangeDeg(), aspect()))
        lastPoint = p
        output.send(Packet.Laser(true, p.x, p.y))
    }
}
