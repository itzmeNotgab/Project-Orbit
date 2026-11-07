package app.orbit.client.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.*
import app.orbit.library.*
import java.io.File

@Composable
fun LibraryScreen() {
    var part by rememberSaveable { mutableStateOf("Skins") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Skins", "Packs", "Worlds").forEach { Chip(it, part == it) { part = it } } }
        when (part) { "Skins" -> SkinsTab(); "Packs" -> PacksTab(); else -> WorldsTab() }
    }
}

@Composable
private fun SkinsTab() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var slim by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val active by AppStore.viewer.collectAsState()
    @Suppress("UNUSED_VARIABLE") val t = tick

    val png = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readCapped(ctx, uri, 256 * 1024)
            if (b == null || !Png.isValidSkin(b)) msg = "Not a valid skin: PNG, 64x64 or 64x32 (or larger multiples), max 256 KB."
            else { AppStore.addSkin("Skin ${AppStore.skins().size + 1}", b, slim); msg = "Skin added."; tick++ }
        }
    }
    val pack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readCapped(ctx, uri, Packs.MAX_PACK_BYTES)
            msg = if (b == null) "File too large." else runCatching {
                val r = SkinPack.read(b); r.skins.forEach { AppStore.addSkin(it.name, it.png, it.slim) }; tick++
                "Imported ${r.skins.size} skin(s)" + if (r.skipped > 0) ", skipped ${r.skipped} invalid." else "."
            }.getOrElse { it.message }
        }
    }

    Section("Import") {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Slim arms (Alex)", color = OrbitColors.Text, fontSize = 13.sp); Switch(slim, { slim = it })
        }
        Btn("Import skin PNG") { png.launch(arrayOf("image/png")) }
        Btn("Import skin pack (.mcpack)") { pack.launch(arrayOf("*/*")) }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val skins = AppStore.skins()
    Section("Your skins (${skins.size})") {
        if (skins.isEmpty()) Text("No skins yet.", color = OrbitColors.Muted, fontSize = 13.sp)
        skins.forEach { sk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(sk.name + if (sk.slim) " (slim)" else "", color = OrbitColors.Text, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Preview") { AppStore.useSkin(sk.id); tick++ }
                    Btn("Delete") { AppStore.deleteSkin(sk.id); tick++ }
                }
            }
        }
        if (active.skinDataUri != null) Btn("Preview default skin") { AppStore.useSkin(null); tick++ }
    }
    if (skins.isNotEmpty()) Section("Send to Minecraft") {
        Text("Builds a standard skin pack and opens it in Minecraft, which imports it itself. Equipping is done in Minecraft's Dressing Room.", color = OrbitColors.Muted, fontSize = 12.sp)
        Btn("Build .mcpack and open in Minecraft") {
            val f = File(AppStore.folder("packs"), "ORBIT_Skins.mcpack")
            runCatching {
                f.writeBytes(SkinPack.build("ORBIT Skins", skins.mapNotNull { s -> AppStore.skinBytes(s.id)?.let { SkinInput(s.name, it, s.slim) } }))
            }.onSuccess { msg = if (openInMinecraft(ctx, f)) "Opening Minecraft…" else "Could not open Minecraft (is it installed?)." }
                .onFailure { msg = it.message }
        }
    }
}

@Composable
private fun PacksTab() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "pack.mcpack"
            val b = readCapped(ctx, uri, Packs.MAX_PACK_BYTES)
            if (b == null) msg = "File larger than 60 MB; open it directly in Minecraft instead."
            else Packs.inspect(b, name).onSuccess { r ->
                File(AppStore.folder("packs"), name).writeBytes(b); tick++
                msg = "${r.kind.name.lowercase()}: " + r.packs.joinToString { "${it.name} ${it.version}" } +
                    r.warnings.joinToString("") { "\n⚠ $it" }
            }.onFailure { msg = it.message }
        }
    }
    Section("Add-ons and packs") {
        Text("Validates .mcpack / .mcaddon / .zip manifests. ORBIT cannot enable packs inside Minecraft; it hands the file over and Minecraft imports it.", color = OrbitColors.Muted, fontSize = 12.sp)
        Btn("Import and validate") { pick.launch(arrayOf("*/*")) }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val files = AppStore.folder("packs").listFiles()?.sortedBy { it.name } ?: emptyList()
    Section("Stored (${files.size})") {
        files.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(f.name, color = OrbitColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Btn("Open") { if (!openInMinecraft(ctx, f)) msg = "Could not open Minecraft." }
                Btn("Delete") { f.delete(); tick++ }
            }
        }
    }
}

@Composable
private fun WorldsTab() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readCapped(ctx, uri, Packs.MAX_WORLD_BYTES)
            if (b == null) msg = "World larger than 100 MB; open it directly in Minecraft instead."
            else Packs.inspectWorld(b).onSuccess { w ->
                val safe = w.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(40).ifBlank { "world" }
                File(AppStore.folder("worlds"), "$safe.mcworld").writeBytes(b); tick++; msg = "Imported “${w.name}”."
            }.onFailure { msg = it.message }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = exporting
        if (uri != null && f != null) msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(f.readBytes()) }; "Exported ${f.name}."
        }.getOrElse { it.message }
    }
    Section("Worlds") {
        Text("Android hides Minecraft's own world folder, so ORBIT can only manage .mcworld files you import. It cannot list or back up worlds already inside Minecraft.", color = OrbitColors.Muted, fontSize = 12.sp)
        Btn("Import .mcworld") { pick.launch(arrayOf("*/*")) }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val files = AppStore.folder("worlds").listFiles()?.sortedBy { it.name } ?: emptyList()
    Section("Stored (${files.size})") {
        files.forEach { f ->
            Text(f.name, color = OrbitColors.Text, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Open") { if (!openInMinecraft(ctx, f)) msg = "Could not open Minecraft." }
                Btn("Backup") {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                    f.copyTo(File(f.parentFile, f.nameWithoutExtension + "-backup-$stamp.mcworld")); tick++
                }
                Btn("Export") { exporting = f; export.launch(f.name) }
                Btn("Delete") { f.delete(); tick++ }
            }
        }
    }
}
