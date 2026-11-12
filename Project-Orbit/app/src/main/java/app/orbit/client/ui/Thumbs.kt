package app.orbit.client.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** A gallery tile: schematic thumbnail, name, equipped indicator. */
@Composable
fun ThumbCard(label: String, equipped: Boolean, onClick: () -> Unit, thumb: @Composable BoxScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), equipped).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            thumb()
            if (equipped) Text("EQUIPPED", color = OrbitColors.Text, fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopEnd).glass(RoundedCornerShape(8.dp), true).padding(horizontal = 5.dp, vertical = 2.dp))
        }
        Text(label, color = if (equipped) OrbitColors.Text else OrbitColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun tint(id: String): Color = Color.hsv((abs(id.hashCode()) % 360).toFloat(), 0.55f, 0.95f)

/** Simple drawn icon per slot. These are schematic icons, not renders of the 3D model. */
@Composable
fun CosmeticThumb(id: String) {
    val slot = id.substringBefore('_'); val c = tint(id)
    Canvas(Modifier.fillMaxSize(0.8f)) {
        val w = size.width; val h = size.height
        when (slot) {
            "hat" -> { drawRoundRect(c, Offset(w * .08f, h * .68f), Size(w * .84f, h * .1f), CornerRadius(8f)); drawRoundRect(c.copy(alpha = .85f), Offset(w * .25f, h * .2f), Size(w * .5f, h * .5f), CornerRadius(10f)) }
            "halo" -> drawOval(c, Offset(w * .12f, h * .3f), Size(w * .76f, h * .26f), style = Stroke(w * .07f))
            "head" -> { drawArc(c, 180f, 180f, false, Offset(w * .12f, h * .15f), Size(w * .76f, h * .7f), style = Stroke(w * .08f)); drawCircle(c, w * .09f, Offset(w * .12f, h * .55f)); drawCircle(c, w * .09f, Offset(w * .88f, h * .55f)) }
            "wings" -> wing(c, true)
            "tail" -> { val p = Path().apply { moveTo(w * .2f, h * .8f); cubicTo(w * .9f, h * .9f, w * .9f, h * .3f, w * .5f, h * .15f) }; drawPath(p, c, style = Stroke(w * .12f)) }
            "back" -> drawRoundRect(c, Offset(w * .22f, h * .15f), Size(w * .56f, h * .7f), CornerRadius(14f))
            "shoulder" -> { drawCircle(c, w * .26f, Offset(w * .5f, h * .55f)); drawCircle(c.copy(alpha = .6f), w * .12f, Offset(w * .5f, h * .3f)) }
            else -> drawOval(c, Offset(w * .1f, h * .35f), Size(w * .8f, h * .3f), style = Stroke(w * .06f))
        }
    }
}

private fun DrawScope.wing(c: Color, both: Boolean) {
    val w = size.width; val h = size.height
    for (dir in if (both) listOf(-1f, 1f) else listOf(1f)) {
        val p = Path().apply {
            moveTo(w * .5f, h * .55f); lineTo(w * (.5f + dir * .42f), h * .12f); lineTo(w * (.5f + dir * .36f), h * .5f); lineTo(w * (.5f + dir * .28f), h * .85f); close()
        }
        drawPath(p, c)
    }
}

val CAPE_COLORS = mapOf(
    "orbit_nebula" to (Color(0xFF7C3AED) to Color(0xFF1E1B4B)), "orbit_ion" to (Color(0xFF38BDF8) to Color(0xFF0C4A6E)),
    "orbit_ember" to (Color(0xFFFB923C) to Color(0xFF7F1D1D)), "orbit_aurora" to (Color(0xFF4ADE80) to Color(0xFF134E4A)),
)

@Composable
fun CapeThumb(capeId: String, customDataUri: String?) {
    val bmp = remember(customDataUri) {
        if (capeId == "custom" && customDataUri != null) runCatching {
            val b = Base64.decode(customDataUri.substringAfter("base64,"), 0)
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        }.getOrNull() else null
    }
    Canvas(Modifier.fillMaxHeight(0.9f).aspectRatio(0.6f)) {
        if (bmp != null) drawImage(bmp, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
        else { val (a, b) = CAPE_COLORS[capeId] ?: (Color.Gray to Color.DarkGray); drawRoundRect(Brush.verticalGradient(listOf(a, b)), cornerRadius = CornerRadius(10f)) }
    }
}

@Composable
fun PetThumb(id: String) {
    val c = tint(id)
    Canvas(Modifier.fillMaxSize(0.7f)) {
        val w = size.width; val h = size.height
        drawRoundRect(c, Offset(w * .15f, h * .3f), Size(w * .7f, h * .55f), CornerRadius(14f))
        drawCircle(Color.Black.copy(alpha = .7f), w * .06f, Offset(w * .36f, h * .52f)); drawCircle(Color.Black.copy(alpha = .7f), w * .06f, Offset(w * .64f, h * .52f))
        if (id == "pet_cat") { drawPath(Path().apply { moveTo(w * .18f, h * .32f); lineTo(w * .28f, h * .1f); lineTo(w * .4f, h * .32f); close() }, c); drawPath(Path().apply { moveTo(w * .6f, h * .32f); lineTo(w * .72f, h * .1f); lineTo(w * .82f, h * .32f); close() }, c) }
    }
}
