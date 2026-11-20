package com.example.wandremote.ui.screens.pointer

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.sensors.SensorHub
import com.example.wandremote.sensors.VolumeKeys
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.RemoteMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val EDGE_PADDING = 12.dp
private val TOGGLE_RADIUS = 30.dp

private enum class Role { LEFT, RIGHT, KNOB }

private class Geo(val cx: Float, val cy: Float, val knobR: Float, val ringIn: Float, val ringOut: Float)

private fun labelAngle(deg: Double, leftHanded: Boolean): Double =
    if (leftHanded) mirrorAngle(deg.toFloat()).toDouble() else deg

/** Screen angle of (x, y) around the center in degrees, 0 = east, clockwise positive, [0, 360). */
private fun angleDeg(x: Float, y: Float, g: Geo): Float {
    val a = Math.toDegrees(atan2((y - g.cy).toDouble(), (x - g.cx).toDouble())).toFloat()
    return if (a < 0f) a + 360f else a
}

@Composable
fun PointerScreen(vm: WandViewModel) {
    val view = LocalView.current
    val settings by vm.settings.collectAsState()
    val leftHanded = settings.leftHanded
    val haptics = vm.haptics
    val connection by vm.connectionState.collectAsState()
    val isConnected = connection is ConnectionState.Connected || connection is ConnectionState.ConnectedBluetooth

    var movementOn by remember { mutableStateOf(false) }
    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    var knobAngle by remember { mutableFloatStateOf(0f) } // degrees, accumulated, drawn rotation
    var session by remember { mutableStateOf<SensorHub.PointerSession?>(null) }

    fun setButton(button: MouseButton, pressed: Boolean) {
        val left = button == MouseButton.LEFT
        if ((if (left) leftDown else rightDown) == pressed) return
        if (left) leftDown = pressed else rightDown = pressed
        vm.output.send(Packet.Button(button, pressed))
        if (pressed) haptics.pulse(20)
    }

    fun setMovement(on: Boolean) {
        movementOn = on
        session?.movementEnabled = on
    }

    // Gyro only while this screen is resumed. Movement always starts OFF.
    LifecycleResumeEffect(vm) {
        setMovement(false)
        vm.setMode(RemoteMode.POINTER)
        session = vm.sensors.openPointer(onMove = { dx, dy -> vm.output.send(Packet.Move(dx, dy)) })
        onPauseOrDispose {
            session?.close()
            session = null
            movementOn = false
            // Never leave a button stuck down on the PC.
            setButton(MouseButton.LEFT, false)
            setButton(MouseButton.RIGHT, false)
        }
    }

    // Volume keys act as clicks only while connected and on this screen.
    DisposableEffect(isConnected) {
        if (isConnected) {
            VolumeKeys.handler = { code, pressed ->
                setButton(if (code == KeyEvent.KEYCODE_VOLUME_UP) MouseButton.RIGHT else MouseButton.LEFT, pressed)
            }
        }
        onDispose { VolumeKeys.handler = null }
    }

    val onButton by rememberUpdatedState<(MouseButton, Boolean) -> Unit> { b, p -> setButton(b, p) }
    val degreesPerNotch by rememberUpdatedState(settings.scrollDegreesPerNotch)
    val onKnobTurn by rememberUpdatedState<(Float) -> Unit> { deltaDeg ->
        knobAngle += deltaDeg
        val notches = deltaDeg / degreesPerNotch
        vm.output.send(Packet.Scroll(0f, if (settings.invertScroll) -notches else notches))
    }
    val onTick by rememberUpdatedState<() -> Unit> { haptics.tick(view) }

    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxSize().padding(EDGE_PADDING)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val knobR = wPx * settings.knobSizeFraction
        val gap = with(density) { 8.dp.toPx() }
        val ringIn = knobR + gap
        val ringOut = ringIn + knobR
        val geo = remember(wPx, hPx, knobR, leftHanded) {
            Geo(clusterCenterX(wPx, knobR, leftHanded), hPx - knobR, knobR, ringIn, ringOut)
        }
        val toggleR = with(density) { TOGGLE_RADIUS.toPx() }

        // Status text, top-left.
        Column(Modifier.align(Alignment.TopStart)) {
            Text(
                if (movementOn) "Movement ON" else "Movement OFF",
                style = MaterialTheme.typography.titleLarge,
                color = if (movementOn) scheme.primary else scheme.onSurfaceVariant,
            )
            Text(
                "Volume down = left click, volume up = right click",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // Knob, click ring and all touch handling.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geo) {
                    awaitEachGesture {
                        val roles = HashMap<PointerId, Role>()
                        val lastAngle = HashMap<PointerId, Float>()
                        var notchPos = 0f // total rotation of this gesture in notches, for haptic ticks
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (c in event.changes) {
                                    val role = roles[c.id]
                                    if (role == null) {
                                        if (!c.changedToDown()) continue
                                        val dist = hypot(c.position.x - geo.cx, c.position.y - geo.cy)
                                        val ang = angleDeg(c.position.x, c.position.y, geo)
                                        val hit = when {
                                            dist <= geo.ringIn -> Role.KNOB
                                            dist <= geo.ringOut -> when (ringButtonAt(ang, leftHanded)) {
                                                RingButton.LEFT -> Role.LEFT
                                                RingButton.RIGHT -> Role.RIGHT
                                                null -> null
                                            }
                                            else -> null
                                        }
                                        val newRole = hit ?: continue
                                        roles[c.id] = newRole
                                        c.consume()
                                        when (newRole) {
                                            Role.LEFT -> onButton(MouseButton.LEFT, true)
                                            Role.RIGHT -> onButton(MouseButton.RIGHT, true)
                                            Role.KNOB -> lastAngle[c.id] = ang
                                        }
                                    } else if (!c.pressed) {
                                        roles.remove(c.id)
                                        lastAngle.remove(c.id)
                                        when (role) {
                                            Role.LEFT -> onButton(MouseButton.LEFT, false)
                                            Role.RIGHT -> onButton(MouseButton.RIGHT, false)
                                            Role.KNOB -> Unit
                                        }
                                        c.consume()
                                    } else if (role == Role.KNOB) {
                                        val ang = angleDeg(c.position.x, c.position.y, geo)
                                        var d = ang - (lastAngle[c.id] ?: ang)
                                        if (d > 180f) d -= 360f
                                        if (d < -180f) d += 360f
                                        lastAngle[c.id] = ang
                                        if (d != 0f) {
                                            // Screen angles grow clockwise, so d > 0 = clockwise = scroll down.
                                            onKnobTurn(d)
                                            val before = floor(notchPos)
                                            notchPos += d / degreesPerNotch
                                            if (floor(notchPos) != before) onTick()
                                        }
                                        c.consume()
                                    } else {
                                        c.consume() // held button: swallow drags
                                    }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            if (roles.containsValue(Role.LEFT)) onButton(MouseButton.LEFT, false)
                            if (roles.containsValue(Role.RIGHT)) onButton(MouseButton.RIGHT, false)
                        }
                    }
                },
        ) {
            val center = Offset(geo.cx, geo.cy)
            val thickness = geo.ringOut - geo.ringIn
            val mid = (geo.ringIn + geo.ringOut) / 2f
            val arcTopLeft = Offset(geo.cx - mid, geo.cy - mid)
            val arcSize = Size(mid * 2, mid * 2)
            // Left = half nearest the screen edge (west .. diagonal), right = upper half (diagonal .. north);
            // mirrored when left-handed.
            drawArc(
                color = if (leftDown) scheme.primary else scheme.secondaryContainer,
                startAngle = arcStartAngle(180f, 43f, leftHanded), sweepAngle = 43f, useCenter = false,
                topLeft = arcTopLeft, size = arcSize, style = Stroke(thickness),
            )
            drawArc(
                color = if (rightDown) scheme.tertiary else scheme.tertiaryContainer,
                startAngle = arcStartAngle(227f, 43f, leftHanded), sweepAngle = 43f, useCenter = false,
                topLeft = arcTopLeft, size = arcSize, style = Stroke(thickness),
            )

            // Knob: body, outline, rotating tick marks and indicator dot.
            drawCircle(scheme.surfaceVariant, geo.knobR, center)
            drawCircle(scheme.outline, geo.knobR, center, style = Stroke(2.dp.toPx()))
            val ticks = (360f / degreesPerNotch).roundToInt()
            for (i in 0 until ticks) {
                val a = Math.toRadians((knobAngle + i * degreesPerNotch).toDouble())
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                val start = Offset(geo.cx + c * geo.knobR * 0.80f, geo.cy + s * geo.knobR * 0.80f)
                val end = Offset(geo.cx + c * geo.knobR * 0.94f, geo.cy + s * geo.knobR * 0.94f)
                drawLine(
                    color = if (i == 0) scheme.primary else scheme.onSurfaceVariant,
                    start = start, end = end,
                    strokeWidth = (if (i == 0) 4.dp else 2.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val dotA = Math.toRadians(knobAngle.toDouble())
            drawCircle(
                scheme.primary, 9.dp.toPx(),
                Offset(
                    geo.cx + cos(dotA).toFloat() * geo.knobR * 0.55f,
                    geo.cy + sin(dotA).toFloat() * geo.knobR * 0.55f,
                ),
            )
        }

        // Click labels, centered in each half of the ring.
        @Composable
        fun RingLabel(text: String, angleDegrees: Double, color: androidx.compose.ui.graphics.Color) {
            val mid = (geo.ringIn + geo.ringOut) / 2f
            val a = Math.toRadians(angleDegrees)
            Text(
                text,
                color = color,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.offset {
                    IntOffset(
                        (geo.cx + cos(a).toFloat() * mid).roundToInt() - 10.dp.roundToPx(),
                        (geo.cy + sin(a).toFloat() * mid).roundToInt() - 14.dp.roundToPx(),
                    )
                },
            )
        }
        RingLabel("L", labelAngle(201.5, leftHanded), if (leftDown) scheme.onPrimary else scheme.onSecondaryContainer)
        RingLabel("R", labelAngle(246.5, leftHanded), if (rightDown) scheme.onTertiary else scheme.onTertiaryContainer)

        // Movement toggle, above the ring near the screen edge the cluster is on.
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (if (leftHanded) 0f else wPx - toggleR * 2).roundToInt(),
                        (geo.cy - geo.ringOut - gap * 1.5f - toggleR * 2).roundToInt(),
                    )
                }
                .size(TOGGLE_RADIUS * 2)
                .clip(CircleShape)
                .background(if (movementOn) scheme.primary else scheme.surface)
                .clickable {
                    setMovement(!movementOn)
                    haptics.pulse(15)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (!movementOn) drawCircle(scheme.outline, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            Icon(
                if (movementOn) Icons.Filled.Mouse else Icons.Outlined.Mouse,
                contentDescription = if (movementOn) "Movement on" else "Movement off",
                tint = if (movementOn) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}
