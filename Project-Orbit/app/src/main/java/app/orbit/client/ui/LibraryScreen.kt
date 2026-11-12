package app.orbit.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.*
import app.orbit.library.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TABS = listOf("Addons", "Textures", "Skins", "Worlds")

@Composable
fun LibraryScreen() {
    var part by rememberSaveable { mutableStateOf(TABS[0]) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEach { Chip(it.uppercase(), part == it) { part = it } }
        }
        when (part) {
            "Addons" -> PacksTab("addons", "Add-ons (behavior packs, add-ons)", setOf(PackKind.ADDON, PackKind.BEHAVIOR))
            "Textures" -> PacksTab("textures", "Texture packs (resource packs)", setOf(PackKind.RESOURCE))
            "Skins" -> SkinsTab()
            else -> WorldsTab()
        }
    }
}

/** Streams the picked file into app storage, then inspects it from disk. Nothing is held in memory and there is no size cap. */
private fun importPack(ctx: android.content.Context, uri: android.net.Uri, accepted: Set<PackKind>, sub: String, installed: String?): String {
    val name = displayName(ctx, uri, "import.mcpack")
    val tmp = File(AppStore.folder("export"), "import.tmp")
    try {
        copyUriToFile(ctx, uri, tmp)
        val r = tmp.inputStream().use { Packs.inspect(it, name) }.getOrElse { return it.message ?: "Could not read the file." }
        if (r.kind == PackKind.SKIN) {
            val sk = SkinPack.read(tmp.readBytes()); sk.skins.forEach { AppStore.addSkin(it.name, it.png, it.slim) }
            return "That is a skin pack, so it was added to Skins (${sk.skins.size} skin(s))."
        }
        if (r.kind !in accepted) return "This is a ${r.kind.name.lowercase()} pack. Import it from the ${if (sub == "addons") "Textures" else "Addons"} tab."
        val dest = File(AppStore.folder(sub), name)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true) }
        val warn = Packs.compatibility(r, installed) + r.warnings
        AppStore.writeRecord(dest, r.packs.joinToString { "${it.name} ${it.version}" }, r.packs.map { Triple(it.name, it.version, it.minEngine) })
        return "Imported: " + r.packs.joinToString { "${it.name} ${it.version}" } + warn.joinToString("") { "\n⚠ $it" }
    } finally { tmp.delete() }
}

@Composable
private fun PacksTab(sub: String, title: String, accepted: Set<PackKind>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val installed = detectMinecraft(ctx).versionName

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; msg = "Checking file…"
            scope.launch {
                msg = withContext(Dispatchers.IO) { runCatching { importPack(ctx, uri, accepted, sub, installed) }.getOrElse { it.message ?: "Import failed." } }
                busy = false; tick++
            } }
    }
    Section(title.uppercase()) {
        Note("Each file is checked (manifest, type, required engine version) before it is added. A valid file is not proof it works in Minecraft; ORBIT hands it to Minecraft, which does the actual import.")
        GlassButton("Import .mcpack / .mcaddon / .zip", enabled = !busy) { pick.launch(arrayOf("*/*")) }
        if (sub == "textures") GlassButton("Create ORBIT branding pack", enabled = !busy) {
            msg = runCatching {
                val png = BrandingPack.renderTitlePng()
                val bytes = ResourcePack.build("ORBIT Branding", "ORBIT Nebula title logo", mapOf("textures/ui/title.png" to png))
                val f = File(AppStore.folder("textures"), "ORBIT_Branding.mcpack"); f.writeBytes(bytes)
                AppStore.writeRecord(f, "ORBIT Branding 1.0.0", listOf(Triple("ORBIT Branding", "1.0.0", "1.16.0"))); tick++
                "Created. Open it in Minecraft, then enable it under Settings > Global Resources. Untested: whether 1.26.60.4 uses this file for the title logo."
            }.getOrElse { it.message }
        }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val recs = AppStore.records(sub)
    Section("INSTALLED (${recs.size})") {
        if (recs.isEmpty()) Note("Nothing imported yet.")
        recs.forEach { r ->
            val issues = r.minEngines.mapNotNull { (n, m) ->
                if (installed == null) "$n needs engine $m; Minecraft not detected" else if (Versions.compare(m, installed) > 0) "$n needs engine $m, installed is $installed" else null
            }
            Column {
                Text(r.file.name, color = OrbitColors.Text, fontSize = 14.sp)
                Note(humanSize(r.file.length()) + if (r.summary.isNotEmpty()) " · ${r.summary}" else "")
                issues.forEach { Text("⚠ $it", color = OrbitColors.Warn, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("Open in Minecraft") { if (!openInMinecraft(ctx, r.file)) msg = "Could not open Minecraft (is it installed?)." }
                    Btn("Remove") { AppStore.deleteRecord(r); tick++ }
                }
            }
        }
    }
}

@Composable
private fun SkinsTab() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var slim by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick

    val png = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readAllBytes(ctx, uri)
            if (b == null || !Png.isValidSkin(b)) msg = "Not a valid skin: PNG, 64x64 or 64x32 (or whole-number multiples)."
            else { AppStore.addSkin(displayName(ctx, uri, "Skin ${AppStore.skins().size + 1}").substringBeforeLast('.'), b, slim); msg = "Skin added."; tick++ }
        }
    }
    val pack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b = readAllBytes(ctx, uri)
            msg = if (b == null) "File does not fit in memory." else runCatching {
                val r = SkinPack.read(b); r.skins.forEach { AppStore.addSkin(it.name, it.png, it.slim) }; tick++
                "Imported ${r.skins.size} skin(s)" + if (r.skipped > 0) ", skipped ${r.skipped} invalid." else "."
            }.getOrElse { it.message }
        }
    }
    Section("IMPORT") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Slim arms (Alex)", color = OrbitColors.Text, fontSize = 14.sp); Switch(slim, { slim = it })
        }
        GlassButton("Import skin PNG") { png.launch(arrayOf("image/png")) }
        GlassButton("Import skin pack (.mcpack)") { pack.launch(arrayOf("*/*")) }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val skins = AppStore.skins()
    Section("SKINS (${skins.size})") {
        if (skins.isEmpty()) Note("No skins yet.")
        skins.forEach { sk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sk.name + if (sk.slim) " (slim)" else "", color = OrbitColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Btn("Preview") { AppStore.useSkin(sk.id); tick++ }
                Btn("Delete") { AppStore.deleteSkin(sk.id); tick++ }
            }
        }
        if (AppStore.viewer.value.skinDataUri != null) Btn("Use default skin in preview") { AppStore.useSkin(null); tick++ }
    }
    if (skins.isNotEmpty()) Section("SEND TO MINECRAFT") {
        Note("Builds a standard skin pack and opens it in Minecraft, which imports it. Equip it in Minecraft's Dressing Room.")
        GlassButton("Build .mcpack and open") {
            val f = File(AppStore.folder("export"), "ORBIT_Skins.mcpack")
            runCatching { f.writeBytes(SkinPack.build("ORBIT Skins", skins.mapNotNull { s -> AppStore.skinBytes(s.id)?.let { SkinInput(s.name, it, s.slim) } })) }
                .onSuccess { msg = if (openInMinecraft(ctx, f)) "Opening Minecraft…" else "Could not open Minecraft (is it installed?)." }
                .onFailure { msg = it.message }
        }
    }
}

@Composable
private fun WorldsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<File?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; msg = "Checking world…"
            scope.launch {
                msg = withContext(Dispatchers.IO) {
                    val tmp = File(AppStore.folder("export"), "world.tmp")
                    try {
                        copyUriToFile(ctx, uri, tmp)
                        tmp.inputStream().use { Packs.inspectWorld(it) }.fold({ w ->
                            val safe = w.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "world" }
                            tmp.copyTo(File(AppStore.folder("worlds"), "$safe.mcworld"), overwrite = true); "Imported “${w.name}”."
                        }, { it.message ?: "Not a valid world." })
                    } catch (e: Exception) { e.message ?: "Import failed." } finally { tmp.delete() }
                }
                busy = false; tick++
            } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = exporting
        if (uri != null && f != null) msg = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }; "Exported ${f.name}."
        }.getOrElse { it.message }
    }
    Section("WORLDS") {
        Note("Android hides Minecraft's own world folder, so ORBIT manages .mcworld files you import. It cannot list or back up worlds that are already inside Minecraft.")
        GlassButton("Import .mcworld", enabled = !busy) { pick.launch(arrayOf("*/*")) }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 12.sp) }
    }
    val files = AppStore.folder("worlds").listFiles()?.sortedBy { it.name } ?: emptyList()
    Section("STORED (${files.size})") {
        if (files.isEmpty()) Note("No worlds imported yet.")
        files.forEach { f ->
            Text(f.name, color = OrbitColors.Text, fontSize = 14.sp)
            Note(humanSize(f.length()))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Open in Minecraft") { if (!openInMinecraft(ctx, f)) msg = "Could not open Minecraft." }
                Btn("Backup") {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                    scope.launch(Dispatchers.IO) { f.copyTo(File(f.parentFile, f.nameWithoutExtension + "-backup-$stamp.mcworld")); tick++ }
                }
                Btn("Export") { exporting = f; export.launch(f.name) }
                Btn("Delete") { f.delete(); tick++ }
            }
        }
    }
}
