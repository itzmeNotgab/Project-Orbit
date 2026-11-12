package app.orbit.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.orbit.client.OrbitSettings
import app.orbit.client.launchMinecraft
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { Home("HOME"), Customize("CUSTOMIZE"), Library("LIBRARY"), Settings("SETTINGS") }

@Composable
fun OrbitApp() = OrbitTheme {
    val ctx = LocalContext.current
    var showIntro by rememberSaveable { mutableStateOf(OrbitSettings.showIntro) }
    var launching by remember { mutableStateOf(false) }
    val fade = if (OrbitSettings.animations) 300 else 0

    Box(Modifier.fillMaxSize().background(OrbitColors.Bg)) {
        if (OrbitSettings.dots) DotsBackground()
        AnimatedContent(showIntro, transitionSpec = { fadeIn(tween(fade)) togetherWith fadeOut(tween(fade)) }, label = "root") { intro ->
            if (intro) Intro { showIntro = false } else Main(onLaunch = { launching = true })
        }
        if (launching) LaunchSplash {
            launchMinecraft(ctx)
            launching = false
        }
    }
}

@Composable
private fun Intro(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(if (OrbitSettings.animations) 2200 else 600); onDone() }
    Box(Modifier.fillMaxSize().clickable { onDone() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbitLogo(120.dp)
            Spacer(Modifier.height(20.dp))
            Text("ORBIT", color = OrbitColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
            Text("Beyond Vanilla", color = OrbitColors.Accent, fontSize = 13.sp, letterSpacing = 3.sp)
        }
        Text("tap to skip", color = OrbitColors.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
    }
}

/** ORBIT's own launch transition shown before the game starts. It does not alter Minecraft's screens. */
@Composable
private fun LaunchSplash(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (OrbitSettings.animations) { a.animateTo(1f, tween(700)); delay(250) } else a.snapTo(1f)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(OrbitColors.Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.alpha(a.value).scale(0.9f + 0.1f * a.value), horizontalAlignment = Alignment.CenterHorizontally) {
            OrbitLogo(160.dp)
            Spacer(Modifier.height(16.dp))
            Text("ORBIT", color = OrbitColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
            Text("Starting Minecraft…", color = OrbitColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Main(onLaunch: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showAccount by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ORBIT", color = OrbitColors.Muted, fontSize = 14.sp, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            AccountButton { showAccount = true }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Home -> HomeScreen(onLaunch)
                Tab.Customize -> CustomizeScreen()
                Tab.Library -> LibraryScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tab.entries.forEach {
                Box(Modifier.weight(1f).glass(RoundedCornerShape(18.dp), it == tab).clickable { tab = it }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(it.label, fontSize = 11.sp, letterSpacing = 1.sp, color = if (it == tab) OrbitColors.Text else OrbitColors.Muted)
                }
            }
        }
    }
    if (showAccount) AccountMenu { showAccount = false }
}
