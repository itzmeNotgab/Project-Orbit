package app.orbit.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Planet with a tilted ring. */
@Composable
fun OrbitLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension * 0.28f
        drawCircle(OrbitColors.Accent, r, c)
        rotate(-22f, c) {
            val w = this.size.minDimension * 0.9f
            val h = w * 0.3f
            drawOval(
                OrbitColors.Text.copy(alpha = 0.85f),
                topLeft = Offset(c.x - w / 2, c.y - h / 2),
                size = Size(w, h),
                style = Stroke(width = this.size.minDimension * 0.025f),
            )
        }
    }
}
