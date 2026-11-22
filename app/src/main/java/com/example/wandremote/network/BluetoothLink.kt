package com.example.wandremote.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.StreamFraming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed interface BluetoothLinkState {
    data object Idle : BluetoothLinkState
    data class Connecting(val deviceName: String) : BluetoothLinkState

    /** [latencyMs] uses the [ConnectionState.Connected] LATENCY_PENDING / LATENCY_NONE semantics. */
    data class Connected(val deviceName: String, val latencyMs: Int) : BluetoothLinkState

    /** The last attempt or session ended with [message]; the link is not connected. */
    data class Error(val message: String) : BluetoothLinkState
}

/**
 * RFCOMM link to the PC companion app (PC = server, phone = client): the same packets as UDP, framed with
 * [StreamFraming]. Blocking socket I/O runs on Dispatchers.IO. Outgoing packets go through a channel drained by
 * one writer coroutine, so they stay ordered and [send] never blocks the caller.
 */
class BluetoothLink(context: Context) : PacketSink {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BluetoothLinkState>(BluetoothLinkState.Idle)
    val state: StateFlow<BluetoothLinkState> = _state.asStateFlow()

    private class Session(val outbox: Channel<Packet>, val job: Job) {
        @Volatile var socket: BluetoothSocket? = null
        @Volatile var connected = false
        @Volatile var heartbeat: Job? = null

        fun hardClose() {
            job.cancel()
            outbox.close()
            runCatching { socket?.close() } // also unblocks a pending connect() or read()
        }
    }

    private val lock = Any()
    @Volatile private var session: Session? = null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** True when the phone has Bluetooth and it is switched on. */
    fun isEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** Connects to the paired PC with [address]; ends any previous link first. The result lands in [state]. */
    fun connect(address: String) {
        val s: Session
        synchronized(lock) {
            closeLocked()
            val problem = when {
                adapter == null -> "This phone has no Bluetooth"
                !hasPermission() -> PERMISSION_MESSAGE
                !isEnabled() -> "Bluetooth is off"
                else -> null
            }
            if (problem != null) {
                _state.value = BluetoothLinkState.Error(problem)
                return
            }
            s = Session(Channel(Channel.UNLIMITED), Job(scope.coroutineContext[Job]))
            session = s
            _state.value = BluetoothLinkState.Connecting(address)
        }
        CoroutineScope(scope.coroutineContext + s.job).launch { run(s, address) }
    }

    private suspend fun run(s: Session, address: String) = coroutineScope {
        val bt = adapter ?: return@coroutineScope
        val socket: BluetoothSocket
        val name: String
        try {
            val device = bt.getRemoteDevice(address)
            name = device.name ?: address
            if (isCurrent(s)) _state.value = BluetoothLinkState.Connecting(name)
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString(StreamFraming.RFCOMM_UUID))
            s.socket = socket
        } catch (e: SecurityException) {
            fail(s, PERMISSION_MESSAGE)
            return@coroutineScope
        } catch (e: Exception) {
            fail(s, "Could not open a Bluetooth connection")
            return@coroutineScope
        }

        // Blocking connect() can't be cancelled by a coroutine: closing the socket makes it throw.
        val timeout = launch {
            delay(CONNECT_TIMEOUT_MS)
            runCatching { socket.close() }
        }
        try {
            bt.cancelDiscovery()
            socket.connect()
        } catch (e: SecurityException) {
            timeout.cancel()
            fail(s, PERMISSION_MESSAGE)
            return@coroutineScope
        } catch (e: IOException) {
            timeout.cancel()
            fail(s, "Could not reach the PC app. Is WandRemote running on the PC, and is the phone paired with it?")
            return@coroutineScope
        }
        timeout.cancel()
        if (!isCurrent(s)) return@coroutineScope // disconnected while connecting

        val input = socket.inputStream
        val output = socket.outputStream
        val lastPong = AtomicLong(System.nanoTime())
        s.connected = true
        _state.value = BluetoothLinkState.Connected(name, ConnectionState.Connected.LATENCY_PENDING)

        // Writer: drains the outbox in order; once the outbox is closed (disconnect) it closes the socket.
        launch {
            try {
                for (p in s.outbox) {
                    try {
                        StreamFraming.write(output, p)
                        output.flush()
                    } catch (e: IllegalArgumentException) {
                        // Packet too large to frame: skip it.
                    }
                }
            } catch (e: IOException) {
                fail(s, "Lost the Bluetooth connection")
                return@launch
            }
            s.hardClose()
        }
        // Reader: Pongs -> latency.
        launch {
            try {
                while (isActive) {
                    val pkt = StreamFraming.read(input) ?: continue
                    if (pkt is Packet.Pong) {
                        val now = System.nanoTime()
                        lastPong.set(now)
                        val ms = roundTripMs(now, pkt.timestampNanos)
                        _state.update { cur ->
                            if (cur is BluetoothLinkState.Connected && isCurrent(s)) cur.copy(latencyMs = ms) else cur
                        }
                    }
                }
            } catch (e: EOFException) {
                fail(s, "PC app closed the connection")
            } catch (e: IOException) {
                fail(s, "Lost the Bluetooth connection")
            }
        }
        // Hello every 2 s, Ping every 1 s, plus a no-response watchdog.
        s.heartbeat = launch {
            val hello = Packet.Hello(Build.MODEL ?: "Android")
            var tick = 0
            while (isActive) {
                if (tick % 2 == 0) s.outbox.trySend(hello)
                s.outbox.trySend(Packet.Ping(System.nanoTime()))
                tick++
                delay(1000)
                if (pongOverdue(System.nanoTime(), lastPong.get())) {
                    _state.update { cur ->
                        if (cur is BluetoothLinkState.Connected && isCurrent(s)) {
                            cur.copy(latencyMs = ConnectionState.Connected.LATENCY_NONE)
                        } else {
                            cur
                        }
                    }
                }
            }
        }
    }

    private fun isCurrent(s: Session): Boolean = session === s

    /** Ends session [s] with an error message, unless it was already replaced or closed. */
    private fun fail(s: Session, message: String) {
        synchronized(lock) {
            if (session !== s) return
            session = null
            s.hardClose()
            _state.value = BluetoothLinkState.Error(message)
        }
    }

    /** Queues a packet; never blocks. No-op unless connected. */
    override fun send(packet: Packet) {
        val s = session ?: return
        if (s.connected) s.outbox.trySend(packet)
    }

    fun disconnect() {
        synchronized(lock) {
            closeLocked()
            _state.value = BluetoothLinkState.Idle
        }
    }

    private fun closeLocked() {
        val s = session ?: return
        session = null
        if (s.connected) {
            // Let the writer flush Bye, then it closes the socket; the delayed hard close is the safety net.
            s.heartbeat?.cancel()
            s.outbox.trySend(Packet.Bye)
            s.outbox.close()
            scope.launch {
                delay(500)
                s.hardClose()
            }
        } else {
            s.hardClose()
        }
    }

    fun close() {
        disconnect()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val PERMISSION_MESSAGE = "Bluetooth permission was denied. Allow \"Nearby devices\"."
    }
}
