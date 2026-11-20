package com.example.wandremote.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.wandremote.desktop.network.Server
import com.example.wandremote.desktop.ui.StatusWindow
import com.example.wandremote.desktop.ui.overlay.LaserOverlay
import com.example.wandremote.desktop.ui.viewer.ViewerWindow
import com.example.wandremote.desktop.ui.theme.WandRemoteTheme

/** Simple tray icon: a purple disc with a white dot. */
private object TrayIconPainter : Painter() {
    override val intrinsicSize = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF7C5CFF), radius = size.minDimension / 2)
        drawCircle(Color.White, radius = size.minDimension / 6, center = Offset(size.width * 0.5f, size.height * 0.5f))
    }
}

fun main() = application {
    val state = remember { AppState() }
    var windowVisible by remember { mutableStateOf(!state.startMinimized) }

    val server = remember { Server(state) }
    DisposableEffect(state.serverRunning) {
        if (state.serverRunning) server.start()
        onDispose { server.stop() }
    }

    // Open the built-in 3D viewer whenever the phone enters 3D mode (the user may close it afterwards).
    LaunchedEffect(state.mode, state.viewerBackend) {
        if (state.mode == ControlMode.THREE_D && state.viewerBackend == ViewerBackend.BUILT_IN) state.viewerOpen = true
    }

    Tray(
        icon = TrayIconPainter,
        tooltip = "WandRemote",
        onAction = { windowVisible = true },
        menu = {
            Item("Show window", onClick = { windowVisible = true })
            Item("Toggle laser overlay (test)", onClick = { state.laserVisible = !state.laserVisible })
            Item("3D viewer", onClick = { state.viewerOpen = true })
            Separator()
            Item("Quit", onClick = { server.stop(); exitApplication() })
        },
    )

    Window(
        onCloseRequest = { windowVisible = false },
        visible = windowVisible,
        title = "WandRemote",
        state = rememberWindowState(size = DpSize(720.dp, 520.dp)),
    ) {
        WandRemoteTheme { StatusWindow(state) }
    }

    WandRemoteTheme { LaserOverlay(state) }
    ViewerWindow(state)
}
