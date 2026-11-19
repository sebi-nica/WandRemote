package com.example.wandremote.ui.screens.pointer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.ConnectionState
import com.example.wandremote.sensors.VolumeKeys
import com.example.wandremote.shared.MouseButton
import com.example.wandremote.shared.Packet

private const val SCROLL_NOTCHES_PER_STRIP = 10f

private fun vibrateClick(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** Button that reports press and release separately, so hold-and-move dragging works. */
@Composable
private fun HoldButton(label: String, primary: Boolean, onDown: () -> Unit, onUp: () -> Unit, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        pressed -> scheme.tertiary
        primary -> scheme.primary
        else -> scheme.secondaryContainer
    }
    val fg = when {
        pressed -> scheme.onTertiary
        primary -> scheme.onPrimary
        else -> scheme.onSecondaryContainer
    }
    Box(
        modifier
            .background(bg, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onDown()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        onUp()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun PointerScreen(vm: WandViewModel) {
    val context = LocalContext.current
    val connection by vm.connection.state.collectAsState()
    val isConnected = connection is ConnectionState.Connected
    var touchClutch by remember { mutableStateOf(false) }
    var proximity by remember { mutableStateOf(false) }
    val clutch = touchClutch || proximity
    var scrollTouched by remember { mutableStateOf(false) }
    val down = remember { booleanArrayOf(false, false) } // [left, right]

    fun setButton(button: MouseButton, pressed: Boolean) {
        val i = if (button == MouseButton.LEFT) 0 else 1
        if (down[i] == pressed) return
        down[i] = pressed
        vm.connection.send(Packet.Button(button, pressed))
        if (pressed) vibrateClick(context)
    }

    // Gyro + proximity only while this screen is resumed.
    LifecycleResumeEffect(vm) {
        vm.sensors.startPointer(
            onMove = { dx, dy -> vm.connection.send(Packet.Move(dx, dy)) },
            onProximityChange = { proximity = it },
        )
        onPauseOrDispose {
            vm.sensors.stopPointer()
            proximity = false
            touchClutch = false
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

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (clutch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(24.dp),
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            touchClutch = true
                            vm.sensors.touchClutch = true
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                touchClutch = false
                                vm.sensors.touchClutch = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (clutch) "Moving cursor..." else "Hold to move cursor",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (clutch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(
                        if (scrollTouched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown()
                            scrollTouched = true
                            var lastY = first.position.y
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                    val dy = change.position.y - lastY
                                    lastY = change.position.y
                                    if (dy != 0f) {
                                        // Drag down = scroll down = positive dy.
                                        vm.connection.send(Packet.Scroll(0f, dy / size.height * SCROLL_NOTCHES_PER_STRIP))
                                        change.consume()
                                    }
                                    if (!change.pressed) break
                                }
                            } finally {
                                scrollTouched = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("SCROLL", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }

        Text(
            "Volume keys = click  |  Tap the back of the phone = click",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HoldButton(
                "Left click", true,
                onDown = { setButton(MouseButton.LEFT, true) },
                onUp = { setButton(MouseButton.LEFT, false) },
                modifier = Modifier.weight(1f).height(72.dp),
            )
            HoldButton(
                "Right click", false,
                onDown = { setButton(MouseButton.RIGHT, true) },
                onUp = { setButton(MouseButton.RIGHT, false) },
                modifier = Modifier.weight(1f).height(72.dp),
            )
        }
    }
}
