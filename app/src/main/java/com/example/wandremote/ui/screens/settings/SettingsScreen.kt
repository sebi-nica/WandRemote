package com.example.wandremote.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wandremote.WandViewModel
import com.example.wandremote.ui.components.LabeledSlider
import com.example.wandremote.ui.components.SectionCard
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: WandViewModel) {
    val settings by vm.settings.collectAsState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pointer tuning is in the Config tab.", style = MaterialTheme.typography.bodyMedium, color = muted)

        SectionCard("General") {
            SwitchRow("Flip button layout (left-handed)", settings.leftHanded) { v -> vm.updateSettings { copy(leftHanded = v) } }
            LabeledSlider(
                "Vibration strength", settings.vibrationStrength,
                { v -> vm.updateSettings { copy(vibrationStrength = v) } },
                valueText = { if (it <= 0f) "Off" else "${(it * 100).roundToInt()}%" },
                onValueChangeFinished = { vm.haptics.pulse(30) },
            )
        }

        SectionCard("Gestures") {
            Text(
                "Gesture actions are configured in the PC app's Gestures tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
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
