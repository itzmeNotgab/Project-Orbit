package app.orbit.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.orbit.client.AppStore
import app.orbit.client.readAllBytes
import app.orbit.library.Catalog
import app.orbit.library.Gallery
import app.orbit.library.MAX_PET_DISTANCE
import app.orbit.library.MIN_PET_DISTANCE
import app.orbit.library.Png

@Composable
fun CustomizeScreen() {
    val s by AppStore.viewer.collectAsState()
    var gallery by rememberSaveable { mutableStateOf(Gallery.HEAD.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var showLoadouts by remember { mutableStateOf(false) }

    // Layout adapts to the space actually available (phones, tablets, landscape), never to a fixed screen height.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ViewerView(s, Modifier.weight(0.42f).fillMaxHeight())
                Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GalleryPanel(gallery, { gallery = it; query = "" }, query, { query = it }) { showLoadouts = true }
                }
            }
        } else {
            // Viewer gets ~42% of the height (enough for the whole character), but always leaves room for the controls.
            val viewerH = (maxHeight * 0.42f).coerceAtMost((maxHeight - 300.dp).coerceAtLeast(200.dp)).coerceAtLeast(200.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewerView(s, Modifier.fillMaxWidth().height(viewerH))
                GalleryPanel(gallery, { gallery = it; query = "" }, query, { query = it }) { showLoadouts = true }
            }
        }
    }
    if (showLoadouts) LoadoutsDialog { showLoadouts = false }
}

/** Gallery tabs, search + loadouts, and the scrollable gallery. Fills the rest of the column and never overflows it. */
@Composable
private fun ColumnScope.GalleryPanel(gallery: String, onGallery: (String) -> Unit, query: String, onQuery: (String) -> Unit, onLoadouts: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Gallery.entries.forEach { g -> Chip(g.label.uppercase(), gallery == g.name, Modifier.weight(1f)) { onGallery(g.name) } }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(query, onQuery, label = { Text("Search ${Gallery.valueOf(gallery).label.lowercase()}", maxLines = 1) }, singleLine = true, modifier = Modifier.weight(1f))
        Chip("Loadouts", false) { onLoadouts() }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (Gallery.valueOf(gallery)) {
            Gallery.HEAD, Gallery.BACK -> ItemGallery(Gallery.valueOf(gallery), query)
            Gallery.CAPE -> CapeGallery(query)
            Gallery.PETS -> PetGallery(query)
        }
    }
}

@Composable
private fun EmptyState(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Note(text) }

@Composable
private fun ItemGallery(g: Gallery, query: String) {
    val s by AppStore.viewer.collectAsState()
    var filter by rememberSaveable(g.name) { mutableStateOf("") }
    val slots = Catalog.slots.filter { it.third == g }
    val shown = Catalog.inGallery(g).filter { (filter.isEmpty() || it.slot == filter) && (query.isBlank() || it.name.contains(query, true)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("All", filter.isEmpty()) { filter = "" }
            slots.forEach { (slot, label, _) -> Chip(label, filter == slot) { filter = slot } }
        }
        if (shown.isEmpty()) EmptyState(if (query.isBlank()) "Nothing in this category." else "No cosmetics match “$query”.")
        else LazyVerticalGrid(GridCells.Adaptive(96.dp), contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { c ->
                ThumbCard(c.name, c.id in s.cosmetics, { AppStore.update { v -> v.copy(cosmetics = Catalog.toggle(v.cosmetics, c.id)) } }) { CosmeticThumb(c.id) }
            }
        }
        if (s.cosmetics.isNotEmpty()) Btn("Unequip all") { AppStore.update { it.copy(cosmetics = emptyList()) } }
    }
}

@Composable
private fun CapeGallery(query: String) {
    val ctx = LocalContext.current
    val s by AppStore.viewer.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readAllBytes(ctx, uri)
            if (b == null || !Png.isValidCape(b)) msg = "Not a valid cape: use a PNG that is wider than tall (vanilla capes are 64x32)."
            else { AppStore.setCustomCape(b); msg = "Custom cape loaded." }
        }
    }
    val entries = buildList {
        add("none" to "No cape")
        Catalog.capes.forEach { add(it.first to it.second) }
        if (s.capeTexDataUri != null) add("custom" to "Custom")
    }.filter { query.isBlank() || it.second.contains(query, true) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (entries.isEmpty()) Note("No capes match “$query”.")
            else FlowGrid(entries.size) { i ->
                val (id, name) = entries[i]
                val equipped = if (id == "none") !s.capeEnabled else s.capeEnabled && s.capeId == id
                ThumbCard(name, equipped, {
                    AppStore.update { if (id == "none") it.copy(capeEnabled = false) else it.copy(capeEnabled = true, capeId = id) }
                }) { if (id == "none") Note("—") else CapeThumb(id, s.capeTexDataUri) }
            }
        }
        item {
            Section("CAPE") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cape physics", color = OrbitColors.Text, fontSize = 14.sp)
                    Switch(s.capePhysics, { v -> AppStore.update { it.copy(capePhysics = v) } })
                }
                Note("Stiffness"); Slider(s.capeStiffness.toFloat(), { v -> AppStore.update { it.copy(capeStiffness = v.toDouble()) } }, valueRange = 0f..1f)
                Note("Sway"); Slider(s.capeSway.toFloat(), { v -> AppStore.update { it.copy(capeSway = v.toDouble()) } }, valueRange = 0f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Import cape PNG") { picker.launch(arrayOf("image/png")) }
                    if (s.capeTexDataUri != null) GlassButton("Remove custom") { AppStore.setCustomCape(null) }
                }
                msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
                Note("Capes are shown in ORBIT's preview. Vanilla Minecraft cannot display custom capes to other players.")
            }
        }
    }
}

@Composable
private fun PetGallery(query: String) {
    val s by AppStore.viewer.collectAsState()
    val entries = buildList { add("none" to "No pet"); addAll(Catalog.pets) }.filter { query.isBlank() || it.second.contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (entries.isEmpty()) Note("No pets match “$query”.")
            else FlowGrid(entries.size) { i ->
                val (id, name) = entries[i]
                ThumbCard(name, if (id == "none") s.petId == null else s.petId == id, { AppStore.update { it.copy(petId = if (id == "none") null else id) } }) {
                    if (id == "none") Note("—") else PetThumb(id)
                }
            }
        }
        item {
            Section("PET") {
                Note("Distance from player"); Slider(s.petDistance.toFloat(), { v -> AppStore.update { it.copy(petDistance = v.toDouble()) } }, valueRange = MIN_PET_DISTANCE..MAX_PET_DISTANCE)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Show pet in preview", color = OrbitColors.Text, fontSize = 14.sp)
                    Switch(s.petVisible, { v -> AppStore.update { it.copy(petVisible = v) } })
                }
                Note("Pets follow the character in the 3D preview. Walking beside you in Minecraft needs game integration, which is not implemented.")
            }
        }
    }
}

/** Non-lazy 3-column grid for use inside a LazyColumn item. */
@Composable
private fun FlowGrid(count: Int, cell: @Composable (Int) -> Unit) {
    val cols = 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until (count + cols - 1) / cols) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    Box(Modifier.weight(1f)) { if (i < count) cell(i) }
                }
            }
        }
    }
}

@Composable
private fun LoadoutsDialog(onDismiss: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val list = AppStore.loadouts()
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Loadouts", color = OrbitColors.Text, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                Btn("Save", name.isNotBlank()) { AppStore.saveLoadout(name); name = ""; tick++ }
            }
            Note("Saves your current cosmetics, cape and pet. Saving under an existing name replaces it.")
            if (list.isEmpty()) Note("No saved loadouts yet.")
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(list, key = { it.name }) { l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, color = OrbitColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Btn("Apply") { AppStore.applyLoadout(l); onDismiss() }
                        Btn("Delete") { AppStore.deleteLoadout(l.name); tick++ }
                    }
                }
            }
        }
    }
}
