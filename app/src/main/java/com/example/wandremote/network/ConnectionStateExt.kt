package com.example.wandremote.network

/** True when the PC companion app is reachable (Wi-Fi or Bluetooth link); false for the plain HID mouse and when offline. */
val ConnectionState.companionConnected: Boolean
    get() = this is ConnectionState.Connected || this is ConnectionState.ConnectedLink

/** Round-trip latency of a companion connection (see [ConnectionState.Connected] constants), null otherwise. */
val ConnectionState.companionLatencyMs: Int?
    get() = when (this) {
        is ConnectionState.Connected -> latencyMs
        is ConnectionState.ConnectedLink -> latencyMs
        else -> null
    }

/** Identifies one companion session so the UI can react to a new connection (e.g. re-send the mode). Null if none. */
fun ConnectionState.companionSessionId(): String? = when (this) {
    is ConnectionState.Connected -> "$host:$port"
    is ConnectionState.ConnectedLink -> "bt:$deviceName"
    else -> null
}

/** Round-trip in ms for a Pong that echoes [sentNanos], at least 1 (0 means "pending"). */
fun roundTripMs(nowNanos: Long, sentNanos: Long): Int =
    ((nowNanos - sentNanos) / 1_000_000L).toInt().coerceAtLeast(1)

/** True when no Pong arrived for longer than [timeoutNanos]. */
fun pongOverdue(nowNanos: Long, lastPongNanos: Long, timeoutNanos: Long = 4_000_000_000L): Boolean =
    nowNanos - lastPongNanos > timeoutNanos

/**
 * Merges the transport states into the UI state: HID mouse wins, then the Bluetooth link, then Wi-Fi.
 * [hidDevice] is the connected HID host's name or null; [link] is null unless the link is connected.
 */
fun mergeConnectionState(udp: ConnectionState, hidDevice: String?, link: BluetoothLinkState.Connected?): ConnectionState =
    when {
        hidDevice != null -> ConnectionState.ConnectedBluetooth(hidDevice)
        link != null -> ConnectionState.ConnectedLink(link.deviceName, link.latencyMs)
        else -> udp
    }
