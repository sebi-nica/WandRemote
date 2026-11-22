package com.example.wandremote.desktop.input

import com.example.wandremote.desktop.AppState
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketHandlerTest {
    private class FakeSource(override val label: String = "Bluetooth") : PacketSource {
        val replies = mutableListOf<Packet>()
        override fun reply(packet: Packet) { replies += packet }
    }

    private class FakeInput : InputController() {
        val log = mutableListOf<String>()
        override fun press(button: MouseButton) { log += "press $button" }
        override fun release(button: MouseButton) { log += "release $button" }
    }

    private fun handler(input: InputController) = PacketHandler(AppState(), input)

    @Test
    fun pingIsAnsweredOnTheSameSource() {
        val a = FakeSource()
        val b = FakeSource()
        val h = handler(FakeInput())
        h.handle(Packet.Ping(1234L), a)
        assertEquals(listOf<Packet>(Packet.Pong(1234L)), a.replies)
        assertTrue(b.replies.isEmpty())
        h.reset()
    }

    @Test
    fun helloIsAnsweredWithScreenInfoOnTheSameSource() {
        val a = FakeSource()
        val b = FakeSource()
        val h = handler(FakeInput())
        h.handle(Packet.Hello("Phone"), a)
        val info = a.replies.single() as Packet.ScreenInfo
        assertTrue(info.widthPx > 0 && info.heightPx > 0)
        assertTrue(b.replies.isEmpty())
        h.reset()
    }

    @Test
    fun disconnectReleasesHeldButtons() {
        val input = FakeInput()
        val src = FakeSource()
        val h = handler(input)
        h.handle(Packet.Button(MouseButton.LEFT, true), src)
        h.disconnected(src)
        assertEquals(listOf("press LEFT", "release LEFT"), input.log)
        h.reset()
    }
}
