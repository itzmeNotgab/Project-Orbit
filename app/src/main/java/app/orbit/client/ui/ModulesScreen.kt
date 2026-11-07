package app.orbit.client.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.core.*
import kotlinx.coroutines.delay

/** In-app module list (ClickGUI-style). This is NOT an in-game overlay. */
@Composable
fun ModulesScreen(core: ClientCore) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("All") }
    var open by rememberSaveable { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val t = tick

    val all = core.modules.all
    val shown = all.filter {
        (cat == "All" || it.category.name == cat) &&
            (query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true))
    }.sortedByDescending { core.modules.isFavorite(it) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val hud = all.filter { it.enabled }.flatMap { it.hudLines() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Modules", color = OrbitColors.Text, fontSize = 24.sp)
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (listOf("All") + Category.entries.map { it.name }).forEach {
                Text(it, fontSize = 12.sp, color = if (it == cat) OrbitColors.Accent else OrbitColors.Muted,
                    modifier = Modifier.clickable { cat = it }.padding(vertical = 6.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("HUD preview (in-app only)", color = OrbitColors.Muted, fontSize = 11.sp)
            if (hud.isEmpty()) Text("Enable a HUD or performance module. Some show data only while the relay runs.",
                color = OrbitColors.Muted, fontSize = 12.sp)
            hud.forEach { Text(it, color = OrbitColors.Text, fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("Profiles", color = OrbitColors.Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(profileName, { profileName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                Text("Save", color = OrbitColors.Accent, modifier = Modifier.clickable {
                    note = runCatching { core.modules.saveProfile(profileName.trim()); "Saved." }.getOrElse { it.message }; tick++ }.padding(8.dp))
            }
            core.modules.profiles().forEach { p ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(p, color = OrbitColors.Text, fontSize = 13.sp)
                    Text("Load", color = OrbitColors.Accent, fontSize = 13.sp, modifier = Modifier.clickable { core.modules.loadProfile(p); tick++ })
                    Text("Delete", color = OrbitColors.Muted, fontSize = 13.sp, modifier = Modifier.clickable { core.modules.deleteProfile(p); tick++ })
                }
            }
            note?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.name }) { m ->
                Column(Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { open = if (open == m.name) "" else m.name }) {
                            Text(m.name, color = OrbitColors.Text, fontSize = 15.sp)
                            Text(m.description, color = OrbitColors.Muted, fontSize = 12.sp)
                            m.lastError?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
                        }
                        Text(if (core.modules.isFavorite(m)) "★" else "☆", color = OrbitColors.Accent, fontSize = 20.sp,
                            modifier = Modifier.clickable { core.modules.toggleFavorite(m); tick++ }.padding(horizontal = 10.dp))
                        Switch(m.enabled, { core.modules.setEnabled(m, it); tick++ })
                    }
                    if (open == m.name) {
                        if (m.settings.isEmpty()) Text("No settings.", color = OrbitColors.Muted, fontSize = 12.sp)
                        m.settings.forEach { s -> SettingRow(s) { core.modules.save(); tick++ } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(s: Setting<*>, changed: () -> Unit) {
    when (s) {
        is BoolSetting -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(s.name, color = OrbitColors.Text, fontSize = 13.sp)
            Switch(s.value, { s.value = it; changed() })
        }
        is FloatSetting -> Column {
            Text("${s.name}: ${s.value.toInt()}", color = OrbitColors.Text, fontSize = 13.sp)
            Slider(s.value, { s.value = it; changed() }, valueRange = s.min..s.max)
        }
        is ChoiceSetting -> Text("${s.name}: ${s.value} (tap)", color = OrbitColors.Text, fontSize = 13.sp,
            modifier = Modifier.clickable { s.value = s.options[(s.options.indexOf(s.value) + 1) % s.options.size]; changed() }.padding(vertical = 8.dp))
    }
}
