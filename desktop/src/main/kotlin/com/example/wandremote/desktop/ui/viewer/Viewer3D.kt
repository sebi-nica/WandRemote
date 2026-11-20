package com.example.wandremote.desktop.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.example.wandremote.desktop.AppState
import com.example.wandremote.desktop.ui.theme.WandRemoteTheme
import kotlin.math.exp

const val MIN_ZOOM = 0.3f
const val MAX_ZOOM = 4f

/** The separate "WandRemote 3D" window. Closing it only hides it; it reopens when 3D mode starts again. */
@Composable
fun ViewerWindow(state: AppState) {
    if (!state.viewerOpen) return
    Window(
        onCloseRequest = { state.viewerOpen = false },
        title = "WandRemote 3D",
        state = rememberWindowState(size = DpSize(640.dp, 680.dp)),
    ) {
        WandRemoteTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Viewer3D(state, Modifier.weight(1f).fillMaxWidth())
                    ViewerControls(state)
                }
            }
        }
    }
}

/** Zoom read-out and reset button, shared by the window and the status window. */
@Composable
fun ViewerControls(state: AppState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Zoom ${"%.2f".format(state.zoom)}x", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { state.zoom = 1f }) { Text("Reset zoom") }
        Text(
            if (state.phoneName == null) "No phone connected" else "Tilt + hold on the phone to zoom",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Renders the phone model at the live orientation and zoom, smoothed at display rate. */
@Composable
fun Viewer3D(state: AppState, modifier: Modifier = Modifier) {
    val shownQ = remember { floatArrayOf(1f, 0f, 0f, 0f) }
    val shownZoom = remember { floatArrayOf(1f) }
    var frame by remember { mutableLongStateOf(0L) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
                lastNanos = now
                val k = 1f - exp(-dt * 30f)
                Math3D.nlerp(shownQ, state.orientation, k).copyInto(shownQ)
                shownZoom[0] += (state.zoom - shownZoom[0]) * k
                frame = now
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.outlineVariant
    val screenColor = scheme.primary
    val backColor = scheme.surfaceVariant
    val topColor = scheme.tertiary
    val sideColor = scheme.secondary
    val background = scheme.surfaceVariant.copy(alpha = 0.35f)
    val inset = scheme.onSurface

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame // invalidate the draw scope every frame
        drawRect(background)
        val scene = SceneBuilder.build(shownQ, shownZoom[0], size.width, size.height)
        for (l in scene.lines) {
            val color = when (l.kind) {
                LineKind.GRID -> gridColor
                LineKind.AXIS_X -> Color(0xFFE5484D)
                LineKind.AXIS_Y -> Color(0xFF3FB950)
                LineKind.AXIS_Z -> Color(0xFF4C8DFF)
            }
            val w = if (l.kind == LineKind.GRID) 1f else 3f
            drawLine(color, Offset(l.x1, l.y1), Offset(l.x2, l.y2), strokeWidth = w)
        }
        for (f in scene.faces) {
            val base = when (f.kind) {
                FaceKind.SCREEN -> screenColor
                FaceKind.BACK -> backColor
                FaceKind.TOP -> topColor
                FaceKind.SIDE -> sideColor
            }
            val shaded = Color(base.red * f.shade, base.green * f.shade, base.blue * f.shade, 1f)
            val path = Path().apply {
                moveTo(f.xs[0], f.ys[0])
                for (i in 1..3) lineTo(f.xs[i], f.ys[i])
                close()
            }
            drawPath(path, shaded)
            drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(1.5f))
            if (f.kind == FaceKind.SCREEN) drawScreenDetails(f, inset)
        }
    }
}

/** Bilinear point on a projected quad whose corners are (-,-), (+,-), (+,+), (-,+); u, v in 0..1. */
private fun ProjectedFace.at(u: Float, v: Float): Offset {
    val bx = xs[0] + (xs[1] - xs[0]) * u
    val by = ys[0] + (ys[1] - ys[0]) * u
    val tx = xs[3] + (xs[2] - xs[3]) * u
    val ty = ys[3] + (ys[2] - ys[3]) * u
    return Offset(bx + (tx - bx) * v, by + (ty - by) * v)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScreenDetails(f: ProjectedFace, ink: Color) {
    val inner = Path().apply {
        val a = f.at(0.08f, 0.04f)
        moveTo(a.x, a.y)
        for ((u, v) in listOf(0.92f to 0.04f, 0.92f to 0.9f, 0.08f to 0.9f)) f.at(u, v).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(inner, Color.Black.copy(alpha = 0.45f))
    // speaker slot at the top edge so "up" is obvious
    drawLine(ink.copy(alpha = 0.9f), f.at(0.36f, 0.95f), f.at(0.64f, 0.95f), strokeWidth = 3f)
}
