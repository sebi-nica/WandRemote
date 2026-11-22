package com.example.wandremote.ui.screens.calibration

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.wandremote.WandViewModel
import com.example.wandremote.network.companionConnected
import com.example.wandremote.sensors.Aim
import com.example.wandremote.sensors.AimHistory
import com.example.wandremote.sensors.AimSensor
import com.example.wandremote.sensors.ScreenCalibration
import com.example.wandremote.ui.components.SectionCard
import com.example.wandremote.ui.components.TouchDownButton
import java.util.Date

private val CORNER_NAMES = listOf("TOP-LEFT", "TOP-RIGHT", "BOTTOM-RIGHT", "BOTTOM-LEFT")

/**
 * Guided "aim at each screen corner" calibration for the PC the phone is connected to (profile key = Wi-Fi host).
 * Used on the Calibration screen and in the Recalibrate dialog of the Pointer screen.
 */
@Composable
fun CornerCalibrationCard(vm: WandViewModel) {
    val context = LocalContext.current
    val profile by vm.calibrationProfile.collectAsState()
    val key by vm.profileKey.collectAsState()
    val connection by vm.connectionState.collectAsState()

    var step by remember { mutableIntStateOf(-1) } // -1 = not running, else 0..3
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val captured = remember { mutableListOf<Aim>() }
    val history = remember { AimHistory() }
    val running = step >= 0

    // A half-finished calibration is dropped whenever the screen is not visible.
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { step = -1 }
    }
    DisposableEffect(running) {
        var session: AimSensor.Session? = null
        if (running) {
            history.clear()
            session = vm.aim.open { timeNs, aim -> history.add(timeNs, aim) }
        }
        onDispose { session?.close() }
    }

    fun capture() {
        val aim = history.averageBefore(SystemClock.elapsedRealtimeNanos()) ?: return
        captured.add(aim)
        vm.haptics.pulse(30)
        if (captured.size < 4) {
            step = captured.size
            return
        }
        val calibration = ScreenCalibration.fromCorners(captured.toList())
        step = -1
        if (calibration == null) {
            failed = true
            message = "The corners were too close together. Try again, aiming at the real corners."
        } else {
            vm.saveCalibration(calibration)
            failed = false
            message = "Calibration saved."
        }
    }

    SectionCard("Screen corners") {
        val pcName = if (key == "default") "default (not connected to a PC)" else key
        Text("Profile: $pcName", style = MaterialTheme.typography.bodyMedium)
        val saved = profile
        Text(
            if (saved == null) "Not calibrated yet." else "Calibrated " + dateText(saved.savedAtMs, context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!connection.companionConnected) {
            Text(
                "Connect to the PC first to save the profile for that PC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!vm.aim.isAvailable) {
            Text("This phone has no rotation sensor, so absolute pointing is not possible.", color = MaterialTheme.colorScheme.error)
            return@SectionCard
        }

        if (running) {
            Text("Corner ${step + 1} of 4", style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { step / 4f }, modifier = Modifier.fillMaxWidth())
            Text(
                "Hold the phone like a remote and aim it at the ${CORNER_NAMES[step]} corner of your screen, then tap.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TouchDownButton("Tap: aimed at ${CORNER_NAMES[step]}", onDown = ::capture, modifier = Modifier.fillMaxWidth().height(96.dp))
            OutlinedButton(onClick = { step = -1 }) { Text("Cancel") }
        } else {
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    captured.clear()
                    message = null
                    step = 0
                }) { Text(if (saved == null) "Start" else "Recalibrate") }
                OutlinedButton(
                    onClick = {
                        vm.deleteCalibration()
                        message = null
                    },
                    enabled = saved != null,
                ) { Text("Delete") }
            }
        }
    }
}

private fun dateText(timeMs: Long, context: android.content.Context): String {
    if (timeMs == 0L) return "(date unknown)"
    return DateFormat.getMediumDateFormat(context).format(Date(timeMs)) + " " + DateFormat.getTimeFormat(context).format(Date(timeMs))
}
