package com.example.wandremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.ui.theme.ConnectedGreen

@Composable
fun ConnectionStatusChip(state: ConnectionState, modifier: Modifier = Modifier) {
    val connected = state as? ConnectionState.Connected
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(
                when {
                    connected == null -> "Offline"
                    connected.latencyMs < 0 -> "No response"
                    connected.latencyMs == 0 -> "..."
                    else -> "${connected.latencyMs} ms"
                },
            )
        },
        leadingIcon = {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (connected != null && connected.latencyMs >= 0) ConnectedGreen else MaterialTheme.colorScheme.error,
                        CircleShape,
                    ),
            )
        },
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}
