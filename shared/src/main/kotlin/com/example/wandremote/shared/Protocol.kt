package com.example.wandremote.shared

/** Network constants shared by the phone app and the PC companion. */
object Protocol {
    /** Bumped whenever the wire format changes incompatibly. */
    const val VERSION: Int = 1

    /** UDP port the PC listens on for discovery broadcasts from the phone. */
    const val DISCOVERY_PORT: Int = 47800

    /** UDP port for low-latency sensor / command packets. */
    const val DEFAULT_UDP_PORT: Int = 47801

    /** Payload the phone broadcasts to find PCs. */
    const val DISCOVERY_REQUEST: String = "WANDREMOTE_DISCOVER"

    /** Prefix of the PC's reply: "WANDREMOTE_HERE|<version>|<pcName>|<udpPort>". */
    const val DISCOVERY_RESPONSE_PREFIX: String = "WANDREMOTE_HERE"

    /** Magic first byte of every data packet, so stray UDP traffic is ignored. */
    const val MAGIC: Byte = 0x57 // 'W'

    /** URI scheme of pairing links (the PC's QR code); the Android app registers for it. */
    const val PAIRING_SCHEME: String = "wandremote"

    /** Builds the text encoded in the PC's pairing QR code: `wandremote://<host>:<port>?v=<version>`. */
    fun pairingUri(host: String, udpPort: Int = DEFAULT_UDP_PORT): String =
        "$PAIRING_SCHEME://$host:$udpPort?v=$VERSION"

    /** Parses a link made by [pairingUri]; returns null if it isn't a valid WandRemote pairing link. */
    fun parsePairingUri(text: String): PairingInfo? {
        val uri = try {
            java.net.URI(text.trim())
        } catch (e: java.net.URISyntaxException) {
            return null
        }
        if (!uri.scheme.equals(PAIRING_SCHEME, ignoreCase = true)) return null
        val host = uri.host?.takeIf { isValidHost(it) } ?: return null
        val udpPort = if (uri.port in 1..65535) uri.port else DEFAULT_UDP_PORT
        val version = parseParams(uri.rawQuery.orEmpty())["v"]?.toIntOrNull() ?: VERSION
        return PairingInfo(host, udpPort, version)
    }

    private fun parseParams(text: String): Map<String, String> = text.split('&').mapNotNull {
        val parts = it.split('=', limit = 2)
        if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
    }.toMap()

    /** Hostnames / IPv4 literals only: letters, digits, dots and dashes. */
    private fun isValidHost(host: String): Boolean =
        host.isNotBlank() && host.length <= 253 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}

/** Connection details carried by a pairing link / QR code. */
data class PairingInfo(val host: String, val udpPort: Int, val version: Int)
