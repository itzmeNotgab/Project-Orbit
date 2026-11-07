package app.orbit.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.AppStore
import app.orbit.client.readCapped
import app.orbit.library.Catalog
import app.orbit.library.Png

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen() {
    val ctx = LocalContext.current
    val s by AppStore.viewer.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val capePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readCapped(ctx, uri, 256 * 1024)
            val i = b?.let { Png.info(it) }
            if (b == null || i == null || i.width < 22 || i.width < i.height) msg = "Not a valid cape PNG (max 256 KB, wider than tall)."
            else { AppStore.setCustomCape(b); msg = "Custom cape loaded." }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ViewerView(s)
        Section("Animation") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog.anims.forEach { a -> Chip(a, s.anim == a) { AppStore.update { it.copy(anim = a) } } } } }
        Section("Camera") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog.cameras.forEach { c -> Chip(c, s.camera == c) { AppStore.update { it.copy(camera = c) } } } } }
        Section("Cosmetics") {
            Catalog.categories.forEach { (label, key) ->
                Text(label, color = OrbitColors.Text, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Catalog.cosmetics.filter { it.category == key }.forEach { c ->
                        Chip(c.name, c.id in s.cosmetics) {
                            AppStore.update { v -> v.copy(cosmetics = if (c.id in v.cosmetics) v.cosmetics - c.id else v.cosmetics + c.id) }
                        }
                    }
                }
            }
        }
        Section("Cape") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", !s.capeEnabled) { AppStore.update { it.copy(capeEnabled = false) } }
                Catalog.capes.forEach { (id, name) -> Chip(name, s.capeEnabled && s.capeId == id) { AppStore.update { it.copy(capeEnabled = true, capeId = id) } } }
                if (s.capeTexDataUri != null) Chip("Custom", s.capeEnabled && s.capeId == "custom") { AppStore.update { it.copy(capeEnabled = true, capeId = "custom") } }
            }
            Btn("Import cape PNG") { capePicker.launch(arrayOf("image/png")) }
            if (s.capeTexDataUri != null) Btn("Remove custom cape") { AppStore.setCustomCape(null) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Cape physics", color = OrbitColors.Text, fontSize = 13.sp)
                Switch(s.capePhysics, { v -> AppStore.update { it.copy(capePhysics = v) } })
            }
            Text("Stiffness", color = OrbitColors.Muted, fontSize = 12.sp)
            Slider(s.capeStiffness.toFloat(), { v -> AppStore.update { it.copy(capeStiffness = v.toDouble()) } }, valueRange = 0f..1f)
            Text("Sway", color = OrbitColors.Muted, fontSize = 12.sp)
            Slider(s.capeSway.toFloat(), { v -> AppStore.update { it.copy(capeSway = v.toDouble()) } }, valueRange = 0f..1f)
            msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
        }
        Section("Pet (preview only)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", s.petId == null) { AppStore.update { it.copy(petId = null) } }
                Catalog.pets.forEach { (id, name) -> Chip(name, s.petId == id) { AppStore.update { it.copy(petId = id) } } }
            }
            Text("Distance", color = OrbitColors.Muted, fontSize = 12.sp)
            Slider(s.petDistance.toFloat(), { v -> AppStore.update { it.copy(petDistance = v.toDouble()) } }, valueRange = 0.5f..2f)
        }
        Section("Viewer") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Auto-rotate", color = OrbitColors.Text, fontSize = 13.sp)
                Switch(s.spin, { v -> AppStore.update { it.copy(spin = v) } })
            }
            Text("Cosmetics, capes and pets appear in this preview only. Vanilla Minecraft cannot show them.", color = OrbitColors.Muted, fontSize = 12.sp)
        }
    }
}
