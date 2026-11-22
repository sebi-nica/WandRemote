package com.example.wandremote.ui.screens.pointer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.wandremote.ui.screens.pointer.layout.ControlId
import com.example.wandremote.ui.screens.pointer.layout.ControlShape
import com.example.wandremote.ui.screens.pointer.layout.ControlSpec
import com.example.wandremote.ui.screens.pointer.layout.PointerLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Per-frame visual state of the controls; everything is keyed by [ControlId] so the renderer stays generic. */
internal class ControlsVisuals(
    /** Drawn in their "on" color (movement toggle on, laser held). */
    val active: Set<ControlId> = emptySet(),
    /** Drawn dimmed. They still receive touches (the screen decides what a press does). */
    val disabled: Set<ControlId> = emptySet(),
)

/**
 * Draws every control of [layout] generically (by shape and id) and dispatches touches by [PointerLayout.hitTest].
 *
 * Multi-touch: each finger is bound to the control it landed on, by its pointer id, until it lifts. Fingers on
 * different controls work independently and at the same time (hold a click while turning the knob, hold the laser
 * while pressing arrows). A control that is already held by another finger ignores further touches.
 *
 *  - [onPress] fires on touch down, [onRelease] on lift (or with `inside = false` when the gesture is cancelled).
 *    `inside` tells whether the finger was still over the control it started on (for tap-style controls).
 *  - The knob is special only in that finger movement around its center is reported through [onKnobTurn]
 *    (degrees, clockwise positive) and [onKnobNotch] (one haptic tick per scroll notch).
 *
 * Nothing here knows what a control does; that is bound by the caller from the [ControlId].
 */
@Composable
internal fun PointerControls(
    layout: PointerLayout,
    visuals: ControlsVisuals,
    degreesPerNotch: Float,
    onPress: (ControlId) -> Unit,
    onRelease: (ControlId, Boolean) -> Unit,
    onKnobTurn: (Float) -> Unit,
    onKnobNotch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val mouseOn = rememberVectorPainter(Icons.Filled.Mouse)
    val mouseOff = rememberVectorPainter(Icons.Outlined.Mouse)

    var pressed by remember { mutableStateOf<Set<ControlId>>(emptySet()) }
    var knobAngle by remember { mutableFloatStateOf(0f) } // degrees, accumulated, drawn rotation

    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val turn by rememberUpdatedState<(Float) -> Unit> { d ->
        knobAngle += d
        onKnobTurn(d)
    }
    val notch by rememberUpdatedState(onKnobNotch)
    val perNotch by rememberUpdatedState(degreesPerNotch)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(layout) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val knob = layout[ControlId.KNOB]?.shape as? ControlShape.Circle
                val kx = (knob?.cx ?: 0f) * w
                val ky = (knob?.cy ?: 0f) * h

                fun angleAt(x: Float, y: Float): Float =
                    Math.toDegrees(atan2((y - ky).toDouble(), (x - kx).toDouble())).toFloat()

                awaitEachGesture {
                    val owner = HashMap<PointerId, ControlId>()
                    val lastAngle = HashMap<PointerId, Float>()
                    var notchPos = 0f // total knob rotation of this gesture in notches, for haptic ticks
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val id = owner[c.id]
                                if (id == null) {
                                    if (!c.changedToDown()) continue
                                    val hit = layout.hitTest(c.position.x, c.position.y, w, h) ?: continue
                                    if (owner.containsValue(hit)) continue
                                    owner[c.id] = hit
                                    pressed = pressed + hit
                                    if (hit == ControlId.KNOB) lastAngle[c.id] = angleAt(c.position.x, c.position.y)
                                    c.consume()
                                    press(hit)
                                } else if (!c.pressed) {
                                    val inside = layout.hitTest(c.position.x, c.position.y, w, h) == id
                                    owner.remove(c.id)
                                    lastAngle.remove(c.id)
                                    pressed = pressed - id
                                    c.consume()
                                    release(id, inside)
                                } else {
                                    if (id == ControlId.KNOB) {
                                        val ang = angleAt(c.position.x, c.position.y)
                                        var d = ang - (lastAngle[c.id] ?: ang)
                                        if (d > 180f) d -= 360f
                                        if (d < -180f) d += 360f
                                        lastAngle[c.id] = ang
                                        if (d != 0f) {
                                            // Screen angles grow clockwise, so d > 0 = clockwise = scroll down.
                                            turn(d)
                                            val before = floor(notchPos)
                                            notchPos += d / perNotch
                                            if (floor(notchPos) != before) notch()
                                        }
                                    }
                                    c.consume() // held control: swallow drags
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        // Gesture cancelled (dialog opened, layout changed...): never leave a control held.
                        for ((_, id) in owner) release(id, false)
                        pressed = emptySet()
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        for (spec in layout.controls) {
            val colors = controlColors(
                spec.id,
                pressed = spec.id in pressed,
                active = spec.id in visuals.active,
                disabled = spec.id in visuals.disabled,
                scheme = scheme,
            )
            when (val shape = spec.shape) {
                is ControlShape.RingSector -> drawRingSector(shape, colors, w, h)
                is ControlShape.RoundRect -> drawRoundRect(shape, colors, w, h)
                is ControlShape.Circle -> drawCircleControl(shape, colors, w, h)
            }
            when (spec.id) {
                ControlId.KNOB -> drawKnobMarks(spec.shape as ControlShape.Circle, knobAngle, perNotch, scheme, w, h)
                ControlId.MOVE_TOGGLE -> {
                    val on = ControlId.MOVE_TOGGLE in visuals.active
                    drawIcon(spec.shape as ControlShape.Circle, if (on) mouseOn else mouseOff, colors.content, w, h)
                }
                ControlId.ARROW_LEFT, ControlId.ARROW_RIGHT, ControlId.ARROW_UP, ControlId.ARROW_DOWN ->
                    drawArrowGlyph(spec.id, spec.shape as ControlShape.RoundRect, colors.content, w, h)
                else -> drawLabel(spec, colors.content, measurer, typography.titleMedium, typography.labelMedium, typography.titleLarge, w, h)
            }
        }
    }
}

private class ControlColors(val container: Color, val content: Color, val outline: Color?)

private fun controlColors(
    id: ControlId,
    pressed: Boolean,
    active: Boolean,
    disabled: Boolean,
    scheme: androidx.compose.material3.ColorScheme,
): ControlColors {
    if (disabled) return ControlColors(scheme.onSurface.copy(alpha = 0.12f), scheme.onSurface.copy(alpha = 0.38f), null)
    val on = pressed || active
    return when (id) {
        ControlId.KNOB -> ControlColors(scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.outline)
        ControlId.LEFT_CLICK ->
            if (on) ControlColors(scheme.primary, scheme.onPrimary, null)
            else ControlColors(scheme.secondaryContainer, scheme.onSecondaryContainer, null)
        ControlId.RIGHT_CLICK ->
            if (on) ControlColors(scheme.tertiary, scheme.onTertiary, null)
            else ControlColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, null)
        ControlId.MOVE_TOGGLE ->
            if (on) ControlColors(scheme.primary, scheme.onPrimary, null)
            else ControlColors(scheme.surface, scheme.onSurfaceVariant, scheme.outline)
        ControlId.LASER ->
            if (on) ControlColors(scheme.error, scheme.onError, null)
            else ControlColors(scheme.errorContainer, scheme.onErrorContainer, null)
        ControlId.ARROW_LEFT, ControlId.ARROW_RIGHT, ControlId.ARROW_UP, ControlId.ARROW_DOWN ->
            if (on) ControlColors(scheme.primary, scheme.onPrimary, null)
            else ControlColors(scheme.secondaryContainer, scheme.onSecondaryContainer, null)
    }
}

private fun DrawScope.drawRingSector(s: ControlShape.RingSector, colors: ControlColors, w: Float, h: Float) {
    val inner = s.innerR * w
    val outer = s.outerR * w
    val mid = (inner + outer) / 2f
    drawArc(
        color = colors.container,
        startAngle = s.startDeg, sweepAngle = s.sweepDeg, useCenter = false,
        topLeft = Offset(s.cx * w - mid, s.cy * h - mid), size = Size(mid * 2, mid * 2),
        style = Stroke(outer - inner),
    )
}

private fun DrawScope.drawRoundRect(s: ControlShape.RoundRect, colors: ControlColors, w: Float, h: Float) {
    val topLeft = Offset(s.left * w, s.top * h)
    val size = Size((s.right - s.left) * w, (s.bottom - s.top) * h)
    val radius = CornerRadius(min(s.cornerRadius * w, min(size.width, size.height) / 2f))
    drawRoundRect(colors.container, topLeft, size, radius, style = Fill)
    colors.outline?.let { drawRoundRect(it, topLeft, size, radius, style = Stroke(1.5.dp.toPx())) }
}

private fun DrawScope.drawCircleControl(s: ControlShape.Circle, colors: ControlColors, w: Float, h: Float) {
    val c = Offset(s.cx * w, s.cy * h)
    drawCircle(colors.container, s.r * w, c)
    colors.outline?.let { drawCircle(it, s.r * w - 1.dp.toPx(), c, style = Stroke(2.dp.toPx())) }
}

/** Knob: rotating tick marks (one per scroll notch) and an indicator dot. */
private fun DrawScope.drawKnobMarks(
    s: ControlShape.Circle,
    knobAngle: Float,
    degreesPerNotch: Float,
    scheme: androidx.compose.material3.ColorScheme,
    w: Float,
    h: Float,
) {
    val cx = s.cx * w
    val cy = s.cy * h
    val r = s.r * w
    val ticks = (360f / degreesPerNotch).roundToInt()
    for (i in 0 until ticks) {
        val a = Math.toRadians((knobAngle + i * degreesPerNotch).toDouble())
        val c = cos(a).toFloat()
        val sn = sin(a).toFloat()
        drawLine(
            color = if (i == 0) scheme.primary else scheme.onSurfaceVariant,
            start = Offset(cx + c * r * 0.80f, cy + sn * r * 0.80f),
            end = Offset(cx + c * r * 0.94f, cy + sn * r * 0.94f),
            strokeWidth = (if (i == 0) 4.dp else 2.dp).toPx(),
            cap = StrokeCap.Round,
        )
    }
    val dot = Math.toRadians(knobAngle.toDouble())
    drawCircle(scheme.primary, 9.dp.toPx(), Offset(cx + cos(dot).toFloat() * r * 0.55f, cy + sin(dot).toFloat() * r * 0.55f))
}

private fun DrawScope.drawIcon(s: ControlShape.Circle, painter: Painter, tint: Color, w: Float, h: Float) {
    val side = s.r * w * 1.1f
    translate(s.cx * w - side / 2f, s.cy * h - side / 2f) {
        with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** A solid triangle pointing in the direction of [id], centered in the button. */
private fun DrawScope.drawArrowGlyph(id: ControlId, s: ControlShape.RoundRect, color: Color, w: Float, h: Float) {
    val cx = (s.left + s.right) / 2f * w
    val cy = (s.top + s.bottom) / 2f * h
    val half = min((s.right - s.left) * w, (s.bottom - s.top) * h) * 0.26f
    val path = Path()
    when (id) {
        ControlId.ARROW_LEFT -> {
            path.moveTo(cx - half, cy)
            path.lineTo(cx + half, cy - half * 1.2f)
            path.lineTo(cx + half, cy + half * 1.2f)
        }
        ControlId.ARROW_RIGHT -> {
            path.moveTo(cx + half, cy)
            path.lineTo(cx - half, cy - half * 1.2f)
            path.lineTo(cx - half, cy + half * 1.2f)
        }
        ControlId.ARROW_UP -> {
            path.moveTo(cx, cy - half)
            path.lineTo(cx - half * 1.2f, cy + half)
            path.lineTo(cx + half * 1.2f, cy + half)
        }
        else -> {
            path.moveTo(cx, cy + half)
            path.lineTo(cx - half * 1.2f, cy - half)
            path.lineTo(cx + half * 1.2f, cy - half)
        }
    }
    path.close()
    drawPath(path, color)
}

/** The spec's label, centered in the shape (ring sectors: at the middle of the arc). */
private fun DrawScope.drawLabel(
    spec: ControlSpec,
    color: Color,
    measurer: TextMeasurer,
    laserStyle: TextStyle,
    smallStyle: TextStyle,
    ringStyle: TextStyle,
    w: Float,
    h: Float,
) {
    val text = spec.label ?: return
    val center: Offset
    val maxWidth: Float
    val style: TextStyle
    when (val shape = spec.shape) {
        is ControlShape.RingSector -> {
            val mid = (shape.innerR + shape.outerR) / 2f * w
            val a = Math.toRadians((shape.startDeg + shape.sweepDeg / 2f).toDouble())
            center = Offset(shape.cx * w + cos(a).toFloat() * mid, shape.cy * h + sin(a).toFloat() * mid)
            maxWidth = (shape.outerR - shape.innerR) * w
            style = ringStyle
        }
        is ControlShape.RoundRect -> {
            center = Offset((shape.left + shape.right) / 2f * w, (shape.top + shape.bottom) / 2f * h)
            maxWidth = (shape.right - shape.left) * w - 8.dp.toPx()
            style = if (spec.id == ControlId.LASER) laserStyle else smallStyle
        }
        is ControlShape.Circle -> {
            center = Offset(shape.cx * w, shape.cy * h)
            maxWidth = shape.r * w * 1.6f
            style = smallStyle
        }
    }
    val fixed = maxWidth.roundToInt().coerceAtLeast(1)
    val layout = measurer.measure(
        text,
        style.copy(textAlign = TextAlign.Center),
        constraints = Constraints(minWidth = fixed, maxWidth = fixed),
    )
    drawText(layout, color = color, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
