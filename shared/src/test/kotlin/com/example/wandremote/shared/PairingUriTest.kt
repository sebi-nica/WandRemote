package com.example.wandremote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingUriTest {
    @Test
    fun roundTrips() {
        val uri = Protocol.pairingUri("192.168.50.76", 47801)
        assertEquals("wandremote://192.168.50.76:47801?v=${Protocol.VERSION}", uri)
        assertEquals(PairingInfo("192.168.50.76", 47801, Protocol.VERSION), Protocol.parsePairingUri(uri))
    }

    @Test
    fun oldLinksWithTcpParameterStillParse() {
        assertEquals(
            PairingInfo("10.0.0.5", 47801, 1),
            Protocol.parsePairingUri("wandremote://10.0.0.5:47801?tcp=47802&v=1"),
        )
    }

    @Test
    fun missingPortFallsBackToDefault() {
        assertEquals(
            PairingInfo("10.0.0.5", Protocol.DEFAULT_UDP_PORT, Protocol.VERSION),
            Protocol.parsePairingUri("wandremote://10.0.0.5"),
        )
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(Protocol.parsePairingUri("https://192.168.50.76:47801"))
        assertNull(Protocol.parsePairingUri("wandremote://"))
        assertNull(Protocol.parsePairingUri("not a uri at all %%"))
        assertNull(Protocol.parsePairingUri(""))
    }
}
