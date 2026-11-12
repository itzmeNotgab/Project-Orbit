package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

enum class PackKind { ADDON, BEHAVIOR, RESOURCE, SKIN }
class PackInfo(val name: String, val description: String, val uuid: String, val version: String,
               val minEngine: String?, val kind: PackKind, val moduleTypes: List<String>)
class InspectResult(val kind: PackKind, val packs: List<PackInfo>, val warnings: List<String>)
class WorldInfo(val name: String)

object Packs {
    private val MANIFEST = Regex("(^|/)manifest\\.json$", RegexOption.IGNORE_CASE)
    private const val MAX_TEXT_ENTRY = 4 * 1024 * 1024   // a manifest/level.dat bigger than this is not real (zip-bomb guard)
    private const val MAX_NESTING = 2

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

    private class NoClose(i: InputStream) : FilterInputStream(i) { override fun close() {} }

    private fun readLimited(z: InputStream, what: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0
        while (true) {
            val r = z.read(buf); if (r < 0) break
            total += r; if (total > MAX_TEXT_ENTRY) error("$what is implausibly large")
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    /** Streams the archive (and nested .mcpack/.zip inside it); only manifests are ever held in memory. */
    private fun scan(stream: InputStream, depth: Int, prefix: String, manifests: MutableMap<String, ByteArray>, warnings: MutableList<String>) {
        val z = ZipInputStream(stream)
        while (true) {
            val e = z.nextEntry ?: break
            val n = e.name
            if (e.isDirectory || n.contains("..")) continue
            when {
                MANIFEST.containsMatchIn(n) -> manifests[prefix + n] = readLimited(z, n)
                depth < MAX_NESTING && (n.endsWith(".mcpack", true) || n.endsWith(".zip", true)) ->
                    try { scan(NoClose(z), depth + 1, "$prefix$n!/", manifests, warnings) } catch (ex: Exception) { warnings += "Could not open nested archive $n" }
            }
        }
    }

    private fun collect(files: Map<String, ByteArray>, warnings: MutableList<String>): List<PackInfo> {
        val out = mutableListOf<PackInfo>()
        for ((path, data) in files) {
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

    fun inspect(bytes: ByteArray, fileName: String): Result<InspectResult> = inspect(bytes.inputStream(), fileName)

    fun inspect(stream: InputStream, fileName: String): Result<InspectResult> = runCatching {
        val warnings = mutableListOf<String>(); val manifests = LinkedHashMap<String, ByteArray>()
        try { scan(stream, 0, "", manifests, warnings) } catch (e: ZipException) { error("Not a valid zip archive.") }
        val packs = collect(manifests, warnings)
        require(packs.isNotEmpty()) { "No valid manifest.json found. This does not look like a Bedrock pack." }
        val addon = fileName.endsWith(".mcaddon", true) || packs.size > 1
        InspectResult(if (addon) PackKind.ADDON else packs[0].kind, packs, warnings)
    }

    /** Messages about whether the pack can work with [installedVersion]. Empty = no known problem. */
    fun compatibility(info: InspectResult, installedVersion: String?): List<String> {
        val msgs = mutableListOf<String>()
        for (p in info.packs) {
            val min = p.minEngine ?: continue
            if (installedVersion == null) { msgs += "${p.name} needs engine $min or newer; Minecraft was not detected."; continue }
            if (Versions.compare(min, installedVersion) > 0) msgs += "${p.name} needs engine $min or newer; installed is $installedVersion."
        }
        return msgs
    }

    fun inspectWorld(bytes: ByteArray): Result<WorldInfo> = inspectWorld(bytes.inputStream())

    fun inspectWorld(stream: InputStream): Result<WorldInfo> = runCatching {
        var hasLevel = false; var name: String? = null
        try {
            val z = ZipInputStream(stream)
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (e.isDirectory || n.contains("..")) continue
                if (n.equals("level.dat", true) || n.endsWith("/level.dat", true)) hasLevel = true
                else if (n.endsWith("levelname.txt", true)) name = Zip.text(readLimited(z, n)).trim()
            }
        } catch (e: ZipException) { error("Not a valid zip archive.") }
        require(hasLevel) { "No level.dat found. This does not look like a Bedrock world." }
        WorldInfo(name?.ifEmpty { null } ?: "Unnamed world")
    }
}
