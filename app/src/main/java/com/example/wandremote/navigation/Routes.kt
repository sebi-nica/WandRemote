package com.example.wandremote.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val CONNECT = "connect"
    const val POINTER = "pointer"
    const val CONFIG = "config"
    const val PRESENTER = "presenter"
    const val CALIBRATION = "calibration"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"

    fun title(route: String?): String = when (route) {
        POINTER -> "Pointer"
        CONFIG -> "Config"
        PRESENTER -> "Presenter"
        CALIBRATION -> "Calibration"
        SETTINGS -> "Settings"
        DEBUG -> "Sensor debug"
        else -> "WandRemote"
    }
}

enum class ModeDestination(val route: String, val label: String, val icon: ImageVector) {
    Pointer(Routes.POINTER, "Pointer", Icons.Default.Mouse),
    Config(Routes.CONFIG, "Config", Icons.Default.Tune),
    Presenter(Routes.PRESENTER, "Presenter", Icons.Default.Slideshow),
}
