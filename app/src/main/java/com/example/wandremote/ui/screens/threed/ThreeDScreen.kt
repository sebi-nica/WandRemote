package com.example.wandremote.ui.screens.threed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.sensors.AxisLock
import com.example.wandremote.sensors.OrientationSensor
import com.example.wandremote.sensors.Quat
import com.example.wandremote.shared.RemoteMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D orientation mode: streams the phone's orientation (relative to a re-centerable reference, with optional
 * per-axis locks) to the PC and shows a live preview. "Hold to zoom" freezes the orientation and turns tilt into zoom.
 */
@Composable
fun ThreeDScreen(vm: WandViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = vm.haptics
    val connection by vm.connectionState.collectAsState()
    val settings by vm.settings.collectAsState()
    val wifi = connection is ConnectionState.Connected
    val bluetoothOnly = connection is ConnectionState.ConnectedBluetooth

    val session = remember(vm) {
        ThreeDSession(
            send = { vm.output.send(it) },
            streaming = { vm.connectionState.value is ConnectionState.Connected },
        )
    }
    val sensor = remember(session) { OrientationSensor(context) { q, ts -> session.onSample(q, ts) } }

    var lockPitch by remember { mutableStateOf(false) }
    var lockRoll by remember { mutableStateOf(false) }
    var lockYaw by remember { mutableStateOf(false) }
    LaunchedEffect(lockPitch, lockRoll, lockYaw) {
        session.setLock(AxisLock(pitch = lockPitch, roll = lockRoll, yaw = lockYaw))
    }

    // Tell the PC we are in 3D mode (again when the Wi-Fi connection comes up while this screen is open).
    // Goes through the VM so the mode is remembered and re-sent after reconnects.
    LaunchedEffect(wifi) { if (wifi) vm.setMode(RemoteMode.THREE_D) }

    // The sensor runs only while this screen is resumed; each time it opens, the current pose becomes the reference.
    LifecycleResumeEffect(session) {
        session.onResume()
        vm.setMode(RemoteMode.THREE_D)
        sensor.start()
        onPauseOrDispose {
            sensor.stop()
            session.onPause()
        }
    }

    // Drives the preview at display rate without recomposing per sensor event.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) androidx.compose.runtime.withFrameNanos { frame = it }
    }

    val scheme = MaterialTheme.colorScheme
    val colors = PhoneColors(
        screen = scheme.primary,
        back = scheme.outline,
        top = scheme.tertiary,
        side = scheme.secondary,
        ink = scheme.onSurface,
    )

    val recenterButton: @Composable (Modifier) -> Unit = { m ->
        FilledTonalButton(
            onClick = {
                session.recenter()
                haptics.pulse(30)
            },
            modifier = m.height(HOLD_HEIGHT),
            shape = RoundedCornerShape(20.dp),
        ) { Text("Re-center") }
    }
    val zoomButton: @Composable (Modifier) -> Unit = { m ->
        val active = session.zooming
        val enabled = wifi
        Box(
            m
                .height(HOLD_HEIGHT)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    when {
                        !enabled -> scheme.surfaceVariant
                        active -> scheme.tertiary
                        else -> scheme.primary
                    },
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown()
                        session.beginZoom()
                        haptics.pulse(25)
                        waitForUpOrCancellation()
                        session.endZoom()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (active) "Tilt up = zoom in\nTilt down = zoom out" else "Hold to zoom",
                color = if (enabled) (if (active) scheme.onTertiary else scheme.onPrimary) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (bluetoothOnly) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "3D mode needs the Wi-Fi connection to the PC app. You are connected as a Bluetooth mouse, " +
                        "which cannot carry orientation, so streaming is off. Connect to the PC app on the Connect tab.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!wifi) {
            Text(
                "Not connected to the PC app. The preview works, but nothing is sent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceVariant),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val tick = frame // redraw every frame
                drawPhone(session.display, colors)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Lock:", style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = lockPitch, onClick = { lockPitch = !lockPitch }, label = { Text("X Pitch") })
            FilterChip(selected = lockRoll, onClick = { lockRoll = !lockRoll }, label = { Text("Y Roll") })
            FilterChip(selected = lockYaw, onClick = { lockYaw = !lockYaw }, label = { Text("Z Yaw") })
        }
        Text(
            "Pitch: tilt the top edge up/down. Roll: twist around the long axis. Yaw: turn like a steering wheel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Thumb zone: the big hold button sits on the side of the holding hand.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (settings.leftHanded) {
                zoomButton(Modifier.weight(1.6f))
                recenterButton(Modifier.weight(1f))
            } else {
                recenterButton(Modifier.weight(1f))
                zoomButton(Modifier.weight(1.6f))
            }
        }
    }
}

private val HOLD_HEIGHT = 96.dp

private class PhoneColors(val screen: Color, val back: Color, val top: Color, val side: Color, val ink: Color)

private const val HALF_W = 0.45
private const val HALF_H = 0.9
private const val HALF_D = 0.06
private const val CAMERA = 6.0

private fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
    val r = DoubleArray(9)
    for (i in 0 until 3) for (j in 0 until 3) for (k in 0 until 3) r[i * 3 + j] += a[i * 3 + k] * b[k * 3 + j]
    return r
}

private fun rotX(a: Double) = doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

private fun rotY(a: Double) = doubleArrayOf(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a))

private fun apply(m: DoubleArray, v: DoubleArray) = doubleArrayOf(
    m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
    m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
    m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
)

/** Fixed camera tilt so the thickness and the top edge show even when the phone faces the viewer. */
private val VIEW = mul(rotX(0.35), rotY(-0.5))
private val HALF = doubleArrayOf(HALF_W, HALF_H, HALF_D)
private val LIGHT = doubleArrayOf(-0.35, 0.55, 0.75).let { v ->
    val n = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    doubleArrayOf(v[0] / n, v[1] / n, v[2] / n)
}

/** Face normals in the phone frame; index 0 is the screen, 1 the back, 2 the top edge. */
private val NORMALS = listOf(
    intArrayOf(0, 0, 1), intArrayOf(0, 0, -1), intArrayOf(0, 1, 0),
    intArrayOf(0, -1, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
)
private val CORNER_SIGNS = arrayOf(intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(1, 1), intArrayOf(-1, 1))

private class Face(val xs: FloatArray, val ys: FloatArray, val shade: Float, val index: Int, val depth: Double)

/**
 * Phone-shaped cuboid rotated by [q] (the relative orientation: phone frame -> reference frame, x right, y up,
 * z toward the viewer), perspective-projected, back-face culled, painter's-sorted and Lambert shaded.
 */
private fun DrawScope.drawPhone(q: Quat, colors: PhoneColors) {
    val scale = min(size.width, size.height) * 0.3
    val total = mul(VIEW, q.toMatrix())
    fun project(p: DoubleArray): Offset {
        val persp = CAMERA / (CAMERA - p[2]).coerceAtLeast(0.1)
        return Offset((size.width / 2 + p[0] * scale * persp).toFloat(), (size.height / 2 - p[1] * scale * persp).toFloat())
    }

    val faces = ArrayList<Face>()
    for ((index, n) in NORMALS.withIndex()) {
        val axis = n.indexOfFirst { it != 0 }
        val nv = apply(total, doubleArrayOf(n[0].toDouble(), n[1].toDouble(), n[2].toDouble()))
        val center = DoubleArray(3) { n[it] * HALF[it] }
        val cv = apply(total, center)
        if (nv[0] * -cv[0] + nv[1] * -cv[1] + nv[2] * (CAMERA - cv[2]) <= 0.0) continue
        val u = (axis + 1) % 3
        val v = (axis + 2) % 3
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for ((i, s) in CORNER_SIGNS.withIndex()) {
            val local = center.copyOf()
            local[u] += s[0] * HALF[u]
            local[v] += s[1] * HALF[v]
            val p = project(apply(total, local))
            xs[i] = p.x
            ys[i] = p.y
        }
        val lambert = (nv[0] * LIGHT[0] + nv[1] * LIGHT[1] + nv[2] * LIGHT[2]).coerceAtLeast(0.0)
        faces += Face(xs, ys, (0.35 + 0.65 * lambert).toFloat(), index, CAMERA - cv[2])
    }
    faces.sortByDescending { it.depth }

    for (f in faces) {
        val base = when (f.index) {
            0 -> colors.screen
            1 -> colors.back
            2 -> colors.top
            else -> colors.side
        }
        val path = Path().apply {
            moveTo(f.xs[0], f.ys[0])
            for (i in 1..3) lineTo(f.xs[i], f.ys[i])
            close()
        }
        drawPath(path, Color(base.red * f.shade, base.green * f.shade, base.blue * f.shade, 1f))
        drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(2f))
        if (f.index == 0) drawScreenDetails(f, colors.ink)
    }
}

/** Bilinear point on a projected face with corners (-,-), (+,-), (+,+), (-,+); u, v in 0..1. */
private fun Face.at(u: Float, v: Float): Offset {
    val bx = xs[0] + (xs[1] - xs[0]) * u
    val by = ys[0] + (ys[1] - ys[0]) * u
    val tx = xs[3] + (xs[2] - xs[3]) * u
    val ty = ys[3] + (ys[2] - ys[3]) * u
    return Offset(bx + (tx - bx) * v, by + (ty - by) * v)
}

/** Dark inset display plus a speaker slot at the top edge, so "up" is readable. */
private fun DrawScope.drawScreenDetails(f: Face, ink: Color) {
    val inner = Path().apply {
        val a = f.at(0.08f, 0.04f)
        moveTo(a.x, a.y)
        for ((u, v) in listOf(0.92f to 0.04f, 0.92f to 0.9f, 0.08f to 0.9f)) f.at(u, v).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(inner, Color.Black.copy(alpha = 0.45f))
    drawLine(ink.copy(alpha = 0.9f), f.at(0.36f, 0.95f), f.at(0.64f, 0.95f), strokeWidth = 5f)
}
