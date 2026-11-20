package com.example.wandremote.sensors

/** Samples of one sensor inside a time window, oldest first. Vectors are packed x,y,z per sample. */
class SampleWindow(
    val timesNs: LongArray,
    val raw: FloatArray,
    val filtered: FloatArray,
) {
    val size: Int get() = timesNs.size

    companion object {
        val EMPTY = SampleWindow(LongArray(0), FloatArray(0), FloatArray(0))
    }
}

/** Fixed-capacity ring buffer of timestamped 3-axis samples (raw next to filtered). Not thread-safe. */
class SampleBuffer(private val capacity: Int) {
    private val times = LongArray(capacity)
    private val raw = FloatArray(capacity * 3)
    private val filtered = FloatArray(capacity * 3)
    private var head = 0 // next write position
    private var count = 0

    val size: Int get() = count

    fun clear() {
        head = 0
        count = 0
    }

    fun add(timeNs: Long, rawXyz: FloatArray, filteredXyz: FloatArray) {
        times[head] = timeNs
        rawXyz.copyInto(raw, head * 3, 0, 3)
        filteredXyz.copyInto(filtered, head * 3, 0, 3)
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /** Samples with `timeNs >= nowNs - windowNs`, oldest first. */
    fun window(nowNs: Long, windowNs: Long): SampleWindow {
        val oldest = nowNs - windowNs
        var first = 0 // index among stored samples (0 = oldest)
        while (first < count && times[physical(first)] < oldest) first++
        val n = count - first
        if (n == 0) return SampleWindow.EMPTY
        val t = LongArray(n)
        val r = FloatArray(n * 3)
        val f = FloatArray(n * 3)
        for (i in 0 until n) {
            val p = physical(first + i)
            t[i] = times[p]
            raw.copyInto(r, i * 3, p * 3, p * 3 + 3)
            filtered.copyInto(f, i * 3, p * 3, p * 3 + 3)
        }
        return SampleWindow(t, r, f)
    }

    private fun physical(logical: Int) = (head - count + logical + capacity) % capacity
}
