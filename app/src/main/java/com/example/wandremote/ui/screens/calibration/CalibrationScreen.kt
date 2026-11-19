package com.example.wandremote.ui.screens.calibration

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.theme.WandRemoteTheme

private val profiles = listOf("DESKTOP-SEBI / 1 monitor", "DESKTOP-SEBI / dual monitor", "Laptop-Work / projector")

@Composable
fun CalibrationScreen() {
    val context = LocalContext.current
    var profile by rememberSaveable { mutableStateOf(profiles.first()) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val soon = { Toast.makeText(context, "Calibration coming soon", Toast.LENGTH_SHORT).show() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Profile") {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(profile) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                profiles.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = { profile = it; menuOpen = false })
                }
            }
        }

        SectionCard("Screen corners") {
            Text("Point at each screen corner and press the button.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Top-left", "Top-right", "Bottom-right", "Bottom-left").forEachIndexed { i, label ->
                    Text("${i + 1}. $label", style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(onClick = soon) { Text("Start") }
        }

        SectionCard("Gyroscope bias") {
            Text("Place the phone flat on a table and keep it still.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = soon) { Text("Start") }
        }

        SectionCard("Magnetometer") {
            Text("Move the phone in a figure-8 motion.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = soon) { Text("Start") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalibrationScreenPreview() {
    WandRemoteTheme { CalibrationScreen() }
}
