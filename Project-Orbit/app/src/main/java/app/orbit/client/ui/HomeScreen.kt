package app.orbit.client.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.AppEvents
import app.orbit.client.OrbitSettings
import app.orbit.client.detectMinecraft
import app.orbit.library.Versions

@Composable
fun HomeScreen(onLaunch: () -> Unit) {
    val ctx = LocalContext.current
    val resume = AppEvents.resume.intValue
    val mc = remember(resume) { detectMinecraft(ctx) }
    val target = OrbitSettings.targetVersion
    val installed = mc.installed
    val version = mc.versionName
    val matches = installed && version != null && Versions.sameRelease(version, target)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        OrbitLogo(150.dp)
        Text("ORBIT", color = OrbitColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
        Text("Beyond Vanilla", color = OrbitColors.Accent, fontSize = 13.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))

        Section("MINECRAFT") {
            Row2("Installation", if (installed) "Detected" else "Not installed", if (installed) OrbitColors.Ok else OrbitColors.Warn)
            if (installed) Row2("Version", version ?: "unknown")
            Row2("ORBIT target", target)
            if (installed) Row2("Target match", if (matches) "Same release" else "Different release", if (matches) OrbitColors.Ok else OrbitColors.Warn)
        }
        Section("CLIENT") {
            Row2("In-game client", "Not connected", OrbitColors.Warn)
            Note("ORBIT currently starts the unmodified game. In-game modules, ClickGUI and HUD editing need native game integration, which is not available yet.")
        }
        GlassButton(if (installed) "LAUNCH MINECRAFT" else "MINECRAFT NOT FOUND", active = installed, enabled = installed,
            modifier = Modifier.fillMaxWidth()) { onLaunch() }
        if (!installed) Note("Install Minecraft Bedrock from Google Play, then return to ORBIT.")
        else if (!matches) Note("Installed version differs from ORBIT's target ($target). Launching works; ORBIT makes no compatibility claims for other versions.")
        Note("Android ${Build.VERSION.RELEASE}, ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
    }
}
