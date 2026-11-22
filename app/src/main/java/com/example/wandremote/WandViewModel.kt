package com.example.wandremote

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wandremote.bluetooth.BluetoothHidMouse
import com.example.wandremote.bluetooth.BluetoothHidState
import com.example.wandremote.network.BluetoothLink
import com.example.wandremote.network.BluetoothLinkState
import com.example.wandremote.network.ConnectionManager
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.network.PacketSink
import com.example.wandremote.network.RecentPc
import com.example.wandremote.network.RecentPcs
import com.example.wandremote.network.companionSessionId
import com.example.wandremote.network.mergeConnectionState
import com.example.wandremote.pointer.LaserController
import com.example.wandremote.sensors.AimSensor
import com.example.wandremote.sensors.SensorHub
import com.example.wandremote.settings.AppSettings
import com.example.wandremote.settings.SettingsRepository
import com.example.wandremote.ui.Haptics
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.PairingInfo
import com.example.wandremote.shared.Protocol
import com.example.wandremote.shared.RemoteMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicLong

/** App-level holder so the connection and sensors survive recomposition and rotation. */
class WandViewModel(app: Application) : AndroidViewModel(app) {
    val connection = ConnectionManager(app)
    val bluetooth = BluetoothHidMouse(app)
    val bluetoothLink = BluetoothLink(app)
    val sensors = SensorHub(app)
    val haptics = Haptics(app)
    private val recentStore = RecentPcs(app)
    private val settingsRepository = SettingsRepository(app)

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun updateSettings(transform: AppSettings.() -> AppSettings) = settingsRepository.update(transform)

    private fun applySettings(s: AppSettings) {
        sensors.tuning = s.toPointerTuning()
        sensors.gyroBias = s.gyroBias
        haptics.strength = s.vibrationStrength
    }

    init {
        applySettings(settings.value)
        settings.onEach(::applySettings).launchIn(viewModelScope)
    }

    /** Merged UI state: the Bluetooth HID connection if there is one, then the Bluetooth link, otherwise UDP. */
    val connectionState: StateFlow<ConnectionState> =
        combine(connection.state, bluetooth.state, bluetoothLink.state) { udp, bt, link ->
            mergeConnectionState(
                udp,
                (bt as? BluetoothHidState.Connected)?.deviceName,
                link as? BluetoothLinkState.Connected,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)

    /** Routes packets to whichever transport is currently connected. */
    val output: PacketSink = PacketSink { packet: Packet ->
        if (connectionState.value !is ConnectionState.Disconnected) sentPackets.incrementAndGet()
        when (connectionState.value) {
            is ConnectionState.ConnectedBluetooth -> bluetooth.send(packet)
            is ConnectionState.ConnectedLink -> bluetoothLink.send(packet)
            else -> connection.send(packet)
        }
    }

    private val sentPackets = AtomicLong()

    /** Total packets handed to [output] while a transport was connected; sample it over time for a rate. */
    val packetsSent: Long get() = sentPackets.get()

    /** Connects to a paired PC over Bluetooth HID (the HID app must already be registered). */
    fun connectBluetooth(address: String) {
        connection.disconnect()
        bluetoothLink.disconnect()
        bluetooth.connect(address)
    }

    /** Connects to the PC companion app over a Bluetooth RFCOMM link (paired device [address]). */
    fun connectLink(address: String) {
        connection.disconnect()
        bluetooth.disconnect()
        bluetoothLink.connect(address)
    }

    /** Disconnects whichever transport is active. */
    fun disconnect() {
        connection.disconnect()
        bluetooth.disconnect()
        bluetoothLink.disconnect()
    }

    private val _recent = MutableStateFlow(recentStore.load())
    val recent: StateFlow<List<RecentPc>> = _recent.asStateFlow()

    fun connect(name: String, host: String, port: Int) {
        bluetooth.disconnect()
        bluetoothLink.disconnect()
        connection.connect(host, port)
        _recent.value = recentStore.add(RecentPc(name, host, port))
    }

    private val _pendingPairing = MutableStateFlow<PairingInfo?>(null)

    /** A scanned/opened pairing link waiting to be connected to; consumed by the UI via [consumePairing]. */
    val pendingPairing: StateFlow<PairingInfo?> = _pendingPairing.asStateFlow()

    /** Parses a wandremote:// link or QR payload. Returns false (after showing a toast) if it is not valid. */
    fun handlePairingLink(text: String): Boolean {
        val info = Protocol.parsePairingUri(text)
        if (info == null) {
            Toast.makeText(getApplication(), "Not a WandRemote QR code", Toast.LENGTH_SHORT).show()
            return false
        }
        _pendingPairing.value = info
        return true
    }

    fun consumePairing() {
        _pendingPairing.value = null
    }

    // ---- Laser pointer ----

    val aim = AimSensor(app)

    /** The PC's primary screen size as reported by the companion (Wi-Fi or Bluetooth link); null when unknown. */
    val pcScreen: StateFlow<Packet.ScreenInfo?> =
        combine(connection.screenInfo, bluetoothLink.screenInfo) { udp, link -> link ?: udp }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** PC screen height / width; [DEFAULT_PC_ASPECT] while unknown. */
    val pcAspect: StateFlow<Float> = pcScreen
        .map { info -> if (info != null && info.widthPx > 0 && info.heightPx > 0) info.heightPx.toFloat() / info.widthPx else DEFAULT_PC_ASPECT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_PC_ASPECT)

    private var currentMode = RemoteMode.POINTER

    /** Tells the PC which mode the phone is in; remembered and re-sent after every (re)connect. */
    fun setMode(mode: RemoteMode) {
        currentMode = mode
        output.send(Packet.SetMode(mode))
    }

    val laser = LaserController(
        aimSensor = aim,
        output = output,
        rangeDeg = { settings.value.laserRangeDeg },
        aspect = { pcAspect.value },
    )

    init {
        connectionState
            .map { it.companionSessionId() }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach {
                delay(300) // let the new session start its sender
                output.send(Packet.SetMode(currentMode))
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        connection.close()
        bluetooth.close()
        bluetoothLink.close()
    }

    companion object {
        const val DEFAULT_PC_ASPECT = 9f / 16f
    }
}
