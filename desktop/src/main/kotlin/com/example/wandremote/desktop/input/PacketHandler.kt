package com.example.wandremote.desktop.input

import com.example.wandremote.desktop.AppState
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * Applies decoded packets. Called from the UDP receiver thread (Robot calls happen there);
 * all [AppState] writes are posted to the Swing EDT via [ui]. [tick] is called once per second
 * from another thread.
 */
class PacketHandler(
    private val state: AppState,
    private val input: InputController,
    private val reply: (Packet, InetSocketAddress) -> Unit,
) {
    private val count = AtomicInteger()
    @Volatile private var lastPacketNanos = 0L
    @Volatile private var connected = false

    // Receiver-thread only
    private var accX = 0f
    private var accY = 0f
    private var accScroll = 0f

    /** Buttons currently held down by the phone, released on disconnect so nothing stays stuck. */
    private val heldButtons = java.util.Collections.synchronizedSet(mutableSetOf<MouseButton>())

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater(block)

    fun handle(packet: Packet, from: InetSocketAddress) {
        count.incrementAndGet()
        lastPacketNanos = System.nanoTime()
        val ip = from.address.hostAddress
        when (packet) {
            is Packet.Hello -> markConnected("${packet.name} ($ip)")
            is Packet.Bye -> markDisconnected()
            else -> if (!connected) markConnected(ip)
        }
        when (packet) {
            is Packet.Move -> {
                accX += packet.dx
                accY += packet.dy
                val ix = accX.toInt()
                val iy = accY.toInt()
                if (ix != 0 || iy != 0) {
                    accX -= ix
                    accY -= iy
                    input.moveBy(ix, iy)
                }
            }
            is Packet.Button -> if (packet.pressed) {
                heldButtons += packet.button
                input.press(packet.button)
            } else {
                heldButtons -= packet.button
                input.release(packet.button)
            }
            is Packet.Scroll -> {
                accScroll += packet.dy
                val n = accScroll.toInt()
                if (n != 0) {
                    accScroll -= n
                    input.scroll(n.toFloat())
                }
            }
            is Packet.Absolute -> {
                val b = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
                input.moveTo(
                    b.x + (packet.x.coerceIn(0f, 1f) * (b.width - 1)).toInt(),
                    b.y + (packet.y.coerceIn(0f, 1f) * (b.height - 1)).toInt(),
                )
            }
            is Packet.Laser -> ui {
                state.laserVisible = packet.on
                state.laserX = packet.x
                state.laserY = packet.y
            }
            is Packet.Orientation -> {
                val q = floatArrayOf(packet.w, packet.x, packet.y, packet.z)
                ui { state.orientation = q }
            }
            is Packet.GestureEvent -> performAction(state.gestureActions[packet.gesture] ?: "None")
            is Packet.Ping -> reply(Packet.Pong(packet.timestampNanos), from)
            is Packet.Pong, is Packet.Hello, Packet.Bye -> Unit
        }
    }

    /** Once per second: publish packet rate and enforce the 3 s disconnect timeout. */
    fun tick() {
        val rate = count.getAndSet(0)
        ui { state.packetRate = rate }
        if (connected && System.nanoTime() - lastPacketNanos > 3_000_000_000L) markDisconnected()
    }

    fun reset() {
        markDisconnected()
        ui { state.packetRate = 0 }
    }

    private fun markConnected(name: String) {
        connected = true
        ui { if (state.phoneName != name) state.phoneName = name }
    }

    private fun markDisconnected() {
        connected = false
        releaseHeldButtons()
        ui {
            state.phoneName = null
            state.laserVisible = false
        }
    }

    private fun performAction(label: String) {
        when (label) {
            "Left arrow" -> input.keyCombo(KeyEvent.VK_LEFT)
            "Right arrow" -> input.keyCombo(KeyEvent.VK_RIGHT)
            "Up arrow" -> input.keyCombo(KeyEvent.VK_UP)
            "Down arrow" -> input.keyCombo(KeyEvent.VK_DOWN)
            "Page Up" -> input.keyCombo(KeyEvent.VK_PAGE_UP)
            "Page Down" -> input.keyCombo(KeyEvent.VK_PAGE_DOWN)
            "Left click" -> click(MouseButton.LEFT)
            "Right click" -> click(MouseButton.RIGHT)
            "Double click" -> { click(MouseButton.LEFT); click(MouseButton.LEFT) }
            "Ctrl+Z" -> input.keyCombo(KeyEvent.VK_CONTROL, KeyEvent.VK_Z)
            "Ctrl+Y" -> input.keyCombo(KeyEvent.VK_CONTROL, KeyEvent.VK_Y)
            "Esc" -> input.keyCombo(KeyEvent.VK_ESCAPE)
            "F5" -> input.keyCombo(KeyEvent.VK_F5)
            else -> Unit
        }
    }

    private fun releaseHeldButtons() {
        val held = synchronized(heldButtons) { heldButtons.toList().also { heldButtons.clear() } }
        held.forEach { input.release(it) }
    }

    private fun click(b: MouseButton) {
        input.press(b)
        input.release(b)
    }
}
