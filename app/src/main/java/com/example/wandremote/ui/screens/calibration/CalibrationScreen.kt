package com.example.wandremote.ui.screens.calibration

import android.hardware.Sensor
import android.hardware.SensorManager
import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.sensors.GyroBias
import com.example.wandremote.sensors.GyroBiasCalibrator
import com.example.wandremote.sensors.SensorMonitor
import com.example.wandremote.sensors.accuracyLabel
import com.example.wandremote.ui.components.SectionCard
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private sealed interface GyroUi {
    data object Idle : GyroUi
    data class Collecting(val percent: Int) : GyroUi
    data class Finished(val success: Boolean, val message: String) : GyroUi
}

private sealed interface MagUi {
    data object Idle : MagUi
    data object Running : MagUi
    data class Finished(val accuracy: Int) : MagUi
}

@Composable
fun CalibrationScreen(vm: WandViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    val gyroMonitor = remember { SensorMonitor(context, listOf(Sensor.TYPE_GYROSCOPE)) }
    val magMonitor = remember { SensorMonitor(context, listOf(Sensor.TYPE_MAGNETIC_FIELD)) }
    var gyroUi by remember { mutableStateOf<GyroUi>(GyroUi.Idle) }
    var magUi by remember { mutableStateOf<MagUi>(MagUi.Idle) }
    var magAccuracy by remember { mutableIntStateOf(SensorMonitor.NO_DATA) }

    // Never keep sensors running (or a half-finished calibration) while the screen is not visible.
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            gyroMonitor.stop()
            magMonitor.stop()
            if (gyroUi is GyroUi.Collecting) gyroUi = GyroUi.Idle
            if (magUi is MagUi.Running) magUi = MagUi.Idle
        }
    }

    fun startGyro() {
        val calibrator = GyroBiasCalibrator()
        gyroUi = GyroUi.Collecting(0)
        gyroMonitor.onSample = { _, v, timestampNs ->
            calibrator.add(timestampNs, v[0], v[1], v[2])
            if (!calibrator.isDone) {
                gyroUi = GyroUi.Collecting((calibrator.progress * 100).toInt())
            } else {
                gyroMonitor.stop()
                gyroMonitor.onSample = null
                gyroUi = when (val result = calibrator.result()) {
                    is GyroBiasCalibrator.Result.Success -> {
                        vm.updateSettings { copy(gyroBias = result.bias, gyroBiasTimeMs = System.currentTimeMillis()) }
                        GyroUi.Finished(true, "Bias saved.")
                    }
                    GyroBiasCalibrator.Result.Moved -> GyroUi.Finished(false, "Phone moved, try again.")
                    GyroBiasCalibrator.Result.TooFewSamples -> GyroUi.Finished(false, "Not enough sensor data, try again.")
                }
            }
        }
        gyroMonitor.start()
    }

    fun stopMag() {
        magMonitor.stop()
        magMonitor.onAccuracy = null
        magUi = MagUi.Finished(magAccuracy)
    }

    fun startMag() {
        if (magMonitor.snapshot(Sensor.TYPE_MAGNETIC_FIELD).info == null) {
            magUi = MagUi.Finished(SensorMonitor.NO_DATA)
            return
        }
        magAccuracy = SensorMonitor.NO_DATA
        magUi = MagUi.Running
        magMonitor.onAccuracy = { _, accuracy ->
            magAccuracy = accuracy
            if (accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) stopMag()
        }
        magMonitor.start()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Gyroscope bias") {
            Text(
                "Place the phone on a flat table and don't touch it. Takes about 3 seconds.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val state = gyroUi
            if (state is GyroUi.Collecting) {
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            if (state is GyroUi.Finished) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(biasSummary(settings.gyroBias, settings.gyroBiasTimeMs, context), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::startGyro, enabled = state !is GyroUi.Collecting) { Text("Start") }
                OutlinedButton(
                    onClick = {
                        gyroUi = GyroUi.Idle
                        vm.updateSettings { copy(gyroBias = GyroBias.ZERO, gyroBiasTimeMs = 0L) }
                    },
                    enabled = state !is GyroUi.Collecting && settings.gyroBiasTimeMs != 0L,
                ) { Text("Reset") }
            }
        }

        SectionCard("Magnetometer") {
            Text(
                "Android calibrates the compass by itself. Move the phone in a figure-8 until accuracy reaches High.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val state = magUi
            if (state is MagUi.Running) {
                Figure8Hint()
                Text("Accuracy: ${accuracyLabel(magAccuracy)}", style = MaterialTheme.typography.titleMedium)
            }
            if (state is MagUi.Finished) {
                val good = state.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                Text(
                    if (good) "Accuracy: High. The magnetometer is calibrated."
                    else "Accuracy: ${accuracyLabel(state.accuracy)}. Try again, away from metal and magnets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (state is MagUi.Running) {
                OutlinedButton(onClick = ::stopMag) { Text("Stop") }
            } else {
                Button(onClick = ::startMag) { Text("Start") }
            }
        }
    }
}

private fun biasSummary(bias: GyroBias, timeMs: Long, context: android.content.Context): String {
    if (timeMs == 0L) return "Not calibrated yet."
    val whenText = DateFormat.getMediumDateFormat(context).format(Date(timeMs)) + " " +
        DateFormat.getTimeFormat(context).format(Date(timeMs))
    return "Bias (rad/s): x %.4f, y %.4f, z %.4f\nLast calibrated: %s".format(bias.x, bias.y, bias.z, whenText)
}

/** A figure-8 outline with a dot tracing it. */
@Composable
private fun Figure8Hint() {
    val phase by rememberInfiniteTransition(label = "figure8").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val outline = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val rx = size.width * 0.35f
        val ry = size.height * 0.4f
        val center = Offset(size.width / 2, size.height / 2)
        fun point(t: Float) = Offset(center.x + rx * sin(t), center.y + ry * sin(t) * cos(t) * 2f)
        val path = Path()
        val steps = 90
        for (i in 0..steps) {
            val p = point(2 * PI.toFloat() * i / steps)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, outline, style = Stroke(width = 3.dp.toPx()))
        drawCircle(dot, 8.dp.toPx(), point(phase))
    }
}
