package com.example.wandremote.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.SHORTCUT_OPTIONS
import com.example.wandremote.desktop.ViewerBackend
import com.example.wandremote.shared.Gesture
import com.example.wandremote.shared.Protocol
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.math.cos
import kotlin.math.sin

private enum class Section(val label: String, val icon: ImageVector) {
    STATUS("Status", Icons.Filled.Dashboard),
    PAIRING("Pairing", Icons.Filled.QrCode2),
    GESTURES("Gestures", Icons.Filled.TouchApp),
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
                    Section.GESTURES -> GesturesSection(state)
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
            LabeledValue("TCP ${Protocol.DEFAULT_TCP_PORT}", status)
            LabeledValue("Discovery ${Protocol.DISCOVERY_PORT}", status)
            state.serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { state.serverRunning = !state.serverRunning }) {
                Text(if (state.serverRunning) "Stop server" else "Start server")
            }
        }
    }
}

@Composable
private fun PairingSection(state: AppState) {
    val uri = Protocol.pairingUri(state.localIp, Protocol.DEFAULT_UDP_PORT, Protocol.DEFAULT_TCP_PORT)
    LabeledValue("Local IP", state.localIp)
    LabeledValue("UDP port", Protocol.DEFAULT_UDP_PORT.toString())
    LabeledValue("TCP port", Protocol.DEFAULT_TCP_PORT.toString())
    QrCode(uri, Modifier.size(200.dp))
    Text(uri, fontFamily = FontFamily.Monospace)
    OutlinedButton(onClick = {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(uri), null)
    }) { Text("Copy") }
}

@Composable
private fun GesturesSection(state: AppState) {
    Row(Modifier.fillMaxWidth()) {
        Text("Gesture", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text("Action", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    }
    HorizontalDivider()
    Gesture.entries.forEach { gesture ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(gesture.name, Modifier.weight(1f))
            Box(Modifier.weight(1f)) {
                var open by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { open = true }) {
                    Text(state.gestureActions[gesture] ?: "None")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    SHORTCUT_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                state.gestureActions[gesture] = option
                                open = false
                            },
                        )
                    }
                }
            }
        }
    }
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
    WireframeCube(Modifier.fillMaxWidth().height(300.dp))
}

@Composable
private fun WireframeCube(modifier: Modifier) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { angle = (it / 1_000_000_000f) * 0.8f }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val scale = size.minDimension * 0.28f
        val center = Offset(size.width / 2, size.height / 2)
        val verts = List(8) { i ->
            Triple(if (i and 1 == 0) -1f else 1f, if (i and 2 == 0) -1f else 1f, if (i and 4 == 0) -1f else 1f)
        }
        val projected = verts.map { (x, y, z) ->
            // rotate around Y then X, then simple perspective projection
            val x1 = x * cos(angle) + z * sin(angle)
            val z1 = -x * sin(angle) + z * cos(angle)
            val tilt = 0.5f
            val y2 = y * cos(tilt) - z1 * sin(tilt)
            val z2 = y * sin(tilt) + z1 * cos(tilt)
            val persp = 4f / (4f + z2)
            Offset(center.x + x1 * scale * persp, center.y + y2 * scale * persp)
        }
        for (a in 0 until 8) for (b in a + 1 until 8) {
            // edges connect vertices differing in exactly one bit
            if (Integer.bitCount(a xor b) == 1) {
                drawLine(color, projected[a], projected[b], strokeWidth = 3f)
            }
        }
    }
}

@Composable
private fun SettingsSection(state: AppState) {
    SwitchRow("Start minimized to tray", state.startMinimized) { state.startMinimized = it }
    SwitchRow("Show laser overlay on presenter mode", state.overlayOnPresenter) { state.overlayOnPresenter = it }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PortField("UDP port", Protocol.DEFAULT_UDP_PORT)
        PortField("TCP port", Protocol.DEFAULT_TCP_PORT)
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
