package com.example.wandremote.desktop.network

import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.input.InputController
import com.example.wandremote.desktop.input.PacketHandler
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Owns the discovery responder, UDP receiver and the 1 s housekeeping timer. */
class Server(private val state: AppState) {
    private val input = InputController()
    private var discovery: DiscoveryResponder? = null
    private var receiver: UdpReceiver? = null
    private var handler: PacketHandler? = null
    private var timer: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (receiver != null) return
        val disc = DiscoveryResponder()
        lateinit var rx: UdpReceiver
        val h = PacketHandler(state, input) { p, a -> rx.send(p, a) }
        rx = UdpReceiver(h::handle)
        try {
            rx.start()
            disc.start()
        } catch (e: Exception) {
            rx.stop()
            disc.stop()
            SwingUtilities.invokeLater {
                state.serverError = "Could not bind ports: ${e.message}"
                state.serverRunning = false
            }
            return
        }
        discovery = disc
        receiver = rx
        handler = h
        timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "wand-tick").apply { isDaemon = true } }
            .also { it.scheduleAtFixedRate({ runCatching { h.tick() } }, 1, 1, TimeUnit.SECONDS) }
        SwingUtilities.invokeLater { state.serverError = null }
    }

    @Synchronized
    fun stop() {
        timer?.shutdownNow()
        receiver?.stop()
        discovery?.stop()
        handler?.reset()
        timer = null; receiver = null; discovery = null; handler = null
    }
}
