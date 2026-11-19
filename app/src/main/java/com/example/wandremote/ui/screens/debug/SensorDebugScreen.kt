package com.example.wandremote.ui.screens.debug

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.theme.WandRemoteTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLES = 120

@Composable
fun SensorDebugScreen(connection: ConnectionState) {
    val gyro = remember { mutableStateListOf<Float>() }
    val accel = remember { mutableStateListOf<Float>() }
    val mag = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        var t = 0f
        while (true) {
            fun push(list: MutableList<Float>, v: Float) {
                list.add(v)
                if (list.size > SAMPLES) list.removeAt(0)
            }
            push(gyro, sin(t) + Random.nextFloat() * 0.2f - 0.1f)
            push(accel, sin(t * 0.5f + 1f) * 0.8f + Random.nextFloat() * 0.3f - 0.15f)
            push(mag, sin(t * 0.2f + 2f) * 0.6f + Random.nextFloat() * 0.1f - 0.05f)
            t += 0.15f
            delay(50)
        }
    }

    val latency = (connection as? ConnectionState.Connected)?.latencyMs?.takeIf { it > 0 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Gyroscope (rad/s)") { LineGraph(gyro, MaterialTheme.colorScheme.primary) }
        SectionCard("Accelerometer (m/s2)") { LineGraph(accel, MaterialTheme.colorScheme.secondary) }
        SectionCard("Magnetometer (uT)") { LineGraph(mag, MaterialTheme.colorScheme.tertiary) }

        SectionCard("Detected gestures") {
            listOf("BACK_TAP" to 0.92f, "FLICK_RIGHT" to 0.81f, "SHAKE" to 0.37f).forEach { (name, conf) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    Text("%.0f%%".format(conf * 100))
                }
            }
        }

        SectionCard("Network") {
            Text("Packet rate: 100 pkt/s")
            Text("Latency: ${latency?.let { "$it ms" } ?: "n/a"}")
        }
    }
}

@Composable
private fun LineGraph(values: List<Float>, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val path = Path()
        val stepX = size.width / (SAMPLES - 1)
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height / 2 - v.coerceIn(-1.5f, 1.5f) / 1.5f * (size.height / 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f))
    }
}

@Preview(showBackground = true)
@Composable
private fun SensorDebugScreenPreview() {
    WandRemoteTheme { SensorDebugScreen(ConnectionState.Connected("192.168.1.23", 47801, 12)) }
}
