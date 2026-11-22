package com.example.wandremote.desktop.network

import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.input.InputController
import com.example.wandremote.desktop.input.PacketHandler
import com.example.wandremote.desktop.input.PacketSource
import com.example.wandremote.shared.Packet
import java.net.InetSocketAddress
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
    private var bluetooth: BluetoothServer? = null
    private var timer: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (receiver != null) return
        val disc = DiscoveryResponder()
        lateinit var rx: UdpReceiver
        val h = PacketHandler(state, input)
        rx = UdpReceiver { p, a -> h.handle(p, UdpSource(a, rx)) }
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
        bluetooth = BluetoothServer(state, h).also { it.start() }
        timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "wand-tick").apply { isDaemon = true } }
            .also { it.scheduleAtFixedRate({ runCatching { h.tick() } }, 1, 1, TimeUnit.SECONDS) }
        SwingUtilities.invokeLater { state.serverError = null }
    }

    @Synchronized
    fun stop() {
        timer?.shutdownNow()
        bluetooth?.stop()
        receiver?.stop()
        discovery?.stop()
        handler?.reset()
        timer = null; bluetooth = null; receiver = null; discovery = null; handler = null
    }
}

private class UdpSource(private val address: InetSocketAddress, private val rx: UdpReceiver) : PacketSource {
    override val label: String get() = address.address.hostAddress
    override fun reply(packet: Packet) = rx.send(packet, address)
}
