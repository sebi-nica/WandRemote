package com.example.wandremote.ui.screens.pointer

import android.view.KeyEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.network.companionConnected
import com.example.wandremote.sensors.SensorHub
import com.example.wandremote.sensors.VolumeKeys
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet
import com.example.wandremote.shared.RemoteKey
import com.example.wandremote.shared.RemoteMode
import com.example.wandremote.ui.screens.pointer.layout.ControlId
import com.example.wandremote.ui.screens.pointer.layout.DefaultLayouts
import kotlinx.coroutines.launch

private val EDGE_PADDING = 12.dp

/** Fraction of the content width the status text may use. */
private const val STATUS_WIDTH_FRACTION = 0.9f

private fun keyFor(id: ControlId): RemoteKey? = when (id) {
    ControlId.ARROW_LEFT -> RemoteKey.LEFT
    ControlId.ARROW_RIGHT -> RemoteKey.RIGHT
    ControlId.ARROW_UP -> RemoteKey.UP
    ControlId.ARROW_DOWN -> RemoteKey.DOWN
    else -> null
}

/**
 * Main screen: gyro cursor with a clutch toggle, scroll knob, click ring, arrow keys and a hold-for-laser button.
 * The button arrangement comes from a [com.example.wandremote.ui.screens.pointer.layout.PointerLayout]; this
 * function only binds behavior to each [ControlId].
 */
@Composable
fun PointerScreen(vm: WandViewModel) {
    val view = LocalView.current
    val settings by vm.settings.collectAsState()
    val haptics = vm.haptics
    val connection by vm.connectionState.collectAsState()
    val laserHeld by vm.laser.laserHeld.collectAsState()
    val isConnected = connection.companionConnected || connection is ConnectionState.ConnectedBluetooth
    val bluetooth = connection is ConnectionState.ConnectedBluetooth

    var movementOn by remember { mutableStateOf(false) }
    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SensorHub.PointerSession?>(null) }
    val keysDown = remember { mutableSetOf<RemoteKey>() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    fun setButton(button: MouseButton, pressed: Boolean) {
        val left = button == MouseButton.LEFT
        if ((if (left) leftDown else rightDown) == pressed) return
        if (left) leftDown = pressed else rightDown = pressed
        vm.output.send(Packet.Button(button, pressed))
        if (pressed) haptics.pulse(20)
    }

    fun setKey(key: RemoteKey, pressed: Boolean) {
        if (pressed == (key in keysDown)) return
        if (pressed) keysDown.add(key) else keysDown.remove(key)
        vm.output.send(Packet.Key(key, pressed))
        if (pressed) haptics.pulse(12)
    }

    fun setMovement(on: Boolean) {
        movementOn = on
        session?.movementEnabled = on
    }

    // Gyro (and the laser's aim sensor) only while this screen is resumed. Movement always starts OFF.
    LifecycleResumeEffect(vm, bluetooth) {
        setMovement(false)
        vm.setMode(RemoteMode.POINTER)
        session = vm.sensors.openPointer(onMove = { dx, dy -> vm.output.send(Packet.Move(dx, dy)) })
        if (!bluetooth) vm.laser.start()
        onPauseOrDispose {
            vm.laser.stop()
            session?.close()
            session = null
            movementOn = false
            // Never leave a button or key stuck down on the PC.
            setButton(MouseButton.LEFT, false)
            setButton(MouseButton.RIGHT, false)
            for (key in keysDown.toList()) setKey(key, false)
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

    fun pressLaser() {
        when {
            bluetooth -> notify("The laser needs the PC app (Wi-Fi or Bluetooth link)")
            !vm.laser.hasAimSensor -> notify("This phone has no rotation sensor for the laser")
            else -> {
                setMovement(false) // touching the laser always turns the cursor off, and it stays off
                if (vm.laser.setHeld(true)) haptics.pulse(20)
            }
        }
    }

    val onPress by rememberUpdatedState<(ControlId) -> Unit> { id ->
        when (id) {
            ControlId.LEFT_CLICK -> setButton(MouseButton.LEFT, true)
            ControlId.RIGHT_CLICK -> setButton(MouseButton.RIGHT, true)
            ControlId.ARROW_LEFT, ControlId.ARROW_RIGHT, ControlId.ARROW_UP, ControlId.ARROW_DOWN ->
                keyFor(id)?.let { setKey(it, true) }
            ControlId.LASER -> pressLaser()
            ControlId.KNOB, ControlId.MOVE_TOGGLE -> Unit
        }
    }
    val onRelease by rememberUpdatedState<(ControlId, Boolean) -> Unit> { id, inside ->
        when (id) {
            ControlId.LEFT_CLICK -> setButton(MouseButton.LEFT, false)
            ControlId.RIGHT_CLICK -> setButton(MouseButton.RIGHT, false)
            ControlId.ARROW_LEFT, ControlId.ARROW_RIGHT, ControlId.ARROW_UP, ControlId.ARROW_DOWN ->
                keyFor(id)?.let { setKey(it, false) }
            ControlId.LASER -> vm.laser.setHeld(false)
            ControlId.MOVE_TOGGLE -> if (inside) {
                setMovement(!movementOn)
                haptics.pulse(15)
            }
            ControlId.KNOB -> Unit
        }
    }
    val invertScroll = settings.invertScroll
    val degreesPerNotch = settings.scrollDegreesPerNotch
    val onKnobTurn by rememberUpdatedState<(Float) -> Unit> { deltaDeg ->
        val notches = deltaDeg / degreesPerNotch
        vm.output.send(Packet.Scroll(0f, if (invertScroll) -notches else notches))
    }
    val onKnobNotch by rememberUpdatedState<() -> Unit> { haptics.tick(view) }

    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxSize().padding(EDGE_PADDING)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val aspect = hPx / wPx
        val layout = remember(aspect, settings.knobSizeFraction, settings.leftHanded) {
            DefaultLayouts.forScreen(settings.knobSizeFraction, aspect, flipped = settings.leftHanded)
        }

        val active = buildSet {
            if (movementOn) add(ControlId.MOVE_TOGGLE)
            if (laserHeld) add(ControlId.LASER)
        }
        val disabled = if (bluetooth) setOf(ControlId.LASER) else emptySet()
        val visuals = ControlsVisuals(active = active, disabled = disabled)

        PointerControls(
            layout = layout,
            visuals = visuals,
            degreesPerNotch = degreesPerNotch,
            onPress = { onPress(it) },
            onRelease = { id, inside -> onRelease(id, inside) },
            onKnobTurn = { onKnobTurn(it) },
            onKnobNotch = { onKnobNotch() },
        )

        // Status text along the top (the layout keeps its controls clear of this strip).
        Column(Modifier.align(Alignment.TopStart).width(maxWidth * STATUS_WIDTH_FRACTION)) {
            Text(
                if (movementOn) "Movement ON" else "Movement OFF",
                style = MaterialTheme.typography.titleMedium,
                color = if (movementOn) scheme.primary else scheme.onSurfaceVariant,
            )
            val hint = when {
                connection is ConnectionState.Disconnected -> "Not connected"
                bluetooth -> "Bluetooth mouse: laser unavailable, connect to the PC app"
                else -> "Volume down = left click, volume up = right click"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}
