package app.orbit.core

enum class Category { HUD, VISUAL, UTILITY, PERFORMANCE, CLIENT }

abstract class Module(val name: String, val description: String, val category: Category) {
    val settings = mutableListOf<Setting<*>>()
    @Volatile var enabled = false; internal set
    @Volatile var lastError: String? = null; internal set

    private lateinit var bus: EventBus
    private val subs = mutableListOf<Subscription>()

    internal fun attach(b: EventBus) { bus = b }

    protected fun bool(n: String, d: Boolean) = BoolSetting(n, d).also { settings += it }
    protected fun float(n: String, d: Float, min: Float, max: Float) = FloatSetting(n, d, min, max).also { settings += it }
    protected fun choice(n: String, o: List<String>, d: String) = ChoiceSetting(n, o, d).also { settings += it }

    /** Subscribe for as long as the module is enabled. */
    protected fun <T : Any> listen(type: Class<T>, replay: Boolean = false, h: (T) -> Unit) {
        subs += bus.subscribe(type, replay, h)
    }

    /** Non-null = this module cannot run in the current environment (shown to the user). */
    open fun compatibilityError(): String? = null
    protected open fun onEnable() {}
    protected open fun onDisable() {}
    open fun hudLines(): List<String> = emptyList()

    internal fun enableInternal() = onEnable()
    internal fun disableInternal() {
        subs.forEach { it.cancel() }; subs.clear()
        onDisable()
    }
}
