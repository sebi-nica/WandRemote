package com.example.wandremote.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.PacketCodec
import com.example.wandremote.shared.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    /** Connected as a Bluetooth HID mouse to [deviceName]; no latency measurement. */
    data class ConnectedBluetooth(val deviceName: String) : ConnectionState

    /** [latencyMs]: round-trip in ms; [LATENCY_PENDING] before the first Pong; [LATENCY_NONE] when the PC stopped answering. */
    data class Connected(val host: String, val port: Int, val latencyMs: Int) : ConnectionState {
        companion object {
            const val LATENCY_PENDING = 0
            const val LATENCY_NONE = -1
        }
    }
}

data class DiscoveredPc(val name: String, val ip: String, val udpPort: Int, val tcpPort: Int)

/**
 * UDP connection to the PC plus LAN discovery. All socket I/O runs on Dispatchers.IO.
 * Outgoing packets go through a channel drained by one coroutine, so they stay ordered
 * and [send] never blocks the caller (e.g. a sensor callback).
 */
class ConnectionManager(context: Context) : PacketSink {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val discovered: StateFlow<List<DiscoveredPc>> = _discovered.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private class Session(val socket: DatagramSocket, val outbox: Channel<Packet>, val job: Job)

    @Volatile private var session: Session? = null
    private var scanJob: Job? = null

    // ---- Discovery ----

    /** Starts a ~1.5 s broadcast scan; results land in [discovered]. No-op if already scanning. */
    fun scan() {
        if (scanJob?.isActive == true) return
        _scanning.value = true
        scanJob = scope.launch {
            try {
                _discovered.value = discoverOnce()
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun discoverOnce(): List<DiscoveredPc> {
        val found = LinkedHashMap<String, DiscoveredPc>()
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("wandremote-discovery")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val req = Protocol.DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
                runCatching {
                    socket.send(
                        DatagramPacket(req, req.size, InetAddress.getByName("255.255.255.255"), Protocol.DISCOVERY_PORT),
                    )
                }
                val buf = ByteArray(512)
                val deadline = System.nanoTime() + 1_500_000_000L
                while (System.nanoTime() < deadline) {
                    val dp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(dp)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val ip = dp.address.hostAddress ?: continue
                    val pc = parseReply(String(dp.data, 0, dp.length, Charsets.UTF_8), ip)
                    if (pc != null) found[pc.ip] = pc
                }
            }
        } catch (e: Exception) {
            // Network unavailable etc.: return what we have.
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
        return found.values.toList()
    }

    private fun parseReply(text: String, ip: String): DiscoveredPc? {
        val parts = text.trim().split('|')
        if (parts.size < 5 || parts[0] != Protocol.DISCOVERY_RESPONSE_PREFIX) return null
        val udp = parts[parts.size - 2].toIntOrNull() ?: return null
        val tcp = parts[parts.size - 1].toIntOrNull() ?: return null
        val name = parts.subList(2, parts.size - 2).joinToString("|")
        return DiscoveredPc(name, ip, udp, tcp)
    }

    // ---- Connection ----

    fun connect(host: String, port: Int) {
        closeSession()
        _state.value = ConnectionState.Connected(host, port, ConnectionState.Connected.LATENCY_PENDING)
        val outbox = Channel<Packet>(Channel.UNLIMITED)
        val socket = DatagramSocket()
        val job = Job(scope.coroutineContext[Job])
        session = Session(socket, outbox, job)
        val jobScope = CoroutineScope(scope.coroutineContext + job)

        jobScope.launch {
            val address = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                _state.value = ConnectionState.Connected(host, port, ConnectionState.Connected.LATENCY_NONE)
                return@launch
            }
            socket.soTimeout = 500
            val lastPong = AtomicLong(System.nanoTime())

            // Sender: drains the outbox in order.
            launch {
                for (p in outbox) {
                    val bytes = PacketCodec.encode(p)
                    runCatching { socket.send(DatagramPacket(bytes, bytes.size, address, port)) }
                }
            }
            // Receiver: Pongs -> latency.
            launch {
                val buf = ByteArray(PacketCodec.MAX_SIZE)
                while (isActive) {
                    val dp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(dp)
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        break // socket closed
                    }
                    val pkt = PacketCodec.decode(dp.data, dp.length)
                    if (pkt is Packet.Pong) {
                        val now = System.nanoTime()
                        lastPong.set(now)
                        val ms = ((now - pkt.timestampNanos) / 1_000_000L).toInt().coerceAtLeast(1)
                        _state.update { cur ->
                            if (cur is ConnectionState.Connected) cur.copy(latencyMs = ms) else cur
                        }
                    }
                }
            }
            // Hello every 2 s.
            launch {
                val hello = Packet.Hello(Build.MODEL ?: "Android")
                while (isActive) {
                    outbox.trySend(hello)
                    delay(2000)
                }
            }
            // Ping every 1 s + no-response watchdog.
            launch {
                while (isActive) {
                    outbox.trySend(Packet.Ping(System.nanoTime()))
                    delay(1000)
                    if (System.nanoTime() - lastPong.get() > 4_000_000_000L) {
                        _state.update { cur ->
                            if (cur is ConnectionState.Connected) {
                                cur.copy(latencyMs = ConnectionState.Connected.LATENCY_NONE)
                            } else {
                                cur
                            }
                        }
                    }
                }
            }
        }
    }

    /** Queues a packet; never blocks. No-op when not connected. */
    override fun send(packet: Packet) {
        session?.outbox?.trySend(packet)
    }

    fun disconnect() {
        closeSession()
        _state.value = ConnectionState.Disconnected
    }

    private fun closeSession() {
        val s = session ?: return
        session = null
        val target = _state.value as? ConnectionState.Connected
        scope.launch {
            if (target != null) {
                // Direct send so Bye goes out even though the session's sender is being cancelled.
                withTimeoutOrNull(500) {
                    runCatching {
                        val bytes = PacketCodec.encode(Packet.Bye)
                        s.socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target.host), target.port))
                    }
                }
            }
            s.job.cancel()
            s.outbox.close()
            s.socket.close()
        }
    }

    fun close() {
        disconnect()
    }
}
