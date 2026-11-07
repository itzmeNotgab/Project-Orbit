package app.orbit.core

sealed class Setting<T : Any>(val name: String, val default: T) {
    var value: T = default
    abstract fun encode(): String
    /** Invalid input is ignored, so a corrupt config can never break a module. */
    abstract fun decode(s: String)
}

class BoolSetting(name: String, default: Boolean) : Setting<Boolean>(name, default) {
    override fun encode() = value.toString()
    override fun decode(s: String) { if (s == "true" || s == "false") value = s.toBoolean() }
}

class FloatSetting(name: String, default: Float, val min: Float, val max: Float) : Setting<Float>(name, default) {
    init { require(min <= max && default in min..max) }
    override fun encode() = value.toString()
    override fun decode(s: String) { s.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value = it.coerceIn(min, max) } }
}

class ChoiceSetting(name: String, val options: List<String>, default: String) : Setting<String>(name, default) {
    init { require(default in options) }
    override fun encode() = value
    override fun decode(s: String) { if (s in options) value = s }
}
