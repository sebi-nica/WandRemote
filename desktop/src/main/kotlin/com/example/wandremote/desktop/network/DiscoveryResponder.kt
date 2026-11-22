package com.example.wandremote.desktop.network

import com.example.wandremote.shared.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Answers phone discovery broadcasts on [Protocol.DISCOVERY_PORT]. */
class DiscoveryResponder {
    private var socket: DatagramSocket? = null

    private val pcName: String = try {
        InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
        "PC"
    }

    /** Throws [java.net.SocketException] if the port cannot be bound. */
    @Synchronized
    fun start() {
        if (socket != null) return
        val s = DatagramSocket(null)
        try {
            s.reuseAddress = true
            s.bind(InetSocketAddress(Protocol.DISCOVERY_PORT))
        } catch (e: Exception) {
            s.close()
            throw e
        }
        socket = s
        Thread({ loop(s) }, "wand-discovery").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(256)
        val reply = "${Protocol.DISCOVERY_RESPONSE_PREFIX}|${Protocol.VERSION}|$pcName|${Protocol.DEFAULT_UDP_PORT}"
        val replyBytes = reply.toByteArray(Charsets.UTF_8)
        while (!s.isClosed) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val text = String(p.data, 0, p.length, Charsets.UTF_8)
                if (text == Protocol.DISCOVERY_REQUEST) {
                    s.send(DatagramPacket(replyBytes, replyBytes.size, p.socketAddress))
                }
            } catch (e: Exception) {
                if (s.isClosed) break
            }
        }
    }
}
