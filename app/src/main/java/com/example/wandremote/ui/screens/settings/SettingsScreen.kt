package com.example.wandremote.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wandremote.shared.Gesture
import com.example.wandremote.ui.components.LabeledSlider
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.theme.WandRemoteTheme

private val actions = listOf("Left click", "Double click", "Right click", "Previous slide", "Next slide", "Undo", "None")

private fun defaultAction(g: Gesture) = when (g) {
    Gesture.BACK_TAP -> "Left click"
    Gesture.DOUBLE_BACK_TAP -> "Double click"
    Gesture.FLICK_LEFT -> "Previous slide"
    Gesture.FLICK_RIGHT -> "Next slide"
    Gesture.SHAKE -> "Undo"
}

@Composable
fun SettingsScreen() {
    var sensitivity by rememberSaveable { mutableFloatStateOf(0.6f) }
    var acceleration by rememberSaveable { mutableFloatStateOf(0.4f) }
    var smoothing by rememberSaveable { mutableFloatStateOf(0.5f) }
    var deadZone by rememberSaveable { mutableFloatStateOf(0.1f) }
    var vibration by rememberSaveable { mutableFloatStateOf(0.7f) }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var trackpad by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Pointer") {
            LabeledSlider("Sensitivity", sensitivity, { sensitivity = it })
            LabeledSlider("Acceleration", acceleration, { acceleration = it })
            LabeledSlider("Smoothing", smoothing, { smoothing = it })
            LabeledSlider("Dead zone", deadZone, { deadZone = it })
            Text("Live test area", style = MaterialTheme.typography.labelLarge)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("Move the phone to test (placeholder)", style = MaterialTheme.typography.bodySmall) }
        }

        SectionCard("Gesture mapping") {
            Gesture.entries.forEach { gesture ->
                var action by rememberSaveable(gesture.name) { mutableStateOf(defaultAction(gesture)) }
                var open by rememberSaveable(gesture.name + "menu") { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(gesture.name.replace('_', ' ').lowercase(), Modifier.weight(1f))
                    Box {
                        OutlinedButton(onClick = { open = true }) { Text(action) }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            actions.forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = { action = it; open = false })
                            }
                        }
                    }
                }
            }
        }

        SectionCard("General") {
            SwitchRow("Left-handed mode", leftHanded) { leftHanded = it }
            SwitchRow("Trackpad fallback", trackpad) { trackpad = it }
            LabeledSlider("Vibration intensity", vibration, { vibration = it })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WandRemoteTheme { SettingsScreen() }
}
