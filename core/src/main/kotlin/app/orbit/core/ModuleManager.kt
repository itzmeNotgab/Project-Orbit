package app.orbit.core

class ModuleManager(private val bus: EventBus, private val store: ConfigStore) {
    private val map = LinkedHashMap<String, Module>()
    private val cfg = store.load().toMutableMap()

    /** Writes only keys this class owns; other writers (HudConfig) keep their own keys. */
    private fun persist() = runCatching {
        val out = store.load().toMutableMap()
        out.keys.removeAll { it.startsWith("module.") || it.startsWith("fav.") || it.startsWith("profile.") }
        out.putAll(cfg.filterKeys { !it.startsWith("hud.") })
        store.save(out)
    }

    val all: List<Module> @Synchronized get() = map.values.toList()
    @Synchronized fun get(name: String): Module? = map[name]

    @Synchronized fun register(m: Module) {
        require(m.name !in map) { "module '${m.name}' already registered" }
        m.attach(bus); map[m.name] = m
        m.settings.forEach { s -> cfg["module.${m.name}.${s.name}"]?.let(s::decode) }
        if (cfg["module.${m.name}.enabled"] == "true") setEnabled(m, true, persist = false)
    }

    @Synchronized fun setEnabled(m: Module, on: Boolean, persist: Boolean = true): Boolean {
        if (on == m.enabled) return true
        if (on) {
            m.compatibilityError()?.let { m.lastError = it; return false }
            try { m.enableInternal(); m.enabled = true; m.lastError = null }
            catch (t: Throwable) {
                runCatching { m.disableInternal() }
                m.lastError = t.message ?: t.javaClass.simpleName
                return false
            }
        } else {
            runCatching { m.disableInternal() }
            m.enabled = false
        }
        if (persist) save()
        return true
    }

    @Synchronized fun save() {
        for (m in map.values) {
            cfg["module.${m.name}.enabled"] = m.enabled.toString()
            m.settings.forEach { cfg["module.${m.name}.${it.name}"] = it.encode() }
        }
        persist()
    }

    // ---- favorites ----
    @Synchronized fun isFavorite(m: Module) = cfg["fav.${m.name}"] == "true"
    @Synchronized fun toggleFavorite(m: Module) { cfg["fav.${m.name}"] = (!isFavorite(m)).toString(); persist() }

    // ---- configuration profiles (snapshots of module state and settings) ----
    @Synchronized fun profiles(): List<String> =
        cfg.keys.filter { it.startsWith("profile.") }.map { it.removePrefix("profile.").substringBefore('.') }.distinct().sorted()

    @Synchronized fun saveProfile(name: String) {
        require(name.matches(Regex("[A-Za-z0-9 _-]{1,24}"))) { "Profile names: 1-24 letters, numbers, space, _ or -" }
        save()
        cfg.keys.filter { it.startsWith("profile.$name.") }.toList().forEach { cfg.remove(it) }
        cfg.filterKeys { it.startsWith("module.") }.forEach { (k, v) -> cfg["profile.$name.$k"] = v }
        persist()
    }

    @Synchronized fun loadProfile(name: String): Boolean {
        val prefix = "profile.$name."
        val snap = cfg.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
        if (snap.isEmpty()) return false
        for (m in map.values) {
            m.settings.forEach { s -> snap["module.${m.name}.${s.name}"]?.let(s::decode) }
            setEnabled(m, snap["module.${m.name}.enabled"] == "true", persist = false)
        }
        save(); return true
    }

    @Synchronized fun deleteProfile(name: String) {
        cfg.keys.filter { it.startsWith("profile.$name.") }.toList().forEach { cfg.remove(it) }
        persist()
    }
}
