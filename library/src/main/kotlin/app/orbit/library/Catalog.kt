package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject

enum class Gallery(val label: String) { HEAD("Head"), BACK("Back"), CAPE("Cape"), PETS("Pets") }

/**
 * A wearable. [slot] is the body slot it occupies: one item per slot, except "other" which stacks.
 * IDs must match the builders in app/src/main/assets/viewer.html.
 */
class Cosmetic(val id: String, val name: String, val gallery: Gallery, val slot: String)

object Catalog {
    /** Slot -> (filter label, gallery). Order is display order. */
    val slots: List<Triple<String, String, Gallery>> = listOf(
        Triple("hat", "Hats", Gallery.HEAD), Triple("halo", "Halos", Gallery.HEAD), Triple("head", "Head accessories", Gallery.HEAD),
        Triple("wings", "Wings", Gallery.BACK), Triple("tail", "Tails", Gallery.BACK), Triple("back", "Back items", Gallery.BACK),
        Triple("shoulder", "Shoulder", Gallery.BACK), Triple("other", "Other", Gallery.BACK),
    )
    private val stackable = setOf("other")

    val cosmetics = listOf(
        c("hat_tophat", "Top Hat"), c("hat_crown", "Gold Crown"), c("hat_wizard", "Wizard Hat"),
        c("halo_ring", "Gold Halo"), c("halo_neon", "Neon Halo"),
        c("head_headphones", "Headphones"), c("head_antennae", "Antennae"), c("head_visor", "Visor"),
        c("wings_angel", "Angel Wings"), c("wings_bat", "Bat Wings"), c("wings_neon", "Neon Wings"),
        c("tail_fox", "Fox Tail"), c("tail_dragon", "Dragon Tail"), c("tail_cat", "Cat Tail"),
        c("back_backpack", "Backpack"), c("back_jetpack", "Jetpack"), c("back_sword", "Sheathed Sword"),
        c("shoulder_pads", "Shoulder Pads"), c("shoulder_spikes", "Spiked Pauldrons"), c("shoulder_parrot", "Parrot"),
        c("other_scarf", "Scarf"), c("other_orbit_ring", "Orbiting Planets"), c("other_belt", "Belt"),
    )

    private fun c(id: String, name: String): Cosmetic {
        val slot = id.substringBefore('_')
        val gallery = slots.first { it.first == slot }.third
        return Cosmetic(id, name, gallery, slot)
    }

    fun inGallery(g: Gallery) = cosmetics.filter { it.gallery == g }
    fun byId(id: String) = cosmetics.firstOrNull { it.id == id }

    /**
     * Equip/unequip with slot rules: wearing a second item in the same slot replaces the first
     * (you cannot wear two hats); different slots always combine. Unknown ids are ignored.
     */
    fun toggle(current: List<String>, id: String): List<String> {
        val item = byId(id) ?: return current
        if (id in current) return current - id
        val kept = if (item.slot in stackable) current else current.filter { byId(it)?.slot != item.slot }
        return kept + id
    }

    val pets = listOf("pet_cat" to "Cube Cat", "pet_slime" to "Jelly Slime", "pet_robot" to "Hover Bot", "pet_dragonling" to "Dragonling")
    val capes = listOf("orbit_nebula" to "Nebula", "orbit_ion" to "Ion", "orbit_ember" to "Ember", "orbit_aurora" to "Aurora")
    val anims = listOf("idle", "walk", "run", "wave")
    val cameras = listOf("free", "front", "back", "left", "right", "top", "close")
}

/** Renderer world units (the character is about 32 units tall). Original app: default 22, range 12-40. */
const val DEFAULT_PET_DISTANCE = 22.0
const val MIN_PET_DISTANCE = 12f
const val MAX_PET_DISTANCE = 40f

/** Everything the 3D viewer needs. */
data class ViewerState(
    val skinDataUri: String? = null, val slim: Boolean = false,
    val anim: String = "idle", val camera: String = "free",
    val cosmetics: List<String> = emptyList(),
    val capeEnabled: Boolean = true, val capeId: String = "orbit_nebula", val capeTexDataUri: String? = null,
    val capePhysics: Boolean = true, val capeStiffness: Double = 0.5, val capeSway: Double = 0.5,
    val petId: String? = null, val petDistance: Double = DEFAULT_PET_DISTANCE, val petVisible: Boolean = true,
    val accent: String = "#a855f7", val spin: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("skin", skinDataUri ?: JSONObject.NULL).put("slim", slim).put("anim", anim).put("camera", camera)
        .put("cosmetics", JSONArray(cosmetics))
        .put("cape", JSONObject().put("enabled", capeEnabled).put("id", capeId).put("tex", capeTexDataUri ?: JSONObject.NULL)
            .put("physics", capePhysics).put("stiffness", capeStiffness).put("sway", capeSway))
        .put("pet", JSONObject().put("id", petId ?: JSONObject.NULL).put("distance", petDistance).put("visible", petVisible))
        .put("accent", accent).put("spin", spin).toString()
}
