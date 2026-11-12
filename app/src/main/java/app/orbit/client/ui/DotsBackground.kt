package app.orbit.client.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/** Sparse drifting dots. Fixed 36 points, one animated float: cheap on low-end GPUs. */
@Composable
fun DotsBackground(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.fillMaxSize()) {
        for (i in 0 until 36) {
            val bx = ((i * 37) % 100) / 100f
            val by = ((i * 61) % 100) / 100f
            val phase = t * 2f * PI.toFloat() + i
            val x = (bx + 0.015f * sin(phase)) * size.width
            val y = (by + 0.015f * sin(phase * 0.8f)) * size.height
            val a = 0.15f + 0.2f * ((sin(phase * 1.3f) + 1f) / 2f)
            drawCircle(OrbitColors.Accent.copy(alpha = a), radius = 2.5f, center = Offset(x, y))
        }
    }
}
