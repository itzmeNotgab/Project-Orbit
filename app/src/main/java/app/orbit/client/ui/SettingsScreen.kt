package app.orbit.client.ui

import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.*
import app.orbit.library.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OrbitColors.Text, fontSize = 14.sp); Switch(checked, onChange)
    }

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val resume = AppEvents.resume.intValue
    val mc = remember(resume) { detectMinecraft(ctx) }
    val sizes by produceState<List<Pair<String, Long>>?>(null, resume) {
        value = withContext(Dispatchers.IO) {
            listOf("Skins" to "skins", "Add-ons" to "addons", "Texture packs" to "textures", "Worlds" to "worlds").map { (l, d) -> l to AppStore.dirSize(AppStore.folder(d)) }
        }
    }
    val free = remember(resume) { runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(-1L) }
    val pkg = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val target = OrbitSettings.targetVersion

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = OrbitColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Light)

        Section("APPEARANCE") {
            Note("Theme")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEMES.forEach { Chip(it.name, OrbitSettings.theme == it.name) { OrbitSettings.theme = it.name } }
            }
            Note("Glass effects")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "on" to "On", "off" to "Off").forEach { (k, l) -> Chip(l, OrbitSettings.glass == k) { OrbitSettings.glass = k } }
            }
            Note("Auto turns glass off on low-RAM devices. Off uses plain solid surfaces. Effects are layered translucency, not real blur.")
            ToggleRow("Background dots", OrbitSettings.dots) { OrbitSettings.dots = it }
            ToggleRow("Show intro on launch", OrbitSettings.showIntro) { OrbitSettings.showIntro = it }
        }
        Section("ANIMATIONS & PERFORMANCE") {
            ToggleRow("Animations", OrbitSettings.animations) { OrbitSettings.animations = it }
            Row2("Low-RAM device", if (OrbitSettings.lowRamDevice) "Yes" else "No")
            Row2("Glass effects active", if (OrbitSettings.effectsOn) "Yes" else "No")
            Note("For the smoothest experience on weaker phones: glass Off, background dots off, animations off.")
        }
        Section("STORAGE") {
            val list = sizes
            if (list == null) Note("Calculating…") else {
                list.forEach { (l, b) -> Row2(l, humanSize(b)) }
                Row2("ORBIT total", humanSize(list.sumOf { it.second }))
            }
            if (free >= 0) Row2("Free on device", humanSize(free))
        }
        Section("COMPATIBILITY") {
            Row2("Minecraft", if (mc.installed) "Installed" else "Not installed", if (mc.installed) OrbitColors.Ok else OrbitColors.Warn)
            if (mc.installed) Row2("Installed version", mc.versionName ?: "unknown")
            OutlinedTextField(target, { OrbitSettings.targetVersion = it.trim() }, label = { Text("Target version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val v = mc.versionName
            if (mc.installed && v != null && target.isNotBlank()) {
                val same = Versions.sameRelease(v, target)
                Row2("Matches target", if (same) "Same release" else "Different release", if (same) OrbitColors.Ok else OrbitColors.Warn)
            }
            Row2("Native engine", "Not loaded", OrbitColors.Warn)
            Row2("Hook data for target", "None", OrbitColors.Warn)
            Row2("In-game modules", "Blocked", OrbitColors.Warn)
            Note("The target version is a setting so future releases need no app update. Nothing is assumed to work on a version just because it is set here. In-game features stay blocked until verified hook data exists for the exact version.")
        }
        Section("3D VIEWER") {
            Row2("WebView", ViewerDiagnostics.webViewVersion)
            Row2("Page loaded", if (ViewerDiagnostics.pageLoaded) "Yes" else "No", if (ViewerDiagnostics.pageLoaded) OrbitColors.Ok else OrbitColors.Warn)
            Row2("Bridge ready", if (ViewerDiagnostics.bridgeReady) "Yes" else "No", if (ViewerDiagnostics.bridgeReady) OrbitColors.Ok else OrbitColors.Warn)
            Row2("First frame drawn", ViewerDiagnostics.firstDraw ?: "No", if (ViewerDiagnostics.firstDraw != null) OrbitColors.Ok else OrbitColors.Warn)
            Row2("States sent", ViewerDiagnostics.stateSends.toString())
            Row2("Last error", ViewerDiagnostics.lastError ?: "None", if (ViewerDiagnostics.lastError == null) OrbitColors.Ok else OrbitColors.Warn)
            Note("Open the Customize tab first, then check here. Details are also in logcat under the tag OrbitViewer.")
        }
        Section("ACCOUNT") {
            Row2("ORBIT account", AccountState.user ?: "Signed out")
            Note("Use the profile button at the top right to sign in or out. Microsoft sign-in is not implemented.")
        }
        Section("ABOUT") {
            Row2("ORBIT", pkg?.versionName ?: "?")
            Row2("Package", ctx.packageName)
            Row2("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Row2("Device ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
            Note("Beyond Vanilla. ORBIT is independent software and is not affiliated with Mojang or Microsoft.")
        }
    }
}
