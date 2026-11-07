package app.orbit.core

/** One HUD block (all enabled modules' lines). Position is a fraction of the screen so it survives rotation. */
class HudConfig(private val store: ConfigStore) {
    @Volatile var x = 0.02f; private set
    @Volatile var y = 0.05f; private set
    @Volatile var scale = 1f; private set
    @Volatile var opacity = 0.9f; private set
    @Volatile var color = "#EDE7F6"; private set
    @Volatile var visible = true; private set

    companion object {
        val COLORS = listOf("#EDE7F6", "#A855F7", "#4ADE80", "#FBBF24", "#F87171", "#60A5FA")
        private val DEFAULTS = listOf("hud.x" to "0.02", "hud.y" to "0.05", "hud.scale" to "1.0", "hud.opacity" to "0.9", "hud.color" to "#EDE7F6", "hud.visible" to "true")
    }

    init { store.load().let { m ->
        m["hud.x"]?.toFloatOrNull()?.let { x = it.coerceIn(0f, 1f) }
        m["hud.y"]?.toFloatOrNull()?.let { y = it.coerceIn(0f, 1f) }
        m["hud.scale"]?.toFloatOrNull()?.let { scale = it.coerceIn(0.5f, 2f) }
        m["hud.opacity"]?.toFloatOrNull()?.let { opacity = it.coerceIn(0.2f, 1f) }
        m["hud.color"]?.takeIf { it in COLORS }?.let { color = it }
        m["hud.visible"]?.let { if (it == "true" || it == "false") visible = it.toBoolean() }
    } }

    fun update(x: Float = this.x, y: Float = this.y, scale: Float = this.scale, opacity: Float = this.opacity,
               color: String = this.color, visible: Boolean = this.visible) {
        this.x = x.coerceIn(0f, 1f); this.y = y.coerceIn(0f, 1f); this.scale = scale.coerceIn(0.5f, 2f)
        this.opacity = opacity.coerceIn(0.2f, 1f); this.color = if (color in COLORS) color else this.color; this.visible = visible
        save()
    }

    fun restoreDefaults() { update(0.02f, 0.05f, 1f, 0.9f, "#EDE7F6", true) }

    private fun save() = runCatching {
        val m = store.load().toMutableMap()
        m["hud.x"] = x.toString(); m["hud.y"] = y.toString(); m["hud.scale"] = scale.toString()
        m["hud.opacity"] = opacity.toString(); m["hud.color"] = color; m["hud.visible"] = visible.toString()
        store.save(m)
    }
}
