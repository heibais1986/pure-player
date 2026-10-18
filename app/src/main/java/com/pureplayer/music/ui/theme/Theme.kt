package com.pureplayer.music.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF86EFAC),
    background = Color(0xFF0A0F0D),
    surface = Color(0xFF111815),
    surfaceVariant = Color(0xFF1A231E),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF166534),
    background = Color(0xFFF7FAF7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEAF3EA),
    onBackground = Color(0xFF0A0F0D),
    onSurface = Color(0xFF0A0F0D),
)

@Composable
fun PurePlayerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
