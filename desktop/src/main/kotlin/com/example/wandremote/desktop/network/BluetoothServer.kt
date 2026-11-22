package com.example.wandremote.desktop.network

import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.input.PacketHandler
import com.example.wandremote.desktop.input.PacketSource
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.StreamFraming
import java.io.InputStream
import java.io.OutputStream
import javax.bluetooth.LocalDevice
import javax.bluetooth.RemoteDevice
import javax.microedition.io.Connector
import javax.microedition.io.StreamConnection
import javax.microedition.io.StreamConnectionNotifier
import javax.swing.SwingUtilities

/**
 * Bluetooth RFCOMM (SPP) server using BlueCove. Registers an SDP record for [StreamFraming.RFCOMM_UUID]
 * and accepts one phone at a time. Any failure (missing native lib, no adapter, unsupported stack) is
 * reported through [AppState.bluetoothStatus] and never propagates, so Wi-Fi keeps working.
 */
class BluetoothServer(private val state: AppState, private val handler: PacketHandler) {
    private var notifier: StreamConnectionNotifier? = null
    private var acceptThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var activeConnection: StreamConnection? = null

    private fun status(text: String) {
        System.err.println("[bluetooth] $text")
        SwingUtilities.invokeLater { state.bluetoothStatus = text }
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        // Opening the service can block for a while on the native stack, so do it off the caller thread.
        acceptThread = Thread({ run() }, "wand-bt-accept").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        runCatching { activeConnection?.close() }
        runCatching { notifier?.close() }
        notifier = null
        acceptThread = null
    }

    private fun run() {
        val n: StreamConnectionNotifier
        try {
            val local = LocalDevice.getLocalDevice()
            val info = "${local.friendlyName} (${local.bluetoothAddress})"
            SwingUtilities.invokeLater { state.bluetoothLocal = info }
            val url = "btspp://localhost:${StreamFraming.RFCOMM_UUID.replace("-", "")};" +
                "name=${StreamFraming.RFCOMM_SERVICE_NAME};authenticate=false;encrypt=false"
            n = Connector.open(url) as StreamConnectionNotifier
        } catch (t: Throwable) {
            if (running) status("unavailable: ${describe(t)}")
            return
        }
        synchronized(this) {
            if (!running) {
                runCatching { n.close() }
                return
            }
            notifier = n
        }
        status("listening (${StreamFraming.RFCOMM_SERVICE_NAME})")
        while (running) {
            val conn = try {
                n.acceptAndOpen()
            } catch (t: Throwable) {
                if (!running) return
                status("unavailable: ${describe(t)}")
                return
            }
            serve(conn)
            if (running) status("listening (${StreamFraming.RFCOMM_SERVICE_NAME})")
        }
    }

    /** Handles one connection to completion (blocks the accept thread, so one phone at a time). */
    private fun serve(conn: StreamConnection) {
        activeConnection = conn
        val device = runCatching { RemoteDevice.getRemoteDevice(conn).let { it.getFriendlyName(false) ?: it.bluetoothAddress } }
            .getOrDefault("phone")
        status("connected: $device")
        var input: InputStream? = null
        var output: OutputStream? = null
        val source = object : PacketSource {
            override val label = "Bluetooth"
            override fun reply(packet: Packet) {
                val out = output ?: return
                try {
                    synchronized(out) { StreamFraming.write(out, packet); out.flush() }
                } catch (e: Exception) {
                    // connection dropped; the reader notices and cleans up
                }
            }
        }
        try {
            input = conn.openInputStream()
            output = conn.openOutputStream()
            while (running) {
                val packet = StreamFraming.read(input) ?: continue
                handler.handle(packet, source)
            }
        } catch (e: Exception) {
            // EOF or I/O error: phone went away
        } finally {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { conn.close() }
            activeConnection = null
            handler.disconnected(source)
        }
    }

    private fun describe(t: Throwable): String =
        "${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}"
}
