package com.example.wandremote

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.example.wandremote.network.ConnectionManager
import com.example.wandremote.network.RecentPc
import com.example.wandremote.network.RecentPcs
import com.example.wandremote.sensors.SensorHub
import com.example.wandremote.shared.PairingInfo
import com.example.wandremote.shared.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-level holder so the connection and sensors survive recomposition and rotation. */
class WandViewModel(app: Application) : AndroidViewModel(app) {
    val connection = ConnectionManager(app)
    val sensors = SensorHub(app)
    private val recentStore = RecentPcs(app)

    private val _recent = MutableStateFlow(recentStore.load())
    val recent: StateFlow<List<RecentPc>> = _recent.asStateFlow()

    fun connect(name: String, host: String, port: Int) {
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

    override fun onCleared() {
        connection.close()
    }
}
