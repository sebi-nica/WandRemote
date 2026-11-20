package com.example.wandremote.ui.screens.threed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.wandremote.sensors.AxisLock
import com.example.wandremote.sensors.Quat
import com.example.wandremote.sensors.TiltZoom
import com.example.wandremote.sensors.relativeTo
import com.example.wandremote.shared.Packet

/**
 * The 3D screen's logic, driven by orientation samples on the main thread: keeps the re-center reference, applies the
 * axis locks, rate-limits the orientation stream and turns tilt into zoom while "hold to zoom" is pressed.
 * [streaming] is read per sample (false = preview only).
 */
class ThreeDSession(private val send: (Packet) -> Unit, private val streaming: () -> Boolean) {
    /** What the preview draws: the locked relative orientation. Read from the draw scope each frame. */
    var display: Quat = Quat.IDENTITY
        private set

    /** True while the zoom button is held (orientation is frozen). */
    var zooming by mutableStateOf(false)
        private set

    private var lock = AxisLock()
    private var current: Quat? = null
    private var reference: Quat? = null
    private var frozenRelative = Quat.IDENTITY
    private var lastSent: Quat? = null
    private var lastSentNs = 0L
    private var lastSampleNs = 0L
    private var zoomAccumulator = 0.0
    private val tiltZoom = TiltZoom()

    /** The screen opened or resumed: the next sample becomes the reference. */
    fun onResume() {
        reference = null
        lastSent = null
        display = Quat.IDENTITY
    }

    fun onPause() {
        if (zooming) endZoom()
    }

    fun setLock(newLock: AxisLock) {
        if (newLock == lock) return
        lock = newLock
        val c = current ?: return
        if (!zooming) emit(c, lastSampleNs, force = true)
    }

    fun recenter() {
        val c = current ?: return
        reference = c
        frozenRelative = Quat.IDENTITY
        display = Quat.IDENTITY
        if (!zooming && streaming()) sendOrientation(Quat.IDENTITY, lastSampleNs)
    }

    fun beginZoom() {
        val c = current ?: return
        frozenRelative = relativeTo(reference ?: c, c)
        tiltZoom.begin(c)
        zoomAccumulator = 0.0
        zooming = true
    }

    fun endZoom() {
        if (!zooming) return
        zooming = false
        tiltZoom.end()
        val c = current ?: return
        // Pick the reference so the orientation continues from the frozen value without a jump.
        reference = c * frozenRelative.conjugate()
        lastSent = null
    }

    fun onSample(q: Quat, timestampNs: Long) {
        val dtSeconds = if (lastSampleNs == 0L) 0.0 else (timestampNs - lastSampleNs) / 1e9
        lastSampleNs = timestampNs
        current = q
        if (reference == null) reference = q
        if (zooming) {
            zoomAccumulator += tiltZoom.update(q, dtSeconds)
            if (zoomAccumulator != 0.0 && streaming()) {
                send(Packet.Zoom(zoomAccumulator.toFloat()))
                zoomAccumulator = 0.0
            } else if (!streaming()) {
                zoomAccumulator = 0.0
            }
            return
        }
        emit(q, timestampNs, force = false)
    }

    private fun emit(q: Quat, timestampNs: Long, force: Boolean) {
        val ref = reference ?: return
        val shown = lock.apply(relativeTo(ref, q))
        display = shown
        if (!streaming()) return
        val last = lastSent
        if (!force && last != null) {
            if (timestampNs - lastSentNs < MIN_INTERVAL_NS) return
            if (shown.angleTo(last) < MIN_CHANGE_RADIANS) return
        }
        sendOrientation(shown, timestampNs)
    }

    private fun sendOrientation(q: Quat, timestampNs: Long) {
        val p = q.normalized().withPositiveW()
        send(Packet.Orientation(p.w.toFloat(), p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
        lastSent = q
        lastSentNs = timestampNs
    }

    companion object {
        /** About 60 Hz at most. */
        const val MIN_INTERVAL_NS = 16_000_000L
        /** 0.1 degrees. */
        val MIN_CHANGE_RADIANS = Math.toRadians(0.1)
    }
}

