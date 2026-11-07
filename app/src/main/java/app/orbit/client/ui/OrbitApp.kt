package app.orbit.client.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.core.ClientCore
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { Home("Home"), Relay("Relay"), Modules("Modules"), Customize("Customize"), Library("Library"), Hud("HUD"), Account("Account"), Diagnostics("Diagnostics"), About("Limits") }

@Composable
fun OrbitApp(core: ClientCore) = OrbitTheme {
    var showIntro by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(OrbitColors.Bg)) {
        DotsBackground()
        AnimatedContent(showIntro, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { intro ->
            if (intro) Intro { showIntro = false } else Main(core)
        }
    }
}

@Composable
private fun Intro(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2200); onDone() }
    Box(Modifier.fillMaxSize().clickable { onDone() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbitLogo(120.dp)
            Spacer(Modifier.height(20.dp))
            Text("ORBIT", color = OrbitColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
            Text("Beyond Vanilla", color = OrbitColors.Accent, fontSize = 13.sp, letterSpacing = 3.sp)
        }
        Text("tap to skip", color = OrbitColors.Muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
    }
}

@Composable
private fun Main(core: ClientCore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Home -> HomeScreen(core)
                Tab.Relay -> RelayScreen()
                Tab.Modules -> ModulesScreen(core)
                Tab.Customize -> CustomizeScreen()
                Tab.Library -> LibraryScreen()
                Tab.Hud -> HudScreen(core)
                Tab.Account -> AccountScreen()
                Tab.Diagnostics -> DiagnosticsScreen(core)
                Tab.About -> LimitsScreen()
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach {
                Text(it.label, fontSize = 14.sp,
                    color = if (it == tab) OrbitColors.Accent else OrbitColors.Muted,
                    modifier = Modifier.clickable { tab = it }.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}
