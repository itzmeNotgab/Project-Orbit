package app.orbit.client.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import app.orbit.client.HudOverlayService
import app.orbit.core.ClientCore
import app.orbit.core.HudConfig
import kotlin.math.roundToInt

@Composable
fun HudScreen(core: ClientCore) {
    val ctx = LocalContext.current
    val h = core.hud
    var tick by remember { mutableIntStateOf(0) }
    var overlayOn by remember { mutableStateOf(HudOverlayService.running) }
    var box by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val lines = core.modules.all.filter { it.enabled }.flatMap { it.hudLines() }.ifEmpty { listOf("(enable modules to preview)") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("HUD editor", color = OrbitColors.Text, fontSize = 24.sp)
        Section("Preview (drag to move)") {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF05030A), RoundedCornerShape(6.dp))
                .onSizeChanged { box = IntOffset(it.width, it.height) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (box.x > 0) core.hud.update(x = h.x + drag.x / box.x, y = h.y + drag.y / box.y)
                        tick++
                    }
                }) {
                Column(Modifier.offset { IntOffset((h.x * box.x).roundToInt(), (h.y * box.y).roundToInt()) }) {
                    if (h.visible) lines.forEach {
                        Text(it, color = Color(android.graphics.Color.parseColor(h.color)).copy(alpha = h.opacity), fontSize = (11f * h.scale).sp)
                    }
                }
            }
        }
        Section("Widget") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Visible", color = OrbitColors.Text, fontSize = 13.sp); Switch(h.visible, { h.update(visible = it); tick++ })
            }
            Text("Size ${"%.1f".format(h.scale)}x", color = OrbitColors.Muted, fontSize = 12.sp)
            Slider(h.scale, { h.update(scale = it); tick++ }, valueRange = 0.5f..2f)
            Text("Opacity ${(h.opacity * 100).toInt()}%", color = OrbitColors.Muted, fontSize = 12.sp)
            Slider(h.opacity, { h.update(opacity = it); tick++ }, valueRange = 0.2f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HudConfig.COLORS.forEach { c ->
                    Box(Modifier.size(if (c == h.color) 34.dp else 28.dp)
                        .background(Color(android.graphics.Color.parseColor(c)), RoundedCornerShape(17.dp))
                        .pointerInput(c) { detectTapGestures { h.update(color = c); tick++ } })
                }
            }
            Btn("Restore defaults") { h.restoreDefaults(); tick++ }
        }
        Section("Overlay over Minecraft") {
            Text("Draws this HUD in a separate Android window above other apps. It is not Minecraft's own renderer, cannot be touched, and only shows modules with data (e.g. network stats while the relay runs).",
                color = OrbitColors.Muted, fontSize = 12.sp)
            if (!Settings.canDrawOverlays(ctx)) Btn("Grant “draw over other apps”") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Overlay on", color = OrbitColors.Text, fontSize = 13.sp)
                Switch(overlayOn, { on -> if (on) HudOverlayService.start(ctx) else HudOverlayService.stop(ctx); overlayOn = on })
            }
        }
    }
}
