package com.example.wandremote.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.wandremote.network.PacketSink
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

sealed interface BluetoothHidState {
    /** Nothing started yet (the HID profile is only registered on demand). */
    data object Idle : BluetoothHidState
    data object Unavailable : BluetoothHidState
    data object PermissionDenied : BluetoothHidState
    data object Off : BluetoothHidState
    data object Registering : BluetoothHidState

    /** Registered as a HID mouse, no host connected. */
    data object Ready : BluetoothHidState
    data class Connecting(val deviceName: String) : BluetoothHidState
    data class Connected(val deviceName: String) : BluetoothHidState
    data class Error(val message: String) : BluetoothHidState
}

data class PairedDevice(val name: String, val address: String, val isComputer: Boolean)

/**
 * Registers the phone as a Bluetooth HID mouse + keyboard combo ([BluetoothHidDevice], API 28+) and turns
 * [Packet.Move], [Packet.Button], [Packet.Scroll] and [Packet.Key] into HID reports. All report work happens on one background thread.
 *
 * Every public entry point that touches the Bluetooth API first checks [hasPermission] (BLUETOOTH_CONNECT is
 * only a runtime permission on API 31+), hence the class-wide MissingPermission suppression.
 */
@SuppressLint("MissingPermission")
class BluetoothHidMouse(context: Context) : PacketSink {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _state = MutableStateFlow<BluetoothHidState>(BluetoothHidState.Idle)
    val state: StateFlow<BluetoothHidState> = _state.asStateFlow()

    /** Single thread: callbacks, report sending and accumulator state. Keeps reports ordered. */
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "bt-hid").apply { isDaemon = true } }

    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile var isConnected = false
        private set
    @Volatile private var registered = false

    // Touched only on [executor].
    private var buttons = 0
    private val pressedKeys = ArrayList<Int>() // HID usages currently held, in press order
    private val accX = FractionAccumulator()
    private val accY = FractionAccumulator()
    private val wheel = WheelAccumulator()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidMouse.registered = registered
            if (registered) {
                if (!isConnected) _state.value = BluetoothHidState.Ready
                // Host that was already connected when we registered.
                if (pluggedDevice != null &&
                    hidDevice?.getConnectionState(pluggedDevice) == BluetoothProfile.STATE_CONNECTED
                ) {
                    onHostState(pluggedDevice, BluetoothProfile.STATE_CONNECTED)
                }
            } else if (_state.value is BluetoothHidState.Registering) {
                _state.value = BluetoothHidState.Error("Bluetooth mouse registration failed")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            onHostState(device, state)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid
            register(hid)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            registered = false
            resetConnection()
            _state.value = BluetoothHidState.Idle
        }
    }

    private fun register(hid: BluetoothHidDevice) {
        if (!hasPermission()) {
            _state.value = BluetoothHidState.PermissionDenied
            return
        }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "WandRemote",
            "WandRemote air mouse",
            "WandRemote",
            BluetoothHidDevice.SUBCLASS1_COMBO, // mouse + keyboard (arrow keys); hosts may need a re-pair
            HidMouseReport.DESCRIPTOR,
        )
        val ok = try {
            hid.registerApp(sdp, null, null, executor, callback)
        } catch (e: SecurityException) {
            _state.value = BluetoothHidState.PermissionDenied
            return
        }
        if (!ok) _state.value = BluetoothHidState.Error("Could not register the Bluetooth mouse")
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Brings up the HID profile (call after permissions are granted). Safe to call repeatedly. */
    fun start() {
        val bt = adapter
        if (bt == null) {
            _state.value = BluetoothHidState.Unavailable
            return
        }
        if (!hasPermission()) {
            _state.value = BluetoothHidState.PermissionDenied
            return
        }
        if (!bt.isEnabled) {
            _state.value = BluetoothHidState.Off
            return
        }
        val existing = hidDevice
        if (existing != null) {
            if (!registered) {
                _state.value = BluetoothHidState.Registering
                register(existing)
            }
            return
        }
        _state.value = BluetoothHidState.Registering
        val requested = try {
            bt.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            _state.value = BluetoothHidState.PermissionDenied
            return
        }
        if (!requested) _state.value = BluetoothHidState.Unavailable
    }

    fun markPermissionDenied() {
        _state.value = BluetoothHidState.PermissionDenied
    }

    /** Paired devices, computers first. Empty if permission is missing. */
    fun bondedDevices(): List<PairedDevice> {
        val bt = adapter ?: return emptyList()
        if (!hasPermission()) return emptyList()
        return try {
            bt.bondedDevices.orEmpty().map {
                PairedDevice(
                    name = it.name ?: it.address,
                    address = it.address,
                    isComputer = it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER,
                )
            }.sortedWith(compareByDescending<PairedDevice> { it.isComputer }.thenBy { it.name.lowercase() })
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(address: String) {
        val hid = hidDevice
        val bt = adapter
        if (hid == null || bt == null || !registered || !hasPermission()) return
        try {
            val device = bt.getRemoteDevice(address)
            val name = device.name ?: address
            _state.value = BluetoothHidState.Connecting(name)
            if (!hid.connect(device)) _state.value = BluetoothHidState.Error("Could not connect to $name")
        } catch (e: SecurityException) {
            _state.value = BluetoothHidState.PermissionDenied
        } catch (e: IllegalArgumentException) {
            _state.value = BluetoothHidState.Error("Invalid device")
        }
    }

    fun disconnect() {
        val hid = hidDevice
        val dev = host
        if (hid != null && dev != null && hasPermission()) {
            releaseAll()
            try {
                hid.disconnect(dev)
            } catch (e: SecurityException) {
                // permission revoked meanwhile
            }
        } else if (_state.value is BluetoothHidState.Connecting && registered) {
            _state.value = BluetoothHidState.Ready
        }
    }

    /** Releases any held mouse button and keyboard key on the host. */
    fun releaseAll() {
        executor.execute {
            if (buttons != 0) {
                buttons = 0
                sendReport(0, 0, 0)
            }
            if (pressedKeys.isNotEmpty()) {
                pressedKeys.clear()
                sendKeyboardReport()
            }
        }
    }

    override fun send(packet: Packet) {
        if (!isConnected) return
        executor.execute {
            when (packet) {
                is Packet.Move -> {
                    val dx = accX.add(packet.dx)
                    val dy = accY.add(packet.dy)
                    for ((cx, cy) in HidMouseReport.splitMove(dx, dy)) sendReport(cx, cy, 0)
                }
                is Packet.Button -> {
                    val bit = when (packet.button) {
                        MouseButton.LEFT -> HidMouseReport.BUTTON_LEFT
                        MouseButton.RIGHT -> HidMouseReport.BUTTON_RIGHT
                        MouseButton.MIDDLE -> HidMouseReport.BUTTON_MIDDLE
                    }
                    val next = if (packet.pressed) buttons or bit else buttons and bit.inv()
                    if (next != buttons) {
                        buttons = next
                        sendReport(0, 0, 0)
                    }
                }
                is Packet.Scroll -> {
                    var left = wheel.add(packet.dy)
                    while (left != 0) {
                        val c = left.coerceIn(-HidMouseReport.MAX_DELTA, HidMouseReport.MAX_DELTA)
                        sendReport(0, 0, c)
                        left -= c
                    }
                }
                is Packet.Key -> {
                    val usage = HidMouseReport.keyUsage(packet.key)
                    val changed = if (packet.pressed) {
                        !pressedKeys.contains(usage) && pressedKeys.add(usage)
                    } else {
                        pressedKeys.remove(usage)
                    }
                    if (changed) sendKeyboardReport()
                }
                else -> Unit
            }
        }
    }

    /** Executor thread only. */
    private fun sendReport(dx: Int, dy: Int, wheelSteps: Int) {
        val hid = hidDevice ?: return
        val dev = host ?: return
        if (!hasPermission()) return
        try {
            hid.sendReport(dev, HidMouseReport.REPORT_ID, HidMouseReport.pack(buttons, dx, dy, wheelSteps))
        } catch (e: SecurityException) {
            // permission revoked while running
        }
    }

    /** Executor thread only. */
    private fun sendKeyboardReport() {
        val hid = hidDevice ?: return
        val dev = host ?: return
        if (!hasPermission()) return
        try {
            hid.sendReport(dev, HidMouseReport.KEYBOARD_REPORT_ID, HidMouseReport.packKeyboard(0, pressedKeys))
        } catch (e: SecurityException) {
            // permission revoked while running
        }
    }

    private fun onHostState(device: BluetoothDevice, state: Int) {
        val name = try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        }
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                host = device
                buttons = 0
                pressedKeys.clear()
                accX.reset()
                accY.reset()
                wheel.reset()
                isConnected = true
                _state.value = BluetoothHidState.Connected(name)
            }
            BluetoothProfile.STATE_CONNECTING -> if (!isConnected) _state.value = BluetoothHidState.Connecting(name)
            BluetoothProfile.STATE_DISCONNECTING -> Unit
            else -> if (host == null || host == device) {
                resetConnection()
                if (registered) _state.value = BluetoothHidState.Ready
            }
        }
    }

    private fun resetConnection() {
        isConnected = false
        host = null
        buttons = 0
        try {
            executor.execute { pressedKeys.clear() } // the key list belongs to the executor thread
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // already shut down
        }
    }

    fun close() {
        val hid = hidDevice
        hidDevice = null
        registered = false
        resetConnection()
        if (hid != null) {
            try {
                if (hasPermission()) hid.unregisterApp()
            } catch (e: SecurityException) {
                // ignore
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        executor.shutdown()
    }
}
