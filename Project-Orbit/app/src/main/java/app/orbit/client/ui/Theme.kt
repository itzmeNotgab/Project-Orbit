package app.orbit.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.orbit.client.OrbitSettings

/** Colors follow the selected theme; reads are Compose state so changing the theme recomposes the UI. */
object OrbitColors {
    val Bg: Color get() = OrbitSettings.themeDef.bg
    val Surface: Color get() = OrbitSettings.themeDef.surface
    val Accent: Color get() = OrbitSettings.themeDef.accent
    val Text = Color(0xFFEDE7F6)
    val Muted = Color(0xFF8C7FA3)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
}

@Composable
fun OrbitTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = OrbitColors.Accent, background = OrbitColors.Bg, surface = OrbitColors.Surface,
        onPrimary = Color.White, onBackground = OrbitColors.Text, onSurface = OrbitColors.Text,
    ),
    content = content,
)
