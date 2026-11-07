package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject

/** IDs must match the builders in app/src/main/assets/viewer.html. */
class Cosmetic(val id: String, val name: String, val category: String)

object Catalog {
    val categories = listOf("Hats" to "hats", "Halos" to "halos", "Head" to "head", "Wings" to "wings",
        "Tails" to "tails", "Back" to "back", "Shoulder" to "shoulder", "Other" to "other")
    val cosmetics = listOf(
        Cosmetic("hat_tophat", "Top Hat", "hats"), Cosmetic("hat_crown", "Gold Crown", "hats"), Cosmetic("hat_wizard", "Wizard Hat", "hats"),
        Cosmetic("halo_ring", "Gold Halo", "halos"), Cosmetic("halo_neon", "Neon Halo", "halos"),
        Cosmetic("head_headphones", "Headphones", "head"), Cosmetic("head_antennae", "Antennae", "head"), Cosmetic("head_visor", "Visor", "head"),
        Cosmetic("wings_angel", "Angel Wings", "wings"), Cosmetic("wings_bat", "Bat Wings", "wings"), Cosmetic("wings_neon", "Neon Wings", "wings"),
        Cosmetic("tail_fox", "Fox Tail", "tails"), Cosmetic("tail_dragon", "Dragon Tail", "tails"), Cosmetic("tail_cat", "Cat Tail", "tails"),
        Cosmetic("back_backpack", "Backpack", "back"), Cosmetic("back_jetpack", "Jetpack", "back"), Cosmetic("back_sword", "Sheathed Sword", "back"),
        Cosmetic("shoulder_pads", "Shoulder Pads", "shoulder"), Cosmetic("shoulder_spikes", "Spiked Pauldrons", "shoulder"), Cosmetic("shoulder_parrot", "Parrot", "shoulder"),
        Cosmetic("other_scarf", "Scarf", "other"), Cosmetic("other_orbit_ring", "Orbiting Planets", "other"), Cosmetic("other_belt", "Belt", "other"),
    )
    val pets = listOf("pet_cat" to "Cube Cat", "pet_slime" to "Jelly Slime", "pet_robot" to "Hover Bot", "pet_dragonling" to "Dragonling")
    val capes = listOf("orbit_nebula" to "Nebula", "orbit_ion" to "Ion", "orbit_ember" to "Ember", "orbit_aurora" to "Aurora")
    val anims = listOf("idle", "walk", "run", "wave")
    val cameras = listOf("free", "front", "back", "left", "right", "top", "close")
}

/** Everything the 3D viewer needs. Mirrors the legacy ViewerState. */
data class ViewerState(
    val skinDataUri: String? = null, val slim: Boolean = false,
    val anim: String = "idle", val camera: String = "free",
    val cosmetics: List<String> = emptyList(),
    val capeEnabled: Boolean = true, val capeId: String = "orbit_nebula", val capeTexDataUri: String? = null,
    val capePhysics: Boolean = true, val capeStiffness: Double = 0.5, val capeSway: Double = 0.5,
    val petId: String? = null, val petDistance: Double = 1.0, val petVisible: Boolean = true,
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
