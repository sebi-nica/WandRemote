package com.example.wandremote.sensors

import kotlin.math.abs

enum class FlickDirection { LEFT, RIGHT }

/** A detected flick; [confidence] is 0..1 (0.5 = just over the threshold, 1 = twice the threshold or more). */
data class FlickEvent(val direction: FlickDirection, val confidence: Float, val timestampNs: Long)

/**
 * Detects a quick wrist flick from the gyro's yaw rate about the device Z axis (rad/s, bias already removed).
 *
 * A flick is a spike of at least [thresholdRadPerSec] that comes back down (below [returnFraction] of the threshold,
 * or reverses sign) within [maxSpikeNs]. Slower or longer rotations are ignored. After an event nothing is reported
 * for [refractoryNs]. Positive Z rotation (counter-clockwise seen from above) = turning left = [FlickDirection.LEFT].
 * Feed samples in time order from one thread.
 */
class FlickDetector(
    private val thresholdRadPerSec: Float = THRESHOLD,
    private val maxSpikeNs: Long = MAX_SPIKE_NS,
    private val refractoryNs: Long = REFRACTORY_NS,
    private val returnFraction: Float = RETURN_FRACTION,
) {
    private enum class State { IDLE, SPIKE, WAIT_QUIET }

    private var state = State.IDLE
    private var spikeStartNs = 0L
    private var sign = 0
    private var peak = 0f
    private var blockedUntilNs = Long.MIN_VALUE

    fun reset() {
        state = State.IDLE
        blockedUntilNs = Long.MIN_VALUE
    }

    fun onSample(timestampNs: Long, rateZ: Float): FlickEvent? {
        val mag = abs(rateZ)
        val quiet = mag < thresholdRadPerSec * returnFraction
        when (state) {
            State.IDLE -> {
                if (mag >= thresholdRadPerSec && timestampNs >= blockedUntilNs) {
                    state = State.SPIKE
                    spikeStartNs = timestampNs
                    sign = if (rateZ > 0f) 1 else -1
                    peak = mag
                }
            }
            State.SPIKE -> {
                val s = if (rateZ > 0f) 1 else -1
                val reversed = !quiet && s != sign
                if (timestampNs - spikeStartNs > maxSpikeNs) {
                    state = if (quiet) State.IDLE else State.WAIT_QUIET // too long: a sweep, not a flick
                } else if (quiet || reversed) {
                    state = State.IDLE
                    blockedUntilNs = timestampNs + refractoryNs
                    return FlickEvent(
                        if (sign > 0) FlickDirection.LEFT else FlickDirection.RIGHT,
                        (peak / (2f * thresholdRadPerSec)).coerceIn(0f, 1f),
                        timestampNs,
                    )
                } else if (s == sign) {
                    peak = maxOf(peak, mag)
                }
            }
            State.WAIT_QUIET -> if (quiet) state = State.IDLE
        }
        return null
    }

    companion object {
        const val THRESHOLD = 5f
        const val MAX_SPIKE_NS = 150_000_000L
        const val REFRACTORY_NS = 500_000_000L
        const val RETURN_FRACTION = 0.3f
    }
}
