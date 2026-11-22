package com.example.wandremote.desktop.input

import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.ControlMode
import com.example.wandremote.desktop.ViewerBackend
import com.example.wandremote.desktop.ui.viewer.MAX_ZOOM
import com.example.wandremote.desktop.ui.viewer.MIN_ZOOM
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.RemoteKey
import com.example.wandremote.shared.RemoteMode
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.math.pow

/** Where a packet came from (UDP peer or Bluetooth stream) and how to answer it. */
interface PacketSource {
    /** Shown after the phone name, e.g. the IP address or "Bluetooth". */
    val label: String
    fun reply(packet: Packet)
}

/**
 * Applies decoded packets. Called from the UDP receiver thread (Robot calls happen there);
 * all [AppState] writes are posted to the Swing EDT via [ui]. [tick] is called once per second
 * from another thread.
 */
class PacketHandler(
    private val state: AppState,
    private val input: InputController,
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

    fun handle(packet: Packet, from: PacketSource) {
        count.incrementAndGet()
        lastPacketNanos = System.nanoTime()
        when (packet) {
            is Packet.Hello -> {
                markConnected("${packet.name} (${from.label})")
                screenSize().let { (w, h) -> from.reply(Packet.ScreenInfo(w, h)) }
            }
            is Packet.Bye -> markDisconnected()
            else -> if (!connected) markConnected(from.label)
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
            is Packet.Ping -> from.reply(Packet.Pong(packet.timestampNanos))
            is Packet.SetMode -> {
                val mode = when (packet.mode) {
                    RemoteMode.POINTER -> ControlMode.POINTER
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
            is Packet.Key -> {
                val code = keyCode(packet.key)
                if (packet.pressed) {
                    heldKeys += code
                    input.keyPress(code)
                } else {
                    heldKeys -= code
                    input.keyRelease(code)
                }
            }
            is Packet.Pong, is Packet.Hello, is Packet.ScreenInfo, Packet.Bye -> Unit
        }
    }

    /** The transport behind [source] closed (stream EOF/error); treated like a Bye. */
    fun disconnected(source: PacketSource) {
        markDisconnected()
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

    /** True when the Blender backend is selected; switching backends lets go of the orbit first. */
    private fun blenderActive(): Boolean {
        val backend = state.viewerBackend
        if (backend != lastBackend) {
            lastBackend = backend
            orbit.reset()
        }
        return backend == ViewerBackend.BLENDER
    }

    /** Keys currently held down by the phone, released on disconnect so nothing stays stuck. */
    private val heldKeys = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private fun keyCode(key: RemoteKey): Int = when (key) {
        RemoteKey.LEFT -> KeyEvent.VK_LEFT
        RemoteKey.RIGHT -> KeyEvent.VK_RIGHT
        RemoteKey.UP -> KeyEvent.VK_UP
        RemoteKey.DOWN -> KeyEvent.VK_DOWN
        RemoteKey.PAGE_UP -> KeyEvent.VK_PAGE_UP
        RemoteKey.PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN
        RemoteKey.ESCAPE -> KeyEvent.VK_ESCAPE
        RemoteKey.F5 -> KeyEvent.VK_F5
        RemoteKey.ENTER -> KeyEvent.VK_ENTER
        RemoteKey.SPACE -> KeyEvent.VK_SPACE
    }

    private fun releaseHeldButtons() {
        val keys = synchronized(heldKeys) { heldKeys.toList().also { heldKeys.clear() } }
        keys.forEach { input.keyRelease(it) }
        val held = synchronized(heldButtons) { heldButtons.toList().also { heldButtons.clear() } }
        held.forEach { input.release(it) }
    }

    /** Primary screen size in pixels (the phone only needs the aspect ratio). */
    private fun screenSize(): Pair<Int, Int> = try {
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        mode.width to mode.height
    } catch (e: Exception) { // headless or no display
        1920 to 1080
    }

    private companion object {
        /** Viewer zoom factor per Zoom step (one step = one wheel notch in Blender). */
        const val ZOOM_PER_STEP = 1.1f
    }
}
