package app.orbit.client

import android.content.Context
import android.util.Base64
import app.orbit.library.DEFAULT_PET_DISTANCE
import app.orbit.library.Loadouts
import app.orbit.library.SavedLoadout
import app.orbit.library.ViewerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local library: skins, loadout, imported packs and worlds. Everything lives in the app's private storage. */
object AppStore {
    class SkinEntry(val id: String, val name: String, val slim: Boolean)

    private lateinit var dir: File
    val viewer = MutableStateFlow(ViewerState())
    private var activeSkinId: String? = null

    fun init(ctx: Context) {
        dir = ctx.filesDir
        listOf("skins", "addons", "textures", "worlds", "export").forEach { File(dir, it).mkdirs() }
        loadViewer()
    }

    fun folder(name: String) = File(dir, name).also { it.mkdirs() }
    fun dataUri(png: ByteArray) = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)

    // ---- skins ----
    private val index get() = File(dir, "skins/index.json")
    fun skins(): List<SkinEntry> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> SkinEntry(o.getString("id"), o.getString("name"), o.getBoolean("slim")) } }
    }.getOrDefault(emptyList())

    private fun writeSkins(l: List<SkinEntry>) =
        index.writeText(JSONArray(l.map { JSONObject().put("id", it.id).put("name", it.name).put("slim", it.slim) }).toString())

    fun addSkin(name: String, png: ByteArray, slim: Boolean): SkinEntry {
        val e = SkinEntry(UUID.randomUUID().toString().take(8), name.take(40), slim)
        File(dir, "skins/${e.id}.png").writeBytes(png)
        writeSkins(skins() + e); return e
    }

    fun skinBytes(id: String): ByteArray? = File(dir, "skins/$id.png").takeIf { it.exists() }?.readBytes()

    fun deleteSkin(id: String) {
        File(dir, "skins/$id.png").delete(); writeSkins(skins().filter { it.id != id })
        if (activeSkinId == id) useSkin(null)
    }

    fun useSkin(id: String?) {
        activeSkinId = id
        val e = skins().firstOrNull { it.id == id }
        val png = id?.let { skinBytes(it) }
        update { it.copy(skinDataUri = png?.let(::dataUri), slim = e?.slim ?: false) }
    }

    // ---- loadout / viewer ----
    fun update(f: (ViewerState) -> ViewerState) { viewer.value = f(viewer.value); saveViewer() }

    fun setCustomCape(png: ByteArray?) {
        val f = File(dir, "cape.png")
        if (png == null) f.delete() else f.writeBytes(png)
        update { it.copy(capeTexDataUri = png?.let(::dataUri), capeId = if (png != null) "custom" else it.capeId) }
    }

    private fun saveViewer() = runCatching {
        val v = viewer.value
        File(dir, "loadout.json").writeText(JSONObject()
            .put("skinId", activeSkinId ?: JSONObject.NULL).put("anim", v.anim).put("camera", v.camera)
            .put("cosmetics", JSONArray(v.cosmetics)).put("capeEnabled", v.capeEnabled).put("capeId", v.capeId)
            .put("capePhysics", v.capePhysics).put("capeStiffness", v.capeStiffness).put("capeSway", v.capeSway)
            .put("petId", v.petId ?: JSONObject.NULL).put("petDistance", v.petDistance).put("petVisible", v.petVisible)
            .put("spin", v.spin).toString())
    }

    private fun loadViewer() {
        runCatching {
            val o = JSONObject(File(dir, "loadout.json").readText())
            val c = o.getJSONArray("cosmetics")
            var s = ViewerState(
                anim = o.optString("anim", "idle"), camera = o.optString("camera", "free"),
                cosmetics = (0 until c.length()).map { c.getString(it) },
                capeEnabled = o.optBoolean("capeEnabled", true), capeId = o.optString("capeId", "orbit_nebula"),
                capePhysics = o.optBoolean("capePhysics", true), capeStiffness = o.optDouble("capeStiffness", 0.5),
                capeSway = o.optDouble("capeSway", 0.5), petId = o.optString("petId").ifEmpty { null },
                petDistance = o.optDouble("petDistance", DEFAULT_PET_DISTANCE).let { if (it < 5.0) DEFAULT_PET_DISTANCE else it }, petVisible = o.optBoolean("petVisible", true), spin = o.optBoolean("spin", false))
            File(dir, "cape.png").takeIf { it.exists() }?.let { s = s.copy(capeTexDataUri = dataUri(it.readBytes())) }
            viewer.value = s
            activeSkinId = o.optString("skinId").ifEmpty { null }
        }
        useSkin(activeSkinId.takeIf { id -> skins().any { it.id == id } })
    }

    // ---- saved loadouts (unlimited) ----
    private val loadoutFile get() = File(dir, "loadouts.json")
    fun loadouts(): List<SavedLoadout> = runCatching { Loadouts.fromJson(loadoutFile.readText()) }.getOrDefault(emptyList())
    fun saveLoadout(name: String) {
        val n = name.trim(); if (n.isEmpty()) return
        loadoutFile.writeText(Loadouts.toJson(Loadouts.upsert(loadouts(), Loadouts.fromState(n, viewer.value))))
    }
    fun deleteLoadout(name: String) { loadoutFile.writeText(Loadouts.toJson(loadouts().filter { it.name != name })) }
    fun applyLoadout(l: SavedLoadout) { update { Loadouts.apply(l, it) } }

    // ---- imported content records (sidecar next to each file) ----
    class PackRecord(val file: File, val summary: String, val minEngines: List<Pair<String, String>>)

    fun writeRecord(file: File, summary: String, packs: List<Triple<String, String, String?>>) {
        val arr = JSONArray(packs.map { JSONObject().put("name", it.first).put("version", it.second).put("minEngine", it.third ?: JSONObject.NULL) })
        File(file.path + ".orbit.json").writeText(JSONObject().put("summary", summary).put("packs", arr).toString())
    }

    fun records(sub: String): List<PackRecord> =
        folder(sub).listFiles { f -> !f.name.endsWith(".orbit.json") }?.sortedBy { it.name }?.map { f ->
            val side = File(f.path + ".orbit.json")
            runCatching {
                val o = JSONObject(side.readText()); val a = o.getJSONArray("packs")
                PackRecord(f, o.optString("summary"), (0 until a.length()).mapNotNull { i ->
                    a.getJSONObject(i).let { p -> p.optString("minEngine").takeIf { it.isNotEmpty() && it != "null" }?.let { m -> p.getString("name") to m } } })
            }.getOrElse { PackRecord(f, "", emptyList()) }
        } ?: emptyList()

    fun deleteRecord(r: PackRecord) { r.file.delete(); File(r.file.path + ".orbit.json").delete() }

    fun dirSize(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun filesDirRoot(): File = dir
}
