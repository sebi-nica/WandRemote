package com.example.wandremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
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
    val bluetooth = state as? ConnectionState.ConnectedBluetooth
    val link = state as? ConnectionState.ConnectedLink
    fun latencyText(ms: Int) = when {
        ms < 0 -> "No response"
        ms == 0 -> "..."
        else -> "$ms ms"
    }
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(
                when {
                    bluetooth != null -> bluetooth.deviceName
                    link != null -> "${link.deviceName} via PC app · ${latencyText(link.latencyMs)}"
                    connected == null -> "Offline"
                    else -> latencyText(connected.latencyMs)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            if (bluetooth != null || link != null) {
                val ok = link == null || link.latencyMs >= 0
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    Modifier.size(16.dp),
                    tint = if (ok) ConnectedGreen else MaterialTheme.colorScheme.error,
                )
            } else {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (connected != null && connected.latencyMs >= 0) ConnectedGreen else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                )
            }
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
    valueText: (Float) -> String = { "%.2f".format(it) },
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

/**
 * A button that reacts the moment the finger touches down (and again on release), unlike a click which fires on
 * release. Used where timing matters: "hold for laser" and "aim, then tap" captures.
 */
@Composable
fun TouchDownButton(
    text: String,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    onUp: () -> Unit = {},
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    var pressed by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.12f)
        pressed -> scheme.tertiary
        tonal -> scheme.secondaryContainer
        else -> scheme.primary
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        pressed -> scheme.onTertiary
        tonal -> scheme.onSecondaryContainer
        else -> scheme.onPrimary
    }
    Surface(
        modifier = if (enabled) {
            modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    down()
                    waitForUpOrCancellation()
                    pressed = false
                    up()
                }
            }
        } else {
            modifier
        },
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
