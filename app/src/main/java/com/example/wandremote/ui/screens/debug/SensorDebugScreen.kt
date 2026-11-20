package com.example.wandremote.ui.screens.debug

import android.hardware.Sensor
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.sensors.ChannelSnapshot
import com.example.wandremote.sensors.SensorMonitor
import com.example.wandremote.sensors.accuracyLabel
import com.example.wandremote.ui.components.SectionCard
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val FRAME_INTERVAL_NS = 33_000_000L

private enum class TraceMode(val label: String, val showRaw: Boolean, val showFiltered: Boolean) {
    RAW("Raw", true, false),
    FILTERED("Filtered", false, true),
    BOTH("Both", true, true),
}

private class Frame(val gyro: ChannelSnapshot, val accel: ChannelSnapshot, val mag: ChannelSnapshot, val rotation: ChannelSnapshot)

@Composable
fun SensorDebugScreen(vm: WandViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val connection by vm.connectionState.collectAsState()
    val monitor = remember {
        SensorMonitor(
            context,
            listOf(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR),
        )
    }
    SideEffect {
        monitor.gyroBias = settings.gyroBias
        monitor.gyroAlpha = settings.toPointerTuning().smoothing
    }
    LifecycleResumeEffect(monitor) {
        monitor.start()
        onPauseOrDispose { monitor.stop() }
    }

    var mode by rememberSaveable { mutableStateOf(TraceMode.BOTH) }

    // Sensors fire at up to a few hundred Hz; redraw at most ~30 fps from a snapshot of the buffers.
    var frame by remember { mutableStateOf<Frame?>(null) }
    LaunchedEffect(monitor) {
        var lastNs = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (now - lastNs < FRAME_INTERVAL_NS) continue
            lastNs = now
            frame = Frame(
                monitor.snapshot(Sensor.TYPE_GYROSCOPE),
                monitor.snapshot(Sensor.TYPE_ACCELEROMETER),
                monitor.snapshot(Sensor.TYPE_MAGNETIC_FIELD),
                monitor.snapshot(Sensor.TYPE_ROTATION_VECTOR),
            )
        }
    }

    var packetRate by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm) {
        var last = vm.packetsSent
        while (true) {
            delay(1000)
            val total = vm.packetsSent
            packetRate = (total - last).toInt()
            last = total
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TraceMode.entries.forEach {
                FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(it.label) })
            }
        }

        val f = frame
        if (f != null) {
            val scheme = MaterialTheme.colorScheme
            val axisColors = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
            SensorCard("Gyroscope", "rad/s", "bias-corrected + smoothed", f.gyro, mode, axisColors, minRange = 0.5f)
            SensorCard("Accelerometer", "m/s²", "low-pass (gravity)", f.accel, mode, axisColors, minRange = 12f)
            SensorCard("Magnetometer", "µT", "low-pass", f.mag, mode, axisColors, minRange = 60f)

            SectionCard("Rotation vector") {
                SensorHeader(f.rotation)
                val q = f.rotation.latestRaw
                Text(
                    "w %.3f   x %.3f   y %.3f   z %.3f".format(q[0], q[1], q[2], q[3]),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionCard("Detected gestures") {
            Text(
                "Gesture detection arrives with Presenter mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Network") {
            Text("Packet rate: $packetRate pkt/s")
            Text("Latency: ${latencyText(connection)}")
        }
    }
}

private fun latencyText(state: ConnectionState): String = when (state) {
    is ConnectionState.ConnectedBluetooth -> "n/a over Bluetooth"
    is ConnectionState.Connected -> when {
        state.latencyMs > 0 -> "${state.latencyMs} ms"
        state.latencyMs == ConnectionState.Connected.LATENCY_PENDING -> "measuring..."
        else -> "no response"
    }
    ConnectionState.Disconnected -> "n/a (not connected)"
}

@Composable
private fun SensorHeader(snapshot: ChannelSnapshot) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val info = snapshot.info
    if (info == null) {
        Text("Not available on this phone", style = MaterialTheme.typography.bodySmall, color = muted)
        return
    }
    Text("${info.name} (${info.vendor})", style = MaterialTheme.typography.bodySmall, color = muted)
    Text("Accuracy: ${accuracyLabel(snapshot.accuracy)}", style = MaterialTheme.typography.bodySmall, color = muted)
}

@Composable
private fun SensorCard(
    title: String,
    unit: String,
    filterDescription: String,
    snapshot: ChannelSnapshot,
    mode: TraceMode,
    axisColors: List<Color>,
    minRange: Float,
) {
    SectionCard("$title ($unit)") {
        SensorHeader(snapshot)
        if (snapshot.info == null) return@SectionCard
        LineGraph(snapshot, mode, axisColors, minRange)
        if (mode.showRaw) ValuesRow("raw", snapshot.latestRaw, axisColors)
        if (mode.showFiltered) ValuesRow("filtered", snapshot.latestFiltered, axisColors)
        Text(
            "Filtered: $filterDescription. Axes x / y / z.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ValuesRow(label: String, v: FloatArray, colors: List<Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        listOf("x", "y", "z").forEachIndexed { i, axis ->
            Text("$axis %.3f".format(v[i]), style = MaterialTheme.typography.labelMedium, color = colors[i])
        }
    }
}

@Composable
private fun LineGraph(snapshot: ChannelSnapshot, mode: TraceMode, colors: List<Color>, minRange: Float) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val window = snapshot.window
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val mid = size.height / 2
        drawLine(grid, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
        if (window.size < 2) return@Canvas

        // Autoscale to the visible data, never tighter than minRange so noise does not fill the graph.
        var peak = minRange
        for (i in 0 until window.size * 3) {
            if (mode.showRaw) peak = maxOf(peak, abs(window.raw[i]))
            if (mode.showFiltered) peak = maxOf(peak, abs(window.filtered[i]))
        }

        fun draw(values: FloatArray, axis: Int, color: Color, width: Float) {
            val path = Path()
            for (i in 0 until window.size) {
                val x = (1f - (snapshot.nowNs - window.timesNs[i]).toFloat() / SensorMonitor.WINDOW_NS) * size.width
                val y = mid - values[i * 3 + axis] / peak * mid
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = width))
        }
        for (axis in 0 until 3) {
            if (mode.showFiltered) draw(window.filtered, axis, colors[axis].copy(alpha = if (mode.showRaw) 0.45f else 1f), 3.dp.toPx())
        }
        for (axis in 0 until 3) {
            if (mode.showRaw) draw(window.raw, axis, colors[axis], 1.5.dp.toPx())
        }
    }
}
