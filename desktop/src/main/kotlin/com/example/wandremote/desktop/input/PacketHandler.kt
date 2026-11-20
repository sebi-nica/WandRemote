package com.example.wandremote.desktop.input

import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.ControlMode
import com.example.wandremote.desktop.ViewerBackend
import com.example.wandremote.desktop.ui.viewer.MAX_ZOOM
import com.example.wandremote.desktop.ui.viewer.MIN_ZOOM
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.RemoteMode
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.math.pow

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

    // Blender emulation (3D mode): orientation -> middle-mouse orbit, zoom -> wheel.
    @Volatile private var threeD = false
    private var lastBackend = state.viewerBackend
    private val orbit = BlenderOrbit(object : OrbitOutput {
        override fun middleButton(down: Boolean) {
            if (down) {
                heldButtons += MouseButton.MIDDLE
                input.press(MouseButton.MIDDLE)
            } else {
                heldButtons -= MouseButton.MIDDLE
                input.release(MouseButton.MIDDLE)
            }
        }
        override fun moveBy(dx: Int, dy: Int) = input.moveBy(dx, dy)
        override fun wheel(notches: Int) = input.scroll(notches.toFloat())
    })
    private val orbitTimer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "wand-orbit").apply { isDaemon = true } }
            .also { it.scheduleAtFixedRate({ runCatching { orbit.poll(System.nanoTime()) } }, 50, 50, TimeUnit.MILLISECONDS) }

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
            is Packet.Orientation -> if (threeD) {
                val q = floatArrayOf(packet.w, packet.x, packet.y, packet.z)
                ui { state.orientation = q }
                if (blenderActive()) orbit.onOrientation(packet.w, packet.x, packet.y, packet.z, System.nanoTime())
            }
            is Packet.GestureEvent -> performAction(state.gestureActions[packet.gesture] ?: "None")
            is Packet.Ping -> reply(Packet.Pong(packet.timestampNanos), from)
            is Packet.SetMode -> {
                val mode = when (packet.mode) {
                    RemoteMode.POINTER -> ControlMode.POINTER
                    RemoteMode.PRESENTER -> ControlMode.PRESENTER
                    RemoteMode.THREE_D -> ControlMode.THREE_D
                }
                threeD = mode == ControlMode.THREE_D
                if (!threeD) orbit.reset()
                ui { state.mode = mode }
            }
            is Packet.Zoom -> if (threeD) {
                if (blenderActive()) {
                    orbit.onZoom(packet.amount)
                } else if (!packet.amount.isNaN()) {
                    val factor = ZOOM_PER_STEP.pow(packet.amount.coerceIn(-50f, 50f))
                    ui { state.zoom = (state.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM) }
                }
            }
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
        orbitTimer.shutdownNow()
        markDisconnected()
        ui { state.packetRate = 0 }
    }

    private fun markConnected(name: String) {
        connected = true
        ui { if (state.phoneName != name) state.phoneName = name }
    }

    private fun markDisconnected() {
        connected = false
        orbit.reset()
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

    /** True when the Blender backend is selected; switching backends lets go of the orbit first. */
    private fun blenderActive(): Boolean {
        val backend = state.viewerBackend
        if (backend != lastBackend) {
            lastBackend = backend
            orbit.reset()
        }
        return backend == ViewerBackend.BLENDER
    }

    private fun releaseHeldButtons() {
        val held = synchronized(heldButtons) { heldButtons.toList().also { heldButtons.clear() } }
        held.forEach { input.release(it) }
    }

    private fun click(b: MouseButton) {
        input.press(b)
        input.release(b)
    }

    private companion object {
        /** Viewer zoom factor per Zoom step (one step = one wheel notch in Blender). */
        const val ZOOM_PER_STEP = 1.1f
    }
}
