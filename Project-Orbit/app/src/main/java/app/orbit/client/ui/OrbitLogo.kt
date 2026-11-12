package app.orbit.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** ORBIT Nebula mark: a planet with a tilted ring inside a soft nebula glow. */
@Composable
fun OrbitLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val accent = OrbitColors.Accent
    Canvas(modifier.size(size)) {
        val c = center
        val m = this.size.minDimension
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f)), c, m * 0.5f), m * 0.5f, c)
        drawCircle(Brush.radialGradient(listOf(OrbitColors.Text.copy(alpha = 0.12f), OrbitColors.Text.copy(alpha = 0f)),
            Offset(c.x + m * 0.18f, c.y - m * 0.16f), m * 0.28f), m * 0.28f, Offset(c.x + m * 0.18f, c.y - m * 0.16f))
        drawCircle(accent, m * 0.22f, c)
        drawCircle(Brush.radialGradient(listOf(OrbitColors.Text.copy(alpha = 0.55f), accent.copy(alpha = 0f)),
            Offset(c.x - m * 0.07f, c.y - m * 0.08f), m * 0.16f), m * 0.16f, Offset(c.x - m * 0.07f, c.y - m * 0.08f))
        rotate(-22f, c) {
            val w = m * 0.86f; val h = w * 0.28f
            drawOval(OrbitColors.Text.copy(alpha = 0.85f), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), style = Stroke(width = m * 0.022f))
        }
    }
}
