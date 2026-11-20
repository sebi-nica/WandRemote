package com.example.wandremote.sensors

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Constant gyroscope offset in rad/s, measured while the phone rests. */
data class GyroBias(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = GyroBias(0f, 0f, 0f)
    }
}

/** Pointer movement in pixels. */
data class PointerDelta(val dx: Float, val dy: Float)

/** Gyro rates (already bias-corrected, mapped to screen axes) -> smoothed, accelerated cursor deltas. */
class PointerPipeline {
    private var smX = 0f
    private var smY = 0f

    fun reset() {
        smX = 0f
        smY = 0f
    }

    /** [wx]/[wy] in rad/s, [dt] in seconds. Returns null when there is nothing to move. */
    fun step(wx: Float, wy: Float, dt: Float, tuning: PointerTuning): PointerDelta? {
        if (hypot(wx, wy) < tuning.deadZone) {
            reset()
            return null
        }
        smX += tuning.smoothing * (wx - smX)
        smY += tuning.smoothing * (wy - smY)
        val gain = tuning.baseSensitivity * (1f + tuning.acceleration * hypot(smX, smY))
        val dx = smX * dt * gain
        val dy = smY * dt * gain
        return if (dx != 0f || dy != 0f) PointerDelta(dx, dy) else null
    }
}

/** Per-axis exponential low-pass filter. [alpha] = 1 passes the input through unchanged. */
class LowPass3 {
    private val state = FloatArray(3)
    private var primed = false

    fun reset() {
        primed = false
    }

    fun apply(alpha: Float, x: Float, y: Float, z: Float, out: FloatArray) {
        if (!primed) {
            state[0] = x
            state[1] = y
            state[2] = z
            primed = true
        } else {
            state[0] += alpha * (x - state[0])
            state[1] += alpha * (y - state[1])
            state[2] += alpha * (z - state[2])
        }
        state.copyInto(out)
    }
}

/**
 * Collects gyro samples from a phone at rest for [durationNs] and decides whether the readings are a
 * trustworthy bias: the phone must not have moved (per-axis stddev and absolute rate stay small).
 */
class GyroBiasCalibrator(private val durationNs: Long = DURATION_NS) {
    sealed interface Result {
        data class Success(val bias: GyroBias) : Result
        data object Moved : Result
        data object TooFewSamples : Result
    }

    private var startNs = 0L
    private var count = 0
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumZ = 0.0
    private var sumSqX = 0.0
    private var sumSqY = 0.0
    private var sumSqZ = 0.0
    private var maxAbs = 0f
    private var elapsedNs = 0L

    /** 0..1 */
    val progress: Float get() = (elapsedNs.toFloat() / durationNs).coerceIn(0f, 1f)
    val isDone: Boolean get() = elapsedNs >= durationNs

    fun add(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (count == 0) startNs = timestampNs
        elapsedNs = timestampNs - startNs
        count++
        sumX += x
        sumY += y
        sumZ += z
        sumSqX += x.toDouble() * x
        sumSqY += y.toDouble() * y
        sumSqZ += z.toDouble() * z
        maxAbs = maxOf(maxAbs, abs(x), abs(y), abs(z))
    }

    fun result(): Result {
        if (count < MIN_SAMPLES) return Result.TooFewSamples
        val n = count.toDouble()
        val mx = sumX / n
        val my = sumY / n
        val mz = sumZ / n
        val stdMax = maxOf(
            stddev(sumSqX, mx, n),
            stddev(sumSqY, my, n),
            stddev(sumSqZ, mz, n),
        )
        if (stdMax > MAX_STDDEV || maxAbs > MAX_ABS_RATE) return Result.Moved
        return Result.Success(GyroBias(mx.toFloat(), my.toFloat(), mz.toFloat()))
    }

    private fun stddev(sumSq: Double, mean: Double, n: Double) = sqrt(maxOf(0.0, sumSq / n - mean * mean))

    companion object {
        const val DURATION_NS = 3_000_000_000L
        const val MIN_SAMPLES = 50

        /** rad/s: gyro noise at rest is ~0.002-0.01; hand tremor or a bump is well above this. */
        const val MAX_STDDEV = 0.015

        /** rad/s (~8.6 deg/s): no resting gyro has a bias or noise peak this large. */
        const val MAX_ABS_RATE = 0.15f
    }
}
