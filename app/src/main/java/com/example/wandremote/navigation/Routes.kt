package com.example.wandremote.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val CONNECT = "connect"
    const val POINTER = "pointer"
    const val CONFIG = "config"
    const val THREE_D = "threed"
    const val CALIBRATION = "calibration"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"

    fun title(route: String?): String = when (route) {
        POINTER -> "Pointer"
        CONFIG -> "Config"
        THREE_D -> "3D"
        CALIBRATION -> "Calibration"
        SETTINGS -> "Settings"
        DEBUG -> "Sensor debug"
        else -> "WandRemote"
    }
}

enum class ModeDestination(val route: String, val label: String, val icon: ImageVector) {
    Pointer(Routes.POINTER, "Pointer", Icons.Default.Mouse),
    ThreeD(Routes.THREE_D, "3D", Icons.Default.ViewInAr),
    Config(Routes.CONFIG, "Config", Icons.Default.Tune),
}
