package com.example.wandremote.bluetooth

/** Pure-Kotlin HID mouse report descriptor and report packing (no Android imports, unit-testable). */
object HidMouseReport {
    const val REPORT_ID = 1
    const val MAX_DELTA = 127

    const val BUTTON_LEFT = 0x01
    const val BUTTON_RIGHT = 0x02
    const val BUTTON_MIDDLE = 0x04

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** Mouse, Report ID 1: 3 buttons + 5 padding bits, X/Y/Wheel as relative signed 8-bit (-127..127). */
    val DESCRIPTOR: ByteArray = b(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x02, // Usage (Mouse)
        0xA1, 0x01, // Collection (Application)
        0x85, REPORT_ID, //   Report ID (1)
        0x09, 0x01, //   Usage (Pointer)
        0xA1, 0x00, //   Collection (Physical)
        0x05, 0x09, //     Usage Page (Buttons)
        0x19, 0x01, //     Usage Minimum (1)
        0x29, 0x03, //     Usage Maximum (3)
        0x15, 0x00, //     Logical Minimum (0)
        0x25, 0x01, //     Logical Maximum (1)
        0x95, 0x03, //     Report Count (3)
        0x75, 0x01, //     Report Size (1)
        0x81, 0x02, //     Input (Data, Var, Abs)
        0x95, 0x01, //     Report Count (1)
        0x75, 0x05, //     Report Size (5)
        0x81, 0x03, //     Input (Const) - padding
        0x05, 0x01, //     Usage Page (Generic Desktop)
        0x09, 0x30, //     Usage (X)
        0x09, 0x31, //     Usage (Y)
        0x09, 0x38, //     Usage (Wheel)
        0x15, 0x81, //     Logical Minimum (-127)
        0x25, 0x7F, //     Logical Maximum (127)
        0x75, 0x08, //     Report Size (8)
        0x95, 0x03, //     Report Count (3)
        0x81, 0x06, //     Input (Data, Var, Rel)
        0xC0, //   End Collection
        0xC0, // End Collection
    )

    /** 4-byte report payload (without the report ID): buttons, dx, dy, wheel. Deltas are clamped to +-127. */
    fun pack(buttons: Int, dx: Int, dy: Int, wheel: Int): ByteArray = byteArrayOf(
        (buttons and 0x07).toByte(),
        dx.coerceIn(-MAX_DELTA, MAX_DELTA).toByte(),
        dy.coerceIn(-MAX_DELTA, MAX_DELTA).toByte(),
        wheel.coerceIn(-MAX_DELTA, MAX_DELTA).toByte(),
    )

    /** Splits a movement into steps of at most +-127 per axis whose sums equal [dx] and [dy]. */
    fun splitMove(dx: Int, dy: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var rx = dx
        var ry = dy
        while (rx != 0 || ry != 0) {
            val cx = rx.coerceIn(-MAX_DELTA, MAX_DELTA)
            val cy = ry.coerceIn(-MAX_DELTA, MAX_DELTA)
            out += cx to cy
            rx -= cx
            ry -= cy
        }
        return out
    }
}

/** Accumulates fractional values and releases whole units, keeping the remainder (truncating toward zero). */
class FractionAccumulator {
    private var rest = 0f

    fun add(value: Float): Int {
        rest += value
        val whole = rest.toInt()
        rest -= whole
        return whole
    }

    fun reset() {
        rest = 0f
    }
}

/**
 * Scroll accumulator. Our packets use positive = scroll down; HID wheel positive = scroll up,
 * so the emitted step count has the opposite sign.
 */
class WheelAccumulator {
    private val acc = FractionAccumulator()

    fun add(notchesDown: Float): Int = -acc.add(notchesDown)

    fun reset() = acc.reset()
}
