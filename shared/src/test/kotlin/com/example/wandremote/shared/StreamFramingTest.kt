package com.example.wandremote.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StreamFramingTest {
    @Test
    fun packetsRoundTripThroughAStream() {
        val packets = listOf(Packet.Hello("Phone"), Packet.Move(3f, -4f), Packet.Key(RemoteKey.RIGHT, true), Packet.Bye)
        val out = ByteArrayOutputStream()
        packets.forEach { StreamFraming.write(out, it) }
        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals(packets, packets.map { StreamFraming.read(input) })
        assertFailsWith<EOFException> { StreamFraming.read(input) }
    }

    @Test
    fun undecodableFrameIsSkippedWithoutLosingSync() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(2, 0x00, 0x7F)) // well-framed garbage
        StreamFraming.write(out, Packet.Move(1f, 2f))
        val input = ByteArrayInputStream(out.toByteArray())
        assertNull(StreamFraming.read(input))
        assertEquals(Packet.Move(1f, 2f), StreamFraming.read(input))
    }

    @Test
    fun truncatedFrameThrows() {
        val bytes = StreamFraming.frame(Packet.Move(1f, 2f))
        assertFailsWith<EOFException> { StreamFraming.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 2))) }
    }
}
