package com.example.wandremote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateExtTest {
    private val wifi = ConnectionState.Connected("10.0.0.2", 5000, 12)
    private val link = ConnectionState.ConnectedLink("My PC", 20)
    private val hid = ConnectionState.ConnectedBluetooth("My PC")

    @Test
    fun companionConnected_isTrueForWifiAndLinkOnly() {
        assertTrue(wifi.companionConnected)
        assertTrue(link.companionConnected)
        assertFalse(hid.companionConnected)
        assertFalse(ConnectionState.Disconnected.companionConnected)
    }

    @Test
    fun companionLatency_comesFromWifiOrLink() {
        assertEquals(12, wifi.companionLatencyMs)
        assertEquals(20, link.companionLatencyMs)
        assertNull(hid.companionLatencyMs)
        assertNull(ConnectionState.Disconnected.companionLatencyMs)
    }

    @Test
    fun sessionIdAndCalibrationKey() {
        assertEquals("10.0.0.2:5000", wifi.companionSessionId())
        assertEquals("bt:My PC", link.companionSessionId())
        assertNull(hid.companionSessionId())
        assertEquals("10.0.0.2", wifi.calibrationKey())
        assertEquals("bt:My PC", link.calibrationKey())
        assertNull(ConnectionState.Disconnected.calibrationKey())
    }

    @Test
    fun roundTrip_isAtLeastOneMs() {
        assertEquals(1, roundTripMs(1_000_100, 1_000_000))
        assertEquals(25, roundTripMs(26_000_000, 1_000_000))
    }

    @Test
    fun pongOverdue_afterTimeout() {
        assertFalse(pongOverdue(3_900_000_000, 0))
        assertTrue(pongOverdue(4_100_000_000, 0))
    }

    @Test
    fun merge_prefersHidThenLinkThenWifi() {
        val linkState = BluetoothLinkState.Connected("My PC", 7)
        assertEquals(hid, mergeConnectionState(wifi, "My PC", linkState))
        assertEquals(ConnectionState.ConnectedLink("My PC", 7), mergeConnectionState(wifi, null, linkState))
        assertEquals(wifi, mergeConnectionState(wifi, null, null))
        assertEquals(ConnectionState.Disconnected, mergeConnectionState(ConnectionState.Disconnected, null, null))
    }
}
