package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject

enum class PackKind { ADDON, BEHAVIOR, RESOURCE, SKIN }
class PackInfo(val name: String, val description: String, val uuid: String, val version: String,
               val minEngine: String?, val kind: PackKind, val moduleTypes: List<String>)
class InspectResult(val kind: PackKind, val packs: List<PackInfo>, val warnings: List<String>)
class WorldInfo(val name: String)

object Packs {
    const val MAX_PACK_BYTES = 60 * 1024 * 1024
    const val MAX_WORLD_BYTES = 100 * 1024 * 1024
    private val MANIFEST = Regex("(^|/)manifest\\.json$", RegexOption.IGNORE_CASE)

    /** Minecraft manifests may contain comments and trailing commas. */
    fun parseLoose(text: String): JSONObject {
        val t = text.removePrefix("\uFEFF")
        return try { JSONObject(t) } catch (e: Exception) {
            JSONObject(t.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                .replace(Regex("(?m)(^|[^:\"'])//.*$"), "$1")
                .replace(Regex(",(\\s*[}\\]])"), "$1"))
        }
    }

    private fun version(v: Any?): String = when (v) {
        is JSONArray -> (0 until v.length()).joinToString(".") { v.get(it).toString() }
        is String -> v else -> "0"
    }

    private fun collect(files: Map<String, ByteArray>, warnings: MutableList<String>): List<PackInfo> {
        val out = mutableListOf<PackInfo>()
        for ((path, data) in files) {
            if (!MANIFEST.containsMatchIn(path)) continue
            val m = try { parseLoose(Zip.text(data)) } catch (e: Exception) { warnings += "Could not read $path"; continue }
            val h = m.optJSONObject("header")
            if (h == null || h.optString("uuid").isEmpty()) { warnings += "$path has no header uuid"; continue }
            val mods = m.optJSONArray("modules")?.let { a -> (0 until a.length()).map { a.optJSONObject(it)?.optString("type").orEmpty() } } ?: emptyList()
            val kind = when {
                "skin_pack" in mods -> PackKind.SKIN
                "resources" in mods -> PackKind.RESOURCE
                "data" in mods || "script" in mods -> PackKind.BEHAVIOR
                else -> { warnings += "$path has no supported module type (${mods.joinToString().ifEmpty { "none" }})"; continue }
            }
            out += PackInfo(h.optString("name", "Unnamed pack"), h.optString("description"), h.getString("uuid"),
                version(h.opt("version")), if (h.has("min_engine_version")) version(h.opt("min_engine_version")) else null, kind, mods)
        }
        return out
    }

    fun inspect(bytes: ByteArray, fileName: String): Result<InspectResult> = runCatching {
        require(bytes.size <= MAX_PACK_BYTES) { "File is larger than 60 MB; ORBIT will not load it into memory to inspect it." }
        val warnings = mutableListOf<String>()
        val files = Zip.readSelected(bytes) { MANIFEST.containsMatchIn(it) || it.endsWith(".mcpack", true) || it.endsWith(".zip", true) }
        val packs = collect(files, warnings).toMutableList()
        for ((path, data) in files) if (path.endsWith(".mcpack", true) || path.endsWith(".zip", true)) {
            try { packs += collect(Zip.readSelected(data) { MANIFEST.containsMatchIn(it) }, warnings) }
            catch (e: Exception) { warnings += "Could not open nested archive $path" }
        }
        require(packs.isNotEmpty()) { "No valid manifest.json found. This does not look like a Bedrock pack." }
        val addon = fileName.endsWith(".mcaddon", true) || packs.size > 1
        InspectResult(if (addon) PackKind.ADDON else packs[0].kind, packs, warnings)
    }

    fun inspectWorld(bytes: ByteArray): Result<WorldInfo> = runCatching {
        require(bytes.size <= MAX_WORLD_BYTES) { "World is larger than 100 MB; open it directly in Minecraft instead." }
        val files = Zip.readSelected(bytes) { it.endsWith("level.dat", true) || it.endsWith("levelname.txt", true) }
        require(files.keys.any { it.equals("level.dat", true) || it.endsWith("/level.dat", true) }) {
            "No level.dat found. This does not look like a Bedrock world."
        }
        val name = files.entries.firstOrNull { it.key.endsWith("levelname.txt", true) }?.let { Zip.text(it.value).trim() }
        WorldInfo(name?.ifEmpty { null } ?: "Unnamed world")
    }
}
