package com.example.wandremote.desktop.network

import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.PacketCodec
import com.example.wandremote.shared.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** Receives [Packet]s on [Protocol.DEFAULT_UDP_PORT] and hands them to [onPacket] on the receiver thread. */
class UdpReceiver(private val onPacket: (Packet, InetSocketAddress) -> Unit) {
    private var socket: DatagramSocket? = null

    /** Throws [java.net.SocketException] if the port cannot be bound. */
    @Synchronized
    fun start() {
        if (socket != null) return
        val s = DatagramSocket(null)
        try {
            s.reuseAddress = true
            s.bind(InetSocketAddress(Protocol.DEFAULT_UDP_PORT))
        } catch (e: Exception) {
            s.close()
            throw e
        }
        socket = s
        Thread({ loop(s) }, "wand-udp").apply { isDaemon = true; priority = Thread.MAX_PRIORITY }.start()
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    fun send(packet: Packet, address: InetSocketAddress) {
        val s = socket ?: return
        val bytes = PacketCodec.encode(packet)
        try {
            s.send(DatagramPacket(bytes, bytes.size, address))
        } catch (e: Exception) {
            // socket closed or unreachable; ignore
        }
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(PacketCodec.MAX_SIZE * 4)
        while (!s.isClosed) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val packet = PacketCodec.decode(p.data, p.length) ?: continue
                onPacket(packet, p.socketAddress as InetSocketAddress)
            } catch (e: Exception) {
                if (s.isClosed) break
            }
        }
    }
}
