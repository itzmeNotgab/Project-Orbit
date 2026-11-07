package app.orbit.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object OrbitColors {
    val Bg = Color(0xFF0A0612)
    val Surface = Color(0xFF130C20)
    val Accent = Color(0xFFA855F7)
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
