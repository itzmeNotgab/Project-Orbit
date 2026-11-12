package app.orbit.client.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.OrbitSettings

/**
 * Partial liquid-glass surface: translucent fill, thin border, soft top highlight, accent glow when active.
 * This is layered translucency, not real backdrop blur (which is costly on low-end GPUs).
 * With effects off (Settings, or auto on low-RAM devices) it falls back to a solid surface with a plain border.
 */
@Composable
fun Modifier.glass(shape: Shape, active: Boolean = false): Modifier {
    val accent = OrbitColors.Accent
    if (!OrbitSettings.effectsOn) {
        return this.background(OrbitColors.Surface, shape).border(1.dp, if (active) accent else Color(0x33FFFFFF), shape)
    }
    val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (active) 0.16f else 0.11f), Color.White.copy(alpha = 0.04f)))
    val edge = Brush.verticalGradient(listOf(if (active) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f)))
    return this
        .drawBehind {
            if (active) drawRoundRect(accent.copy(alpha = 0.20f), Offset(-6f, -6f), Size(size.width + 12f, size.height + 12f), CornerRadius(34f))
        }
        .background(fill, shape)
        .background(if (active) accent.copy(alpha = 0.14f) else Color.Transparent, shape)
        .border(1.dp, edge, shape)
}

@Composable
fun GlassButton(label: String, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && OrbitSettings.animations) 0.96f else 1f, tween(90), label = "press")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(20.dp), active)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) OrbitColors.Text else OrbitColors.Muted, fontSize = 15.sp) }
}

@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) = Text(
    label, fontSize = 13.sp, color = if (selected) OrbitColors.Text else OrbitColors.Muted, maxLines = 1,
    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    modifier = modifier.glass(RoundedCornerShape(16.dp), selected).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
)

@Composable
fun Btn(label: String, enabled: Boolean = true, onClick: () -> Unit) = Text(
    label, fontSize = 14.sp, color = if (enabled) OrbitColors.Accent else OrbitColors.Muted,
    modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(OrbitColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) { Text(title, color = OrbitColors.Muted, fontSize = 12.sp, letterSpacing = 1.sp); content() }

@Composable
fun Row2(k: String, v: String, vc: Color = OrbitColors.Text) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = OrbitColors.Muted, fontSize = 14.sp)
        Text(v, color = vc, fontSize = 14.sp)
    }

@Composable
fun Note(text: String) = Text(text, color = OrbitColors.Muted, fontSize = 12.sp)
