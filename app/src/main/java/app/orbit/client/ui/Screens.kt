package app.orbit.client.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.*
import app.orbit.core.ClientCore
import app.orbit.core.EngineState

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    Text(title, color = OrbitColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Light)
    content()
}

@Composable
private fun Row2(k: String, v: String, vc: androidx.compose.ui.graphics.Color = OrbitColors.Text) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = OrbitColors.Muted, fontSize = 14.sp)
        Text(v, color = vc, fontSize = 14.sp)
    }

@Composable
fun HomeScreen(core: ClientCore) {
    val ctx = LocalContext.current
    val mc = remember { detectMinecraft(ctx) }
    val engine by core.engine.collectAsState()
    Page("ORBIT") {
        Text("Beyond Vanilla", color = OrbitColors.Accent, fontSize = 14.sp)
        Row2("Minecraft", if (mc.installed) "installed ${mc.versionName}" else "not installed",
            if (mc.installed) OrbitColors.Ok else OrbitColors.Warn)
        Row2("Target version", TARGET_MINECRAFT)
        Row2("Networking engine", engine.name, if (engine == EngineState.Running) OrbitColors.Ok else OrbitColors.Warn)
        if (mc.installed) {
            Text("Open Minecraft", color = OrbitColors.Accent, fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp).clickableNoRipple { launchMinecraft(ctx) })
        }
    }
}

@Composable
fun DiagnosticsScreen(core: ClientCore) {
    val ctx = LocalContext.current
    val mc = remember { detectMinecraft(ctx) }
    val engine by core.engine.collectAsState()
    val matches = mc.installed && mc.versionName?.startsWith(TARGET_MINECRAFT.substringBeforeLast('.')) == true
    Page("Diagnostics") {
        Row2("Android", "API ${Build.VERSION.SDK_INT}")
        Row2("Device ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
        Row2("Installed Minecraft", mc.versionName ?: "none")
        Row2("Matches target line", if (matches) "yes" else "no / unknown",
            if (matches) OrbitColors.Ok else OrbitColors.Warn)
        Row2("Relay", if (engine == EngineState.Running) "listening on 127.0.0.1:${RelayController.LISTEN_PORT}" else "stopped",
            if (engine == EngineState.Running) OrbitColors.Ok else OrbitColors.Warn)
        Row2("Engine state", engine.name)
        Text("Protocol compatibility with ${TARGET_MINECRAFT} has not been verified.",
            color = OrbitColors.Muted, fontSize = 12.sp)
    }
}

@Composable
fun LimitsScreen() = Page("Limits") {
    listOf(
        "ORBIT cannot inject into Minecraft. Android forbids it and ORBIT does not try.",
        "Minecraft traffic is not redirected automatically. You must add 127.0.0.1:19133 as a server inside Minecraft.",
        "Compose UI is a separate app window or overlay, not an internal Minecraft renderer.",
        "The relay forwards raw UDP datagrams only. It cannot read Bedrock packets yet (Phase 3). Authentication and modules are not built.",
    ).forEach { Text("• $it", color = OrbitColors.Text, fontSize = 14.sp) }
}
