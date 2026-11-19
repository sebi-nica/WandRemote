package com.example.wandremote.shared

/** Network constants shared by the phone app and the PC companion. */
object Protocol {
    /** Bumped whenever the wire format changes incompatibly. */
    const val VERSION: Int = 1

    /** UDP port the PC listens on for discovery broadcasts from the phone. */
    const val DISCOVERY_PORT: Int = 47800

    /** UDP port for low-latency sensor / command packets. */
    const val DEFAULT_UDP_PORT: Int = 47801

    /** TCP port for control messages (pairing, settings). */
    const val DEFAULT_TCP_PORT: Int = 47802

    /** Payload the phone broadcasts to find PCs. */
    const val DISCOVERY_REQUEST: String = "WANDREMOTE_DISCOVER"

    /** Prefix of the PC's reply: "WANDREMOTE_HERE|<version>|<pcName>|<udpPort>|<tcpPort>". */
    const val DISCOVERY_RESPONSE_PREFIX: String = "WANDREMOTE_HERE"

    /** Magic first byte of every data packet, so stray UDP traffic is ignored. */
    const val MAGIC: Byte = 0x57 // 'W'

    /** Builds the text encoded in the PC's pairing QR code. */
    fun pairingUri(host: String, udpPort: Int = DEFAULT_UDP_PORT, tcpPort: Int = DEFAULT_TCP_PORT): String =
        "wandremote://$host:$udpPort?tcp=$tcpPort&v=$VERSION"
}
