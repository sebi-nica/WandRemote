package com.example.wandremote.desktop.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.example.wandremote.desktop.AppState
import java.awt.GraphicsEnvironment

/**
 * Transparent, undecorated, always-on-top, non-focusable window covering the primary screen,
 * drawing a glowing red dot at (laserX, laserY), normalized 0..1.
 * Mouse click-through is not implemented (would need native WS_EX_TRANSPARENT).
 */
@Composable
fun LaserOverlay(state: AppState) {
    if (!state.laserVisible) return
    val bounds = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    }
    val windowState = rememberWindowState(
        position = WindowPosition(bounds.x.dp, bounds.y.dp),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )
    Window(
        onCloseRequest = { state.laserVisible = false },
        state = windowState,
        title = "WandRemote laser",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * state.laserX, size.height * state.laserY)
            val glow = 28.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Red.copy(alpha = 0.6f), Color.Transparent),
                    center = center,
                    radius = glow,
                ),
                radius = glow,
                center = center,
            )
            drawCircle(Color.Red, radius = 7.dp.toPx(), center = center)
            drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.5.dp.toPx(), center = center)
        }
    }
}
