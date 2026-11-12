package app.orbit.client

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

class ThemeDef(val name: String, val accent: Color, val bg: Color, val surface: Color)

val THEMES = listOf(
    ThemeDef("Nebula", Color(0xFFA855F7), Color(0xFF0A0612), Color(0xFF130C20)),
    ThemeDef("Ion", Color(0xFF60A5FA), Color(0xFF060B14), Color(0xFF0D1626)),
    ThemeDef("Aurora", Color(0xFF4ADE80), Color(0xFF06100B), Color(0xFF0D1F16)),
    ThemeDef("Ember", Color(0xFFFB923C), Color(0xFF120A06), Color(0xFF1F130C)),
)

/** Launcher settings. Backed by SharedPreferences; reads are Compose state so the UI updates immediately. */
object OrbitSettings {
    private lateinit var prefs: SharedPreferences
    private val _theme = mutableStateOf("Nebula")
    private val _glass = mutableStateOf("auto")
    private val _animations = mutableStateOf(true)
    private val _dots = mutableStateOf(true)
    private val _intro = mutableStateOf(true)
    private val _target = mutableStateOf(DEFAULT_TARGET)
    var lowRamDevice = false; private set

    const val DEFAULT_TARGET = "1.26.60.4"

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE)
        _theme.value = prefs.getString("theme", "Nebula") ?: "Nebula"
        _glass.value = prefs.getString("glass", "auto") ?: "auto"
        _animations.value = prefs.getBoolean("animations", true)
        _dots.value = prefs.getBoolean("dots", true)
        _intro.value = prefs.getBoolean("intro", true)
        _target.value = prefs.getString("target", DEFAULT_TARGET) ?: DEFAULT_TARGET
        lowRamDevice = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    var theme: String
        get() = _theme.value
        set(v) { _theme.value = v; prefs.edit().putString("theme", v).apply() }
    /** "auto" (off on low-RAM devices), "on", "off" */
    var glass: String
        get() = _glass.value
        set(v) { _glass.value = v; prefs.edit().putString("glass", v).apply() }
    var animations: Boolean
        get() = _animations.value
        set(v) { _animations.value = v; prefs.edit().putBoolean("animations", v).apply() }
    var dots: Boolean
        get() = _dots.value
        set(v) { _dots.value = v; prefs.edit().putBoolean("dots", v).apply() }
    var showIntro: Boolean
        get() = _intro.value
        set(v) { _intro.value = v; prefs.edit().putBoolean("intro", v).apply() }
    /** Minecraft version ORBIT targets. Editable so future versions need no app update. */
    var targetVersion: String
        get() = _target.value
        set(v) { _target.value = v; prefs.edit().putString("target", v).apply() }

    val themeDef: ThemeDef get() = THEMES.firstOrNull { it.name == theme } ?: THEMES[0]
    val effectsOn: Boolean get() = when (glass) { "on" -> true; "off" -> false; else -> !lowRamDevice }
}

/** Process-wide triggers the UI can observe. */
object AppEvents {
    val resume = mutableIntStateOf(0)
}
