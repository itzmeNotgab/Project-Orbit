package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SkinInput(val name: String, val png: ByteArray, val slim: Boolean)

object SkinPack {
    private fun id(name: String): String =
        name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(24).ifEmpty { "skin" }

    /** Builds a standard Bedrock skin pack (.mcpack). */
    fun build(packName: String, skins: List<SkinInput>): ByteArray {
        require(skins.isNotEmpty()) { "no skins" }
        val serialize = "Orbit_" + id(packName)
        val entries = JSONArray()
        val lang = StringBuilder("skinpack.$serialize=$packName\n")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(path: String, data: ByteArray) { z.putNextEntry(ZipEntry(path)); z.write(data); z.closeEntry() }
            skins.forEachIndexed { i, s ->
                val key = "skin${i + 1}"
                put("$key.png", s.png)
                entries.put(JSONObject().put("localization_name", key)
                    .put("geometry", if (s.slim) "geometry.humanoid.customSlim" else "geometry.humanoid.custom")
                    .put("texture", "$key.png").put("type", "free"))
                lang.append("skin.$serialize.$key=${s.name}\n")
            }
            put("skins.json", JSONObject().put("skins", entries).put("serialize_name", serialize)
                .put("localization_name", serialize).toString(2).toByteArray())
            put("manifest.json", JSONObject().put("format_version", 1)
                .put("header", JSONObject().put("name", packName).put("uuid", UUID.randomUUID().toString()).put("version", JSONArray(listOf(1, 0, 0))))
                .put("modules", JSONArray().put(JSONObject().put("type", "skin_pack").put("uuid", UUID.randomUUID().toString()).put("version", JSONArray(listOf(1, 0, 0)))))
                .toString(2).toByteArray())
            put("texts/en_US.lang", lang.toString().toByteArray())
            put("texts/languages.json", "[\"en_US\"]".toByteArray())
        }
        return out.toByteArray()
    }

    class ReadResult(val skins: List<SkinInput>, val skipped: Int)

    fun read(bytes: ByteArray): ReadResult {
        val files = Zip.readSelected(bytes) { it.endsWith("skins.json", true) || it.endsWith(".png", true) }
        val path = files.keys.firstOrNull { it.equals("skins.json", true) || it.endsWith("/skins.json", true) }
            ?: error("This pack has no skins.json, so it is not a skin pack.")
        val base = path.dropLast("skins.json".length)
        val json = JSONObject(Zip.text(files.getValue(path)))
        val arr = json.optJSONArray("skins") ?: JSONArray()
        val out = mutableListOf<SkinInput>(); var skipped = 0
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val png = files[base + s.optString("texture")]
            if (png == null || !Png.isValidSkin(png)) { skipped++; continue }
            out += SkinInput(s.optString("localization_name", "Skin"), png, s.optString("geometry").contains("slim", true))
        }
        return ReadResult(out, skipped)
    }
}

object Zip {
    const val MAX_ENTRY = 16 * 1024 * 1024
    fun text(b: ByteArray) = String(b, Charsets.UTF_8).removePrefix("\uFEFF")

    /** Reads only matching entries; rejects path traversal and oversize entries (zip-bomb guard). */
    fun readSelected(bytes: ByteArray, want: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name
                    if (e.isDirectory || n.contains("..") || !want(n)) continue
                    val buf = ByteArrayOutputStream(); val tmp = ByteArray(8192); var total = 0
                    while (true) {
                        val r = z.read(tmp); if (r < 0) break
                        total += r; if (total > MAX_ENTRY) error("entry too large: $n")
                        buf.write(tmp, 0, r)
                    }
                    out[n] = buf.toByteArray()
                }
            }
        } catch (e: java.util.zip.ZipException) { error("Not a valid zip archive.") }
        return out
    }
}
