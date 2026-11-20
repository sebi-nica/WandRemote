package com.example.wandremote.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickDetectorTest {
    private val stepNs = 10_000_000L // 100 Hz

    private fun feed(d: FlickDetector, rates: List<Float>, startNs: Long = 0L): List<FlickEvent> =
        rates.mapIndexedNotNull { i, r -> d.onSample(startNs + i * stepNs, r) }

    private val leftFlick = listOf(0f, 0.5f, 3f, 7f, 9f, 7f, 3f, 0.5f, 0f, 0f)

    @Test
    fun flickLeftIsPositiveRotation() {
        val events = feed(FlickDetector(), leftFlick)
        assertEquals(1, events.size)
        assertEquals(FlickDirection.LEFT, events[0].direction)
        assertTrue(events[0].confidence in 0.8f..1f)
    }

    @Test
    fun flickRightIsNegativeRotation() {
        val events = feed(FlickDetector(), leftFlick.map { -it })
        assertEquals(1, events.size)
        assertEquals(FlickDirection.RIGHT, events[0].direction)
    }

    @Test
    fun confidenceGrowsWithPeak() {
        val weak = feed(FlickDetector(), listOf(0f, 5.5f, 5.5f, 0f, 0f))
        val strong = feed(FlickDetector(), listOf(0f, 12f, 12f, 0f, 0f))
        assertTrue(weak[0].confidence < strong[0].confidence)
        assertEquals(1f, strong[0].confidence, 1e-6f)
    }

    @Test
    fun slowTurnIsNotAFlick() {
        val slow = List(150) { 2.5f } // a 1.5 s sweep at 2.5 rad/s
        assertTrue(feed(FlickDetector(), slow + listOf(0f, 0f)).isEmpty())
    }

    @Test
    fun longFastSweepIsNotAFlick() {
        val sweep = List(40) { 6f } + List(5) { 0f } // 400 ms above the threshold
        assertTrue(feed(FlickDetector(), sweep).isEmpty())
    }

    @Test
    fun doubleFlickWithinRefractoryIsOneEvent() {
        val gap = List(10) { 0f }
        val events = feed(FlickDetector(), leftFlick + gap + leftFlick) // the second flick starts ~200 ms after the first
        assertEquals(1, events.size)
    }

    @Test
    fun returnSwingAfterFlickDoesNotCountAsRightFlick() {
        val withReturn = leftFlick + listOf(-3f, -7f, -9f, -7f, -3f, 0f)
        val events = feed(FlickDetector(), withReturn)
        assertEquals(1, events.size)
        assertEquals(FlickDirection.LEFT, events[0].direction)
    }

    @Test
    fun secondFlickAfterRefractoryIsDetected() {
        val gap = List(60) { 0f } // 600 ms
        val events = feed(FlickDetector(), leftFlick + gap + leftFlick.map { -it })
        assertEquals(listOf(FlickDirection.LEFT, FlickDirection.RIGHT), events.map { it.direction })
    }

    @Test
    fun noiseBelowThresholdIsIgnored() {
        assertTrue(feed(FlickDetector(), List(200) { if (it % 2 == 0) 0.3f else -0.3f }).isEmpty())
    }
}
