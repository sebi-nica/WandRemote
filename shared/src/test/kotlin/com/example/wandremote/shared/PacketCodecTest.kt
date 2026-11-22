package com.example.wandremote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PacketCodecTest {
    private val samples = listOf(
        Packet.Move(1.5f, -2.25f),
        Packet.Button(MouseButton.RIGHT, pressed = true),
        Packet.Button(MouseButton.LEFT, pressed = false),
        Packet.Scroll(0f, 3f),
        Packet.Orientation(1f, 0f, 0.5f, -0.5f),
        Packet.Laser(on = true, x = 0.1f, y = 0.9f),
        Packet.Ping(123_456_789_012L),
        Packet.Pong(-1L),
        Packet.Hello("Pixel 8"),
        Packet.Hello(""),
        Packet.Bye,
        Packet.SetMode(RemoteMode.POINTER),
        Packet.ScreenInfo(2560, 1440),
        Packet.SetMode(RemoteMode.THREE_D),
        Packet.Zoom(-0.5f),
        Packet.Key(RemoteKey.LEFT, pressed = true),
        Packet.Key(RemoteKey.F5, pressed = false),
    )

    @Test
    fun everyPacketRoundTrips() {
        for (packet in samples) {
            assertEquals(packet, PacketCodec.decode(PacketCodec.encode(packet)), "round trip of $packet")
        }
    }

    @Test
    fun encodedPacketsFitMaxSize() {
        val longName = Packet.Hello("x".repeat(200))
        val bytes = PacketCodec.encode(longName)
        assert(bytes.size <= PacketCodec.MAX_SIZE)
        assertEquals(48, (PacketCodec.decode(bytes) as Packet.Hello).name.length)
    }

    @Test
    fun rejectsForeignOrTruncatedData() {
        assertNull(PacketCodec.decode(byteArrayOf()))
        assertNull(PacketCodec.decode(byteArrayOf(0x00, 0x01, 0, 0, 0, 0)))
        assertNull(PacketCodec.decode(byteArrayOf(Protocol.MAGIC, 99)))
        val move = PacketCodec.encode(Packet.Move(1f, 2f))
        assertNull(PacketCodec.decode(move, move.size - 1))
    }
}
