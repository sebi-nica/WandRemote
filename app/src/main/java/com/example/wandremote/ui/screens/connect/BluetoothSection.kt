package com.example.wandremote.ui.screens.connect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wandremote.WandViewModel
import com.example.wandremote.bluetooth.BluetoothHidState
import com.example.wandremote.ui.components.SectionCard

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        emptyArray()
    }

/** Bluetooth HID mouse section of the Connect screen. Navigation to Pointer happens when the host connects. */
@Composable
fun BluetoothSection(vm: WandViewModel) {
    val bt = vm.bluetooth
    val state by bt.state.collectAsState()

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        bt.start() // re-evaluates: Off -> Registering if the user accepted
    }
    val discoverableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) bt.start() else bt.markPermissionDenied()
    }

    fun startBluetooth() {
        val needed = requiredPermissions()
        if (needed.isEmpty() || bt.hasPermission()) bt.start() else permissionLauncher.launch(needed)
    }

    fun makeDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        discoverableLauncher.launch(intent)
    }

    SectionCard("Bluetooth mouse (no PC app, mouse + arrow keys only)") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, Modifier.size(18.dp))
            if (state is BluetoothHidState.Registering || state is BluetoothHidState.Connecting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(statusText(state), style = MaterialTheme.typography.bodyMedium)
        }

        when (state) {
            BluetoothHidState.Idle, BluetoothHidState.PermissionDenied, is BluetoothHidState.Error ->
                Button(onClick = { startBluetooth() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state == BluetoothHidState.Idle) "Start Bluetooth mouse" else "Try again")
                }
            BluetoothHidState.Off ->
                Button(
                    onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Turn on Bluetooth") }
            BluetoothHidState.Unavailable, BluetoothHidState.Registering -> Unit
            else -> {
                Text(
                    "Make the phone discoverable, then on the PC open Bluetooth settings > Add device and pick this phone. " +
                        "Already paired? Tap your PC below.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { makeDiscoverable() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Make discoverable")
                }
                val devices = remember(state) { bt.bondedDevices() }
                if (devices.isEmpty()) {
                    Text("No paired devices yet.", style = MaterialTheme.typography.bodySmall)
                }
                devices.forEach { d ->
                    ListItem(
                        modifier = Modifier.clickable { vm.connectBluetooth(d.address) },
                        leadingContent = {
                            Icon(if (d.isComputer) Icons.Default.Computer else Icons.Default.Bluetooth, contentDescription = null)
                        },
                        headlineContent = { Text(d.name) },
                        trailingContent = { Text("Connect", style = MaterialTheme.typography.labelLarge) },
                    )
                }
                if (state is BluetoothHidState.Connected) {
                    OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

private fun statusText(state: BluetoothHidState): String = when (state) {
    BluetoothHidState.Idle -> "Turns this phone into a Bluetooth mouse."
    BluetoothHidState.Unavailable -> "This phone doesn't support Bluetooth mouse mode"
    BluetoothHidState.PermissionDenied -> "Bluetooth permission was denied. Allow \"Nearby devices\" to use this mode."
    BluetoothHidState.Off -> "Bluetooth is off."
    BluetoothHidState.Registering -> "Starting..."
    BluetoothHidState.Ready -> "Ready, not connected."
    is BluetoothHidState.Connecting -> "Connecting to ${state.deviceName}..."
    is BluetoothHidState.Connected -> "Connected to ${state.deviceName}"
    is BluetoothHidState.Error -> state.message
}
