package com.example.wandremote.bluetooth

import com.example.wandremote.shared.RemoteKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidMouseReportTest {
    @Test
    fun packButtonsBitmask() {
        val b = HidMouseReport.BUTTON_LEFT or HidMouseReport.BUTTON_MIDDLE
        assertArrayEquals(byteArrayOf(0x05, 0, 0, 0), HidMouseReport.pack(b, 0, 0, 0))
        assertEquals(0x02.toByte(), HidMouseReport.pack(HidMouseReport.BUTTON_RIGHT, 0, 0, 0)[0])
        assertEquals(0x07.toByte(), HidMouseReport.pack(0xFF, 0, 0, 0)[0])
    }

    @Test
    fun packSignedDeltasAndClamping() {
        val r = HidMouseReport.pack(0, -5, 7, -1)
        assertEquals(-5, r[1].toInt())
        assertEquals(7, r[2].toInt())
        assertEquals(-1, r[3].toInt())
        val c = HidMouseReport.pack(0, 1000, -1000, 200)
        assertEquals(127, c[1].toInt())
        assertEquals(-127, c[2].toInt())
        assertEquals(127, c[3].toInt())
    }

    @Test
    fun descriptorStartsAsMouseWithReportId() {
        val d = HidMouseReport.DESCRIPTOR
        assertEquals(0x05.toByte(), d[0])
        assertEquals(0xC0.toByte(), d.last())
        assertEquals(0x85.toByte(), d[6])
        assertEquals(HidMouseReport.REPORT_ID.toByte(), d[7])
    }

    @Test
    fun descriptorHasMouseAndKeyboardCollections() {
        val d = HidMouseReport.DESCRIPTOR
        // Usage Page (Generic Desktop), Usage (Mouse) / (Keyboard), Collection (Application), Report ID n.
        val mouse = byteArrayOf(0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), HidMouseReport.REPORT_ID.toByte())
        val keyboard = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), HidMouseReport.KEYBOARD_REPORT_ID.toByte(),
        )
        assertEquals(1, HidMouseReport.REPORT_ID)
        assertEquals(2, HidMouseReport.KEYBOARD_REPORT_ID)
        assertEquals(0xC0.toByte(), d.last())
        // The mouse collection comes first (unchanged, Report ID 1), the keyboard one follows (Report ID 2).
        assertEquals(0, d.indexOfSequence(mouse))
        assertTrue(d.indexOfSequence(keyboard) > 0)
    }

    private fun ByteArray.indexOfSequence(needle: ByteArray): Int =
        (0..size - needle.size).firstOrNull { i -> needle.indices.all { this[i + it] == needle[it] } } ?: -1

    @Test
    fun keyboardReportPacking() {
        assertArrayEquals(ByteArray(8), HidMouseReport.packKeyboard(0, emptyList()))
        assertArrayEquals(byteArrayOf(0, 0, 0x4F, 0, 0, 0, 0, 0), HidMouseReport.packKeyboard(0, listOf(0x4F)))
        assertArrayEquals(
            byteArrayOf(0x02, 0, 0x50, 0x52, 0, 0, 0, 0),
            HidMouseReport.packKeyboard(0x02, listOf(0x50, 0x52)),
        )
        // More than six keys: extra ones are dropped instead of overflowing.
        val packed = HidMouseReport.packKeyboard(0, (1..9).toList())
        assertEquals(8, packed.size)
        assertEquals(6, packed[7].toInt())
    }

    @Test
    fun keyUsagesMatchHidKeyboardPage() {
        assertEquals(0x4F, HidMouseReport.keyUsage(RemoteKey.RIGHT))
        assertEquals(0x50, HidMouseReport.keyUsage(RemoteKey.LEFT))
        assertEquals(0x51, HidMouseReport.keyUsage(RemoteKey.DOWN))
        assertEquals(0x52, HidMouseReport.keyUsage(RemoteKey.UP))
        assertEquals(0x4B, HidMouseReport.keyUsage(RemoteKey.PAGE_UP))
        assertEquals(0x4E, HidMouseReport.keyUsage(RemoteKey.PAGE_DOWN))
        assertEquals(0x29, HidMouseReport.keyUsage(RemoteKey.ESCAPE))
        assertEquals(0x3E, HidMouseReport.keyUsage(RemoteKey.F5))
        assertEquals(0x28, HidMouseReport.keyUsage(RemoteKey.ENTER))
        assertEquals(0x2C, HidMouseReport.keyUsage(RemoteKey.SPACE))
        // Every usage fits the descriptor's logical maximum (0x65) and none is duplicated.
        val all = RemoteKey.entries.map(HidMouseReport::keyUsage)
        assertTrue(all.all { it in 1..0x65 })
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun splitMoveChunksSumToOriginal() {
        for ((dx, dy) in listOf(300 to -50, -500 to 400, 127 to 127, 128 to 0, 0 to 0, 5 to -3)) {
            val parts = HidMouseReport.splitMove(dx, dy)
            assertTrue(parts.all { it.first in -127..127 && it.second in -127..127 })
            assertEquals(dx, parts.sumOf { it.first })
            assertEquals(dy, parts.sumOf { it.second })
        }
        assertTrue(HidMouseReport.splitMove(0, 0).isEmpty())
        assertEquals(3, HidMouseReport.splitMove(300, 0).size)
    }

    @Test
    fun fractionAccumulatorKeepsRemainder() {
        val a = FractionAccumulator()
        assertEquals(0, a.add(0.4f))
        assertEquals(0, a.add(0.4f))
        assertEquals(1, a.add(0.4f))
        val n = FractionAccumulator()
        assertEquals(0, n.add(-0.6f))
        assertEquals(-1, n.add(-0.6f))
    }

    @Test
    fun wheelIsInvertedAndAccumulates() {
        val w = WheelAccumulator()
        assertEquals(-2, w.add(2f)) // scroll down -> negative HID wheel
        assertEquals(2, w.add(-2f)) // scroll up -> positive
        assertEquals(0, w.add(0.3f))
        assertEquals(0, w.add(0.3f))
        assertEquals(0, w.add(0.3f))
        assertEquals(-1, w.add(0.3f)) // 1.2 total
    }
}
