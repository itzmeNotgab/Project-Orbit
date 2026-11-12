package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ResourcePack {
    /** Builds a standard Bedrock resource pack (.mcpack). [files] maps pack-relative paths to bytes. */
    fun build(name: String, description: String, files: Map<String, ByteArray>): ByteArray {
        require(files.keys.none { it.contains("..") || it.startsWith("/") }) { "bad path" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(path: String, data: ByteArray) { z.putNextEntry(ZipEntry(path)); z.write(data); z.closeEntry() }
            put("manifest.json", JSONObject().put("format_version", 2)
                .put("header", JSONObject().put("name", name).put("description", description).put("uuid", UUID.randomUUID().toString())
                    .put("version", JSONArray(listOf(1, 0, 0))).put("min_engine_version", JSONArray(listOf(1, 16, 0))))
                .put("modules", JSONArray().put(JSONObject().put("type", "resources").put("uuid", UUID.randomUUID().toString())
                    .put("version", JSONArray(listOf(1, 0, 0)))))
                .toString(2).toByteArray())
            files.forEach { (p, d) -> put(p, d) }
        }
        return out.toByteArray()
    }
}
