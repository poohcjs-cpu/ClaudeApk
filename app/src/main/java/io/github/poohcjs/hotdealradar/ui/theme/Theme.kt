package io.github.poohcjs.hotdealradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFE5432D)
val Good = Color(0xFF1F9D5C)
val Warn = Color(0xFFD08A12)
val Bad = Color(0xFFD5402B)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3DD),
    onPrimaryContainer = Color(0xFF5A1206),
    secondary = Color(0xFF4F5B66),
    background = Color(0xFFF7F6F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EEEA),
    onSurfaceVariant = Color(0xFF5F5B55),
    outline = Color(0xFFD9D5CE),
    outlineVariant = Color(0xFFE9E6E0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A63),
    onPrimary = Color(0xFF3B0A02),
    primaryContainer = Color(0xFF5A1C10),
    onPrimaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF141312),
    surface = Color(0xFF1D1C1A),
    surfaceVariant = Color(0xFF2A2826),
    onSurfaceVariant = Color(0xFFBDB8B0),
    outline = Color(0xFF4A4642),
    outlineVariant = Color(0xFF33302D),
)

@Composable
fun HotdealTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
