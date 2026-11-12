package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject

/** A named cosmetic selection. There is no limit on how many can be saved. */
data class SavedLoadout(
    val name: String, val cosmetics: List<String>, val capeEnabled: Boolean, val capeId: String,
    val capePhysics: Boolean, val petId: String?,
)

object Loadouts {
    fun fromState(name: String, s: ViewerState) = SavedLoadout(name, s.cosmetics, s.capeEnabled, s.capeId, s.capePhysics, s.petId)

    fun apply(l: SavedLoadout, s: ViewerState) = s.copy(
        cosmetics = l.cosmetics.filter { Catalog.byId(it) != null }, capeEnabled = l.capeEnabled, capeId = l.capeId,
        capePhysics = l.capePhysics, petId = l.petId,
    )

    /** Saving under an existing name replaces it. */
    fun upsert(list: List<SavedLoadout>, l: SavedLoadout) = list.filter { it.name != l.name } + l

    fun toJson(list: List<SavedLoadout>): String = JSONArray(list.map {
        JSONObject().put("name", it.name).put("cosmetics", JSONArray(it.cosmetics)).put("capeEnabled", it.capeEnabled)
            .put("capeId", it.capeId).put("capePhysics", it.capePhysics).put("petId", it.petId ?: JSONObject.NULL)
    }).toString()

    fun fromJson(text: String): List<SavedLoadout> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { return@mapNotNull null }
            val c = o.optJSONArray("cosmetics") ?: JSONArray()
            SavedLoadout(name, (0 until c.length()).map { c.getString(it) }, o.optBoolean("capeEnabled", true),
                o.optString("capeId", "orbit_nebula"), o.optBoolean("capePhysics", true), o.optString("petId").ifEmpty { null })
        }
    } catch (e: Exception) { emptyList() }
}
