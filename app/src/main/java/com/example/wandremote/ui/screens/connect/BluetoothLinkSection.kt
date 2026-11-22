package com.example.wandremote.ui.screens.connect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.BluetoothLinkState
import com.example.wandremote.ui.components.SectionCard

/**
 * "Bluetooth to PC app" section of the Connect screen: an RFCOMM link to the WandRemote PC app that carries every
 * companion feature without Wi-Fi. Navigation to Pointer happens when the link connects.
 */
@Composable
fun BluetoothLinkSection(vm: WandViewModel) {
    val link = vm.bluetoothLink
    val state by link.state.collectAsState()

    var permitted by remember { mutableStateOf(link.hasPermission()) }
    var enabled by remember { mutableStateOf(link.isEnabled()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        enabled = link.isEnabled()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        enabled = link.isEnabled()
    }

    val connecting = state as? BluetoothLinkState.Connecting

    SectionCard("Bluetooth to PC app (all features)") {
        Text(
            "Needs WandRemote running on the PC. Pair the phone with the PC in Bluetooth settings first, " +
                "and start the WandRemote PC app.",
            style = MaterialTheme.typography.bodySmall,
        )

        when {
            !permitted -> Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow Bluetooth access") }

            !enabled -> Button(
                onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Turn on Bluetooth") }

            else -> {
                if (connecting != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Connecting...", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.disconnect() }) { Text("Cancel") }
                    }
                }
                (state as? BluetoothLinkState.Error)?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                // Same paired-device list as the HID mouse card (computers first).
                val devices = remember(state) { vm.bluetooth.bondedDevices() }
                if (devices.isEmpty()) {
                    Text("No paired devices yet.", style = MaterialTheme.typography.bodySmall)
                }
                devices.forEach { d ->
                    ListItem(
                        leadingContent = {
                            Icon(if (d.isComputer) Icons.Default.Computer else Icons.Default.Bluetooth, contentDescription = null)
                        },
                        headlineContent = { Text(d.name) },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { vm.connectLink(d.address) },
                                enabled = connecting == null,
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("Connect") }
                        },
                    )
                }
            }
        }
    }
}
