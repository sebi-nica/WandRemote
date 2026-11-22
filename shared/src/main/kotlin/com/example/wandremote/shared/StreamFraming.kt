package com.example.wandremote.shared

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Framing for stream transports (Bluetooth RFCOMM): each packet is written as one length byte followed by
 * the [PacketCodec] bytes. UDP doesn't need this because every datagram is already one packet.
 */
object StreamFraming {
    /** Bluetooth RFCOMM service UUID of the PC companion app. */
    const val RFCOMM_UUID: String = "6f0b2a7e-3c1d-4b8e-9a51-5752414e4401"

    /** Human-readable name of the RFCOMM service record. */
    const val RFCOMM_SERVICE_NAME: String = "WandRemote"

    fun frame(packet: Packet): ByteArray {
        val body = PacketCodec.encode(packet)
        require(body.size <= 255) { "packet too large for framing: ${body.size}" }
        return byteArrayOf(body.size.toByte()) + body
    }

    fun write(out: OutputStream, packet: Packet) {
        out.write(frame(packet))
    }

    /**
     * Reads one framed packet. Returns null for a well-framed but undecodable packet (skipped);
     * throws [EOFException] when the stream ends.
     */
    fun read(input: InputStream): Packet? {
        val len = input.read()
        if (len < 0) throw EOFException()
        val body = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(body, off, len - off)
            if (n < 0) throw EOFException()
            off += n
        }
        return PacketCodec.decode(body)
    }
}
