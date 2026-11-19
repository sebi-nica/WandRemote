package com.example.wandremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.wandremote.network.ConnectionManager
import com.example.wandremote.network.RecentPc
import com.example.wandremote.network.RecentPcs
import com.example.wandremote.sensors.SensorHub
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

    override fun onCleared() {
        connection.close()
    }
}
