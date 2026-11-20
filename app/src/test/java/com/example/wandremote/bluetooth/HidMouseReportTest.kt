package com.example.wandremote.bluetooth

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
