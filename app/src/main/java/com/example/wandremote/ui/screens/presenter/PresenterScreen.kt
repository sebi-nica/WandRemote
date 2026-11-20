package com.example.wandremote.ui.screens.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.sensors.FlickDirection
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.components.TouchDownButton
import com.example.wandremote.ui.screens.calibration.CornerCalibrationCard
import kotlinx.coroutines.delay

@Composable
fun PresenterScreen(vm: WandViewModel) {
    val presenter = vm.presenter
    val settings by vm.settings.collectAsState()
    val connection by vm.connectionState.collectAsState()
    val profile by vm.calibrationProfile.collectAsState()
    val aimOn by presenter.aimOn.collectAsState()
    val laserHeld by presenter.laserHeld.collectAsState()
    val flickEnabled by presenter.flickEnabled.collectAsState()
    val needsRecenter by presenter.needsRecenter.collectAsState()

    val bluetooth = connection is ConnectionState.ConnectedBluetooth
    val wifi = connection is ConnectionState.Connected
    val calibrated = profile != null
    val canAim = wifi && calibrated && !needsRecenter

    // Flick detection and aim streaming run only while this screen is visible.
    LifecycleResumeEffect(vm, bluetooth) {
        if (!bluetooth) presenter.start()
        onPauseOrDispose { presenter.stop() }
    }

    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    var alerted by rememberSaveable { mutableStateOf(false) }
    var alertText by remember { mutableStateOf(settings.presenterAlertMinutes.toString()) }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds++
        }
    }
    val alertAt = settings.presenterAlertMinutes * 60
    LaunchedEffect(seconds, alertAt) {
        if (running && alertAt > 0 && seconds >= alertAt && !alerted) {
            alerted = true
            vm.haptics.alert()
        }
    }
    val overTime = alertAt in 1..seconds

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (bluetooth) {
            SectionCard("Wi-Fi connection needed") {
                Text(
                    "Presenter features (slide flicks, aim cursor, laser) need the Wi-Fi connection to the PC app. " +
                        "Bluetooth only works as a plain mouse. Connect on the Connect tab.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!wifi) {
            Text(
                "Not connected. Connect to the PC app on the Connect tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!calibrated && !bluetooth) {
            Text(
                "Calibrate screen corners first to use the aim cursor and laser.",
                style = MaterialTheme.typography.titleSmall,
            )
            CornerCalibrationCard(vm)
        }

        val slideButtons = listOf(FlickDirection.LEFT to "< Prev", FlickDirection.RIGHT to "Next >")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((direction, label) in slideButtons) {
                val modifier = Modifier.weight(1f).height(120.dp)
                if (direction == FlickDirection.LEFT) {
                    FilledTonalButton(onClick = { presenter.sendSlide(direction) }, enabled = wifi, modifier = modifier) {
                        Text(label, style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Button(onClick = { presenter.sendSlide(direction) }, enabled = wifi, modifier = modifier) {
                        Text(label, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        SwitchRow("Flick to change slides", "Quick wrist flick left = previous, right = next", flickEnabled, wifi) {
            presenter.setFlickEnabled(it)
        }

        if (calibrated && needsRecenter && wifi) {
            SectionCard("Re-center") {
                Text(
                    "The phone's heading resets whenever the screen opens. Aim at the middle of your screen and tap.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TouchDownButton(
                    "Tap: aimed at screen center",
                    onDown = presenter::recenter,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
                OutlinedButton(onClick = presenter::skipRecenter) { Text("Skip") }
            }
        }

        SwitchRow("Aim cursor", "The PC cursor follows where you point", aimOn, canAim) { presenter.setAim(it) }

        TouchDownButton(
            if (laserHeld) "Laser on" else "Hold for laser",
            onDown = { presenter.setLaser(true) },
            onUp = { presenter.setLaser(false) },
            enabled = canAim,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )

        if (calibrated && !needsRecenter && wifi) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pointing drifts over time. Re-center when the dot is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TouchDownButton(
                    "Re-center",
                    onDown = presenter::recenter,
                    tonal = true,
                    modifier = Modifier.padding(start = 12.dp).height(48.dp).weight(0.6f),
                )
            }
        }

        SectionCard("Timer") {
            Text(
                "%02d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.displayMedium,
                color = if (overTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Start") }
                OutlinedButton(onClick = {
                    running = false
                    seconds = 0
                    alerted = false
                }) { Text("Reset") }
            }
            OutlinedTextField(
                value = alertText,
                onValueChange = {
                    alertText = it.filter(Char::isDigit).take(3)
                    val minutes = alertText.toIntOrNull() ?: 0
                    vm.updateSettings { copy(presenterAlertMinutes = minutes) }
                    alerted = minutes * 60 in 1..seconds // changing the limit while already past it must not buzz at once
                },
                label = { Text("Vibrate at (minutes, 0 = off)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (calibrated) CornerCalibrationCard(vm)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
