package com.example.wandremote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticsTest {
    @Test
    fun amplitudeMapsStrengthToVibrationRange() {
        assertEquals(255, amplitudeFor(1f))
        assertEquals(1, amplitudeFor(0.001f))
        assertEquals(128, amplitudeFor(0.5f))
        assertEquals(255, amplitudeFor(5f))
    }
}
