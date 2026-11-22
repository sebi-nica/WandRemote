package com.example.wandremote.ui.screens.connect

import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wandremote.WandViewModel
import com.example.wandremote.shared.Protocol
import com.example.wandremote.ui.components.SectionCard

@Composable
fun ConnectScreen(vm: WandViewModel, onConnect: (name: String, host: String, port: Int) -> Unit) {
    val context = LocalContext.current
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(Protocol.DEFAULT_UDP_PORT.toString()) }
    val discovered by vm.connection.discovered.collectAsState()
    val scanning by vm.connection.scanning.collectAsState()
    val recent by vm.recent.collectAsState()

    LaunchedEffect(Unit) { vm.connection.scan() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect to a PC", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Discovered PCs") {
            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Scanning local network...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (discovered.isEmpty()) {
                Text("No PCs found. Make sure the companion app is running.", style = MaterialTheme.typography.bodySmall)
            }
            discovered.forEach { pc ->
                ListItem(
                    modifier = Modifier.clickable { onConnect(pc.name, pc.ip, pc.udpPort) },
                    leadingContent = { Icon(Icons.Default.Computer, contentDescription = null) },
                    headlineContent = { Text(pc.name) },
                    supportingContent = { Text("${pc.ip}:${pc.udpPort}") },
                )
            }
            OutlinedButton(
                onClick = { vm.connection.scan() },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Rescan") }
        }

        BluetoothLinkSection(vm)

        BluetoothSection(vm)

        SectionCard("Manual connection") {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val ip = host.trim()
                    if (ip.isEmpty()) {
                        Toast.makeText(context, "Enter the PC's IP address", Toast.LENGTH_SHORT).show()
                    } else {
                        onConnect(ip, ip, port.toIntOrNull() ?: Protocol.DEFAULT_UDP_PORT)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect") }
            OutlinedButton(
                onClick = {
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    GmsBarcodeScanning.getClient(context, options).startScan()
                        .addOnSuccessListener { vm.handlePairingLink(it.rawValue.orEmpty()) }
                        .addOnFailureListener {
                            Toast.makeText(context, it.message ?: "QR scan failed", Toast.LENGTH_LONG).show()
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, Modifier.padding(end = 8.dp))
                Text("Scan QR code")
            }
        }

        SectionCard("Recent PCs") {
            if (recent.isEmpty()) Text("No recent PCs yet.", style = MaterialTheme.typography.bodySmall)
            recent.forEach { pc ->
                ListItem(
                    modifier = Modifier.clickable { onConnect(pc.name, pc.host, pc.port) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    headlineContent = { Text(pc.name) },
                    supportingContent = { Text("${pc.host}:${pc.port}") },
                )
            }
        }
    }
}
