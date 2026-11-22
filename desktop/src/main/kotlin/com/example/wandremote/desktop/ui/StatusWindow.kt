package com.example.wandremote.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.ViewerBackend
import com.example.wandremote.desktop.ui.viewer.Viewer3D
import com.example.wandremote.desktop.ui.viewer.ViewerControls
import com.example.wandremote.shared.Protocol
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private enum class Section(val label: String, val icon: ImageVector) {
    STATUS("Status", Icons.Filled.Dashboard),
    PAIRING("Pairing", Icons.Filled.QrCode2),
    VIEWER("3D viewer", Icons.Filled.ViewInAr),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun StatusWindow(state: AppState) {
    var section by remember { mutableStateOf(Section.STATUS) }
    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Section.entries.forEach { s ->
                    NavigationRailItem(
                        selected = s == section,
                        onClick = { section = s },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(section.label, style = MaterialTheme.typography.headlineMedium)
                when (section) {
                    Section.STATUS -> StatusSection(state)
                    Section.PAIRING -> PairingSection(state)
                    Section.VIEWER -> ViewerSection(state)
                    Section.SETTINGS -> SettingsSection(state)
                }
            }
        }
    }
}

@Composable
private fun StatusSection(state: AppState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connected phone", style = MaterialTheme.typography.titleMedium)
            val phone = state.phoneName
            if (phone == null) {
                Text("No phone connected")
            } else {
                LabeledValue("Name", phone)
                LabeledValue("Latency", if (state.latencyMs < 0) "—" else "${state.latencyMs} ms")
                LabeledValue("Packet rate", "${state.packetRate} pkt/s")
                LabeledValue("Mode", state.mode.label)
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            val status = if (state.serverRunning) "listening" else "stopped"
            LabeledValue("UDP ${Protocol.DEFAULT_UDP_PORT}", status)
            LabeledValue("Discovery ${Protocol.DISCOVERY_PORT}", status)
            LabeledValue("Bluetooth", if (state.serverRunning) state.bluetoothStatus else "stopped")
            state.bluetoothLocal?.let { LabeledValue("This PC (Bluetooth)", it) }
            state.serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { state.serverRunning = !state.serverRunning }) {
                Text(if (state.serverRunning) "Stop server" else "Start server")
            }
        }
    }
}

@Composable
private fun PairingSection(state: AppState) {
    val uri = Protocol.pairingUri(state.localIp, Protocol.DEFAULT_UDP_PORT)
    LabeledValue("Local IP", state.localIp)
    LabeledValue("UDP port", Protocol.DEFAULT_UDP_PORT.toString())
    QrCode(uri, Modifier.size(200.dp))
    Text(uri, fontFamily = FontFamily.Monospace)
    OutlinedButton(onClick = {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(uri), null)
    }) { Text("Copy") }
    Text("Bluetooth: pair your phone with this PC in Windows Bluetooth settings, then pick this PC in the app's Bluetooth list.")
}

@Composable
private fun ViewerSection(state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ViewerBackend.entries.forEach { backend ->
            Row(
                Modifier.selectable(
                    selected = state.viewerBackend == backend,
                    onClick = { state.viewerBackend = backend },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.viewerBackend == backend, onClick = null)
                Spacer(Modifier.width(6.dp))
                Text(backend.label)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { state.viewerOpen = true }) { Text("Open 3D viewer window") }
        Text(
            if (state.viewerBackend == ViewerBackend.BLENDER) {
                "Blender emulation: orientation orbits with the middle mouse button, zoom scrolls the wheel."
            } else {
                "Mode: ${state.mode.label}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Viewer3D(state, Modifier.fillMaxWidth().height(300.dp))
    ViewerControls(state)
}

@Composable
private fun SettingsSection(state: AppState) {
    SwitchRow("Start minimized to tray", state.startMinimized) { state.startMinimized = it }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PortField("UDP port", Protocol.DEFAULT_UDP_PORT)
        PortField("Discovery port", Protocol.DISCOVERY_PORT)
    }
}

@Composable
private fun PortField(label: String, port: Int) {
    OutlinedTextField(
        value = port.toString(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(140.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text(label, Modifier.width(140.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
