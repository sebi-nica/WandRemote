package com.example.wandremote.ui.screens.threed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wandremote.ui.theme.WandRemoteTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ThreeDScreen() {
    var lockX by remember { mutableStateOf(false) }
    var lockY by remember { mutableStateOf(false) }
    var lockZ by remember { mutableStateOf(false) }
    var zooming by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val lineColor = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val rad = Math.toRadians(angle.toDouble())
                val ax = if (lockX) 0.4 else rad * 0.7 + 0.4
                val ay = if (lockY) 0.0 else rad
                val az = if (lockZ) 0.0 else rad * 0.5
                // Phone-shaped cuboid: 0.6 x 1.2 x 0.1
                val hx = 0.3; val hy = 0.6; val hz = 0.05
                val verts = (0 until 8).map { i ->
                    val x = if (i and 1 == 0) -hx else hx
                    val y = if (i and 2 == 0) -hy else hy
                    val z = if (i and 4 == 0) -hz else hz
                    // rotate X, then Y, then Z
                    val y1 = y * cos(ax) - z * sin(ax); val z1 = y * sin(ax) + z * cos(ax)
                    val x2 = x * cos(ay) + z1 * sin(ay); val z2 = -x * sin(ay) + z1 * cos(ay)
                    val x3 = x2 * cos(az) - y1 * sin(az); val y3 = x2 * sin(az) + y1 * cos(az)
                    val scale = size.minDimension * 0.35f / (1.0 + z2 * 0.3).toFloat()
                    Offset(size.width / 2 + x3.toFloat() * scale, size.height / 2 - y3.toFloat() * scale)
                }
                val edges = listOf(
                    0 to 1, 2 to 3, 4 to 5, 6 to 7,
                    0 to 2, 1 to 3, 4 to 6, 5 to 7,
                    0 to 4, 1 to 5, 2 to 6, 3 to 7,
                )
                edges.forEach { (a, b) ->
                    drawLine(lineColor, verts[a], verts[b], strokeWidth = 4f)
                }
                drawCircle(lineColor, radius = min(size.width, size.height) * 0.01f, center = center, style = Stroke(2f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Lock axis:", style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = lockX, onClick = { lockX = !lockX }, label = { Text("X") })
            FilterChip(selected = lockY, onClick = { lockY = !lockY }, label = { Text("Y") })
            FilterChip(selected = lockZ, onClick = { lockZ = !lockZ }, label = { Text("Z") })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f).height(64.dp)) { Text("Re-center") }
            Button(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            zooming = true
                            waitForUpOrCancellation()
                            zooming = false
                        }
                    },
            ) { Text(if (zooming) "Zooming..." else "Hold to zoom") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreeDScreenPreview() {
    WandRemoteTheme { ThreeDScreen() }
}
