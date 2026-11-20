package com.example.wandremote.ui.screens.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.sensors.SensorHub
import com.example.wandremote.settings.AppSettings
import com.example.wandremote.ui.components.LabeledSlider
import com.example.wandremote.ui.components.SectionCard
import kotlin.math.roundToInt

/** The dot crosses the test box as the cursor would cross a screen this wide. */
private const val REFERENCE_SCREEN_WIDTH_PX = 1920f

private const val RAD_TO_DEG = 57.29578f

/** Pointer tuning: live sliders plus a test area driven by the gyro (nothing is sent to the PC). */
@Composable
fun ConfigScreen(vm: WandViewModel) {
    val settings by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TestArea(vm)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Pointer") {
                LabeledSlider(
                    "Sensitivity", settings.sensitivity,
                    { v -> vm.updateSettings { copy(sensitivity = v) } },
                    valueRange = AppSettings.SENSITIVITY_RANGE,
                    valueText = { "%.2f×".format(it) },
                )
                LabeledSlider(
                    "Acceleration", settings.acceleration,
                    { v -> vm.updateSettings { copy(acceleration = v) } },
                    valueRange = AppSettings.ACCELERATION_RANGE,
                )
                LabeledSlider(
                    "Smoothing", settings.smoothing,
                    { v -> vm.updateSettings { copy(smoothing = v) } },
                    valueRange = AppSettings.SMOOTHING_RANGE,
                    valueText = { "${(it * 100).roundToInt()}%" },
                )
                LabeledSlider(
                    "Dead zone", settings.deadZone,
                    { v -> vm.updateSettings { copy(deadZone = v) } },
                    valueRange = AppSettings.DEAD_ZONE_RANGE,
                    valueText = { "%.1f°/s".format(it * RAD_TO_DEG) },
                )
            }

            SectionCard("Scroll knob") {
                LabeledSlider(
                    "Scroll speed", settings.scrollDegreesPerNotch,
                    { v -> vm.updateSettings { copy(scrollDegreesPerNotch = v) } },
                    valueRange = AppSettings.DEGREES_PER_NOTCH_RANGE,
                    valueText = { "%.0f° per notch".format(it) },
                )
                LabeledSlider(
                    "Knob size", settings.knobSizeFraction,
                    { v -> vm.updateSettings { copy(knobSizeFraction = v) } },
                    valueRange = AppSettings.KNOB_FRACTION_RANGE,
                    valueText = { "${(it * 100).roundToInt()}%" },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Invert scroll direction", Modifier.weight(1f))
                    Switch(
                        checked = settings.invertScroll,
                        onCheckedChange = { v -> vm.updateSettings { copy(invertScroll = v) } },
                    )
                }
            }

            OutlinedButton(
                onClick = { vm.updateSettings { withDefaultTuning() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset to defaults") }
        }
    }
}

/** Hold the box to move the dot with the gyro, using the current tuning. Local preview only. */
@Composable
private fun TestArea(vm: WandViewModel) {
    val scheme = MaterialTheme.colorScheme
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dot by remember { mutableStateOf(Offset.Unspecified) }
    var holding by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SensorHub.PointerSession?>(null) }
    val currentSize by rememberUpdatedState(boxSize)
    val currentSession by rememberUpdatedState(session)

    LifecycleResumeEffect(vm) {
        val s = vm.sensors.openPointer { dx, dy ->
            val size = currentSize
            val scale = size.width / REFERENCE_SCREEN_WIDTH_PX
            val from = if (dot.isSpecified) dot else Offset(size.width / 2f, size.height / 2f)
            dot = Offset(
                (from.x + dx * scale).coerceIn(0f, size.width.toFloat()),
                (from.y + dy * scale).coerceIn(0f, size.height.toFloat()),
            )
        }
        session = s
        onPauseOrDispose {
            s.close()
            session = null
            holding = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (holding) scheme.primaryContainer else scheme.surfaceVariant)
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        currentSession?.movementEnabled = true
                        try {
                            awaitRelease()
                        } finally {
                            holding = false
                            currentSession?.movementEnabled = false
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!holding) {
                Text("Hold to move the dot", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            val dotColor = scheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val center = if (dot.isSpecified) dot else Offset(size.width / 2f, size.height / 2f)
                drawCircle(dotColor, 10.dp.toPx(), center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Test area (nothing is sent to the PC)", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            TextButton(onClick = { dot = Offset.Unspecified }) { Text("Center") }
        }
    }
}
