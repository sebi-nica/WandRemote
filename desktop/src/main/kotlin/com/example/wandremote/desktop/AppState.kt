package com.example.wandremote.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.wandremote.shared.Gesture
import java.net.Inet4Address
import java.net.NetworkInterface

enum class ControlMode(val label: String) { POINTER("Pointer"), THREE_D("3D"), PRESENTER("Presenter") }

enum class ViewerBackend(val label: String) { BUILT_IN("Built-in preview"), BLENDER("Blender emulation") }

/** Observable holder for everything the UI shows. Written from background threads only via the Swing EDT. */
class AppState {
    var phoneName by mutableStateOf<String?>(null)
    var latencyMs by mutableStateOf(-1) // -1 = unknown
    var packetRate by mutableStateOf(0)
    var mode by mutableStateOf(ControlMode.POINTER)
    var serverRunning by mutableStateOf(true)
    var serverError by mutableStateOf<String?>(null)
    /** Latest phone orientation quaternion (w, x, y, z). */
    var orientation by mutableStateOf(floatArrayOf(1f, 0f, 0f, 0f))
    /** 3D viewer zoom factor (1 = default), accumulated from Zoom packets. */
    var zoom by mutableStateOf(1f)
    /** Whether the separate "WandRemote 3D" window is open. */
    var viewerOpen by mutableStateOf(false)
    val localIp: String = findLocalIpv4()

    var laserVisible by mutableStateOf(false)
    var laserX by mutableStateOf(0.5f)
    var laserY by mutableStateOf(0.5f)

    var startMinimized by mutableStateOf(false)
    var overlayOnPresenter by mutableStateOf(true)
    var viewerBackend by mutableStateOf(ViewerBackend.BUILT_IN)

    val gestureActions = mutableStateMapOf(
        Gesture.FLICK_LEFT to "Left arrow",
        Gesture.FLICK_RIGHT to "Right arrow",
        Gesture.BACK_TAP to "Left click",
        Gesture.DOUBLE_BACK_TAP to "Double click",
        Gesture.SHAKE to "Ctrl+Z",
    )
}

val SHORTCUT_OPTIONS = listOf(
    "None", "Left arrow", "Right arrow", "Up arrow", "Down arrow", "Page Up", "Page Down",
    "Left click", "Right click", "Double click", "Ctrl+Z", "Ctrl+Y", "Esc", "F5",
)

/** First non-loopback, up, site-local IPv4 address; falls back to 127.0.0.1. */
fun findLocalIpv4(): String = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress ?: "127.0.0.1"
} catch (e: Exception) {
    "127.0.0.1"
}
