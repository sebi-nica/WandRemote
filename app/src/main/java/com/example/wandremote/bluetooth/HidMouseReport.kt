package com.example.wandremote.bluetooth

import com.example.wandremote.shared.RemoteKey

/**
 * Pure-Kotlin HID descriptor and report packing for the phone's mouse + keyboard combo (no Android imports,
 * unit-testable). Mouse = Report ID 1, keyboard = Report ID 2.
 *
 * NOTE: the keyboard collection was added after the mouse-only version shipped. A PC that was already paired with
 * the old descriptor caches it, so Windows may need the phone removed from its Bluetooth devices and paired again
 * before the arrow keys work.
 */
object HidMouseReport {
    const val REPORT_ID = 1
    const val KEYBOARD_REPORT_ID = 2
    const val MAX_KEYS = 6
    const val MAX_DELTA = 127

    const val BUTTON_LEFT = 0x01
    const val BUTTON_RIGHT = 0x02
    const val BUTTON_MIDDLE = 0x04

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** Mouse, Report ID 1: 3 buttons + 5 padding bits, X/Y/Wheel as relative signed 8-bit (-127..127). */
    private val MOUSE_DESCRIPTOR: ByteArray = b(
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

    /** Boot-style keyboard, Report ID 2: modifier bitmap, reserved byte, 6 key slots (usages 0..0x65). */
    private val KEYBOARD_DESCRIPTOR: ByteArray = b(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x06, // Usage (Keyboard)
        0xA1, 0x01, // Collection (Application)
        0x85, KEYBOARD_REPORT_ID, //   Report ID (2)
        0x05, 0x07, //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0, //   Usage Minimum (Left Control)
        0x29, 0xE7, //   Usage Maximum (Right GUI)
        0x15, 0x00, //   Logical Minimum (0)
        0x25, 0x01, //   Logical Maximum (1)
        0x75, 0x01, //   Report Size (1)
        0x95, 0x08, //   Report Count (8)
        0x81, 0x02, //   Input (Data, Var, Abs) - modifier keys
        0x95, 0x01, //   Report Count (1)
        0x75, 0x08, //   Report Size (8)
        0x81, 0x01, //   Input (Const) - reserved byte
        0x95, MAX_KEYS, //   Report Count (6)
        0x75, 0x08, //   Report Size (8)
        0x15, 0x00, //   Logical Minimum (0)
        0x25, 0x65, //   Logical Maximum (101)
        0x05, 0x07, //   Usage Page (Keyboard/Keypad)
        0x19, 0x00, //   Usage Minimum (0)
        0x29, 0x65, //   Usage Maximum (101)
        0x81, 0x00, //   Input (Data, Array) - pressed keys
        0xC0, // End Collection
    )

    /** The full descriptor: the mouse collection followed by the keyboard collection. */
    val DESCRIPTOR: ByteArray = MOUSE_DESCRIPTOR + KEYBOARD_DESCRIPTOR

    /** HID keyboard usage (usage page 0x07) for a phone key. */
    fun keyUsage(key: RemoteKey): Int = when (key) {
        RemoteKey.RIGHT -> 0x4F
        RemoteKey.LEFT -> 0x50
        RemoteKey.DOWN -> 0x51
        RemoteKey.UP -> 0x52
        RemoteKey.PAGE_UP -> 0x4B
        RemoteKey.PAGE_DOWN -> 0x4E
        RemoteKey.ESCAPE -> 0x29
        RemoteKey.F5 -> 0x3E
        RemoteKey.ENTER -> 0x28
        RemoteKey.SPACE -> 0x2C
    }

    /** 8-byte keyboard report payload (without the report ID): modifiers, reserved, up to [MAX_KEYS] usages. */
    fun packKeyboard(modifiers: Int, usages: Collection<Int>): ByteArray {
        val out = ByteArray(2 + MAX_KEYS)
        out[0] = modifiers.toByte()
        usages.take(MAX_KEYS).forEachIndexed { i, u -> out[2 + i] = u.toByte() }
        return out
    }

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
