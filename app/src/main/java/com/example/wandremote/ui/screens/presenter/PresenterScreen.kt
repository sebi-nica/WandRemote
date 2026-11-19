package com.example.wandremote.ui.screens.presenter

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.theme.WandRemoteTheme
import kotlinx.coroutines.delay

@Composable
fun PresenterScreen() {
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(false) }
    var alertMinutes by rememberSaveable { mutableStateOf("10") }
    var laser by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds++
        }
    }
    val alertAt = (alertMinutes.toIntOrNull() ?: 0) * 60
    val overTime = alertAt in 1..seconds

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f).height(120.dp)) {
                Text("< Prev", style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = {}, modifier = Modifier.weight(1f).height(120.dp)) {
                Text("Next >", style = MaterialTheme.typography.titleLarge)
            }
        }

        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        laser = true
                        waitForUpOrCancellation()
                        laser = false
                    }
                },
        ) { Text(if (laser) "Laser on" else "Hold for laser") }

        SectionCard("Timer") {
            Text(
                "%02d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.displayMedium,
                color = if (overTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Start") }
                OutlinedButton(onClick = { running = false; seconds = 0 }) { Text("Reset") }
            }
            OutlinedTextField(
                value = alertMinutes,
                onValueChange = { alertMinutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Alert at (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PresenterScreenPreview() {
    WandRemoteTheme { PresenterScreen() }
}
