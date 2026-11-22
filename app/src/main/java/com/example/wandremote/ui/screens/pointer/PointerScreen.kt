package com.example.wandremote.ui.screens.pointer

import android.view.KeyEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.DialogProperties
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
import com.example.wandremote.ui.screens.calibration.CornerCalibrationCard
import com.example.wandremote.ui.screens.pointer.layout.ControlId
import com.example.wandremote.ui.screens.pointer.layout.DefaultLayouts
import kotlinx.coroutines.launch

private val EDGE_PADDING = 12.dp

/** Fraction of the content width the status text may use (the top buttons start at x = 0.50). */
private const val STATUS_WIDTH_FRACTION = 0.46f

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
    val profile by vm.calibrationProfile.collectAsState()
    val laserHeld by vm.laser.laserHeld.collectAsState()
    val needsRecenter by vm.laser.needsRecenter.collectAsState()
    val isConnected = connection.companionConnected || connection is ConnectionState.ConnectedBluetooth
    val bluetooth = connection is ConnectionState.ConnectedBluetooth
    val calibrated = profile != null

    var movementOn by remember { mutableStateOf(false) }
    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SensorHub.PointerSession?>(null) }
    val keysDown = remember { mutableSetOf<RemoteKey>() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String, offerRecalibrate: Boolean = false) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message,
                actionLabel = if (offerRecalibrate) "Recalibrate" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) showCalibration = true
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
        if (!bluetooth) vm.laser.start(vm.calibrationProfile.value?.savedAtMs)
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

    // A calibration captured while this screen is open is already in the current heading; another PC's is not.
    LaunchedEffect(profile?.savedAtMs) { vm.laser.onProfileChanged(profile?.savedAtMs) }

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
            !calibrated -> notify("Calibrate the screen corners to use the laser", offerRecalibrate = true)
            else -> {
                setMovement(false) // touching the laser always turns the cursor off, and it stays off
                if (vm.laser.setHeld(true)) haptics.pulse(20)
            }
        }
    }

    fun pressRecenter() {
        when {
            bluetooth -> notify("The laser needs the PC app (Wi-Fi or Bluetooth link)")
            !calibrated -> notify("Calibrate the screen corners first", offerRecalibrate = true)
            else -> vm.laser.recenter()
        }
    }

    val onPress by rememberUpdatedState<(ControlId) -> Unit> { id ->
        when (id) {
            ControlId.LEFT_CLICK -> setButton(MouseButton.LEFT, true)
            ControlId.RIGHT_CLICK -> setButton(MouseButton.RIGHT, true)
            ControlId.ARROW_LEFT, ControlId.ARROW_RIGHT, ControlId.ARROW_UP, ControlId.ARROW_DOWN ->
                keyFor(id)?.let { setKey(it, true) }
            ControlId.LASER -> pressLaser()
            ControlId.RECENTER -> pressRecenter()
            ControlId.KNOB, ControlId.MOVE_TOGGLE, ControlId.RECALIBRATE -> Unit
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
            ControlId.RECALIBRATE -> if (inside) {
                setMovement(false)
                showCalibration = true
            }
            ControlId.KNOB, ControlId.RECENTER -> Unit
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
        val disabled = buildSet {
            if (bluetooth) {
                add(ControlId.LASER)
                add(ControlId.RECENTER)
            } else if (!calibrated) {
                add(ControlId.RECENTER)
            }
        }
        val attention = if (!bluetooth && calibrated && needsRecenter) setOf(ControlId.RECENTER) else emptySet()
        val visuals = ControlsVisuals(active = active, disabled = disabled, attention = attention)

        PointerControls(
            layout = layout,
            visuals = visuals,
            degreesPerNotch = degreesPerNotch,
            onPress = { onPress(it) },
            onRelease = { id, inside -> onRelease(id, inside) },
            onKnobTurn = { onKnobTurn(it) },
            onKnobNotch = { onKnobNotch() },
        )

        // Status text, top-left (the layout keeps its controls out of x < 0.50 near the top).
        Column(Modifier.align(Alignment.TopStart).width(maxWidth * STATUS_WIDTH_FRACTION)) {
            Text(
                if (movementOn) "Movement ON" else "Movement OFF",
                style = MaterialTheme.typography.titleMedium,
                color = if (movementOn) scheme.primary else scheme.onSurfaceVariant,
            )
            val hint = when {
                connection is ConnectionState.Disconnected -> "Not connected"
                bluetooth -> "Bluetooth mouse: laser unavailable, connect to the PC app"
                !calibrated -> "Laser needs calibration (Recalibrate)"
                needsRecenter -> "Re-center before using the laser"
                else -> "Volume down = left click, volume up = right click"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }

    if (showCalibration) {
        AlertDialog(
            onDismissRequest = { showCalibration = false },
            confirmButton = { TextButton(onClick = { showCalibration = false }) { Text("Close") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { CornerCalibrationCard(vm) }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
        )
    }
}
