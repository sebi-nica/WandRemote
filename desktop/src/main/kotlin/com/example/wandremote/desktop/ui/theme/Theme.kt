package com.example.wandremote.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4A5FF),
    onPrimary = Color(0xFF2A1A80),
    primaryContainer = Color(0xFF4130B0),
    secondary = Color(0xFF5CE1E6),
    background = Color(0xFF14131A),
    surface = Color(0xFF14131A),
    surfaceVariant = Color(0xFF2A2833),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7C5CFF),
    secondary = Color(0xFF00838A),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
)

@Composable
fun WandRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
