package com.fos.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Language helper: Persian / English switch used everywhere through tr(). */
object Lang {
    /** 0 = follow system, 1 = Persian, 2 = English */
    var mode by mutableIntStateOf(1)
    val fa: Boolean
        get() = when (mode) {
            1 -> true
            2 -> false
            else -> Locale.getDefault().language == "fa"
        }
}

fun tr(fa: String, en: String): String = if (Lang.fa) fa else en

/** A SharedPreferences-backed Compose state. */
class StatePref<T : Any>(
    private val sp: SharedPreferences,
    private val key: String,
    private val def: T
) : ReadWriteProperty<Any?, T> {

    private val state = mutableStateOf(read())

    @Suppress("UNCHECKED_CAST")
    private fun read(): T = when (def) {
        is Int -> sp.getInt(key, def) as T
        is Float -> sp.getFloat(key, def) as T
        is Boolean -> sp.getBoolean(key, def) as T
        is String -> (sp.getString(key, def) ?: def) as T
        else -> def
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        val e = sp.edit()
        when (value) {
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            is Boolean -> e.putBoolean(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }

    fun reset() {
        sp.edit().remove(key).apply()
        state.value = def
    }
}

/** Every user-facing option of F OS lives here. */
class Settings(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("fos_settings", Context.MODE_PRIVATE)
    private val all = mutableListOf<StatePref<*>>()
    private fun <T : Any> pref(key: String, def: T): StatePref<T> =
        StatePref(sp, key, def).also { all.add(it) }

    // ---- General / theme ----
    var language by pref("language", 1)               // 0 auto, 1 fa, 2 en
    var themeMode by pref("themeMode", 2)             // 0 system, 1 light, 2 dark
    var useDynamic by pref("useDynamic", false)
    var accentHue by pref("accentHue", 255f)
    var accentSat by pref("accentSat", 0.62f)
    var fontFamily by pref("fontFamily", 0)
    var haptic by pref("haptic", true)
    var animSpeed by pref("animSpeed", 1f)
    var hideStatusBar by pref("hideStatusBar", false)

    // ---- Icons ----
    var iconShape by pref("iconShape", 0)             // 0 rounded, 1 squircle, 2 teardrop, 3 hexagon
    var cornerRadius by pref("cornerRadius", 30)      // percent of size, 0..50
    var iconSize by pref("iconSize", 56)              // dp
    var labelShow by pref("labelShow", true)
    var labelSize by pref("labelSize", 11f)
    var labelColorMode by pref("labelColorMode", 0)   // 0 white, 1 black, 2 accent
    var labelShadow by pref("labelShadow", true)
    var legacyPlate by pref("legacyPlate", true)
    var plateMode by pref("plateMode", 0)             // 0 auto, 1 dark, 2 accent
    var monoIcons by pref("monoIcons", false)

    // ---- Home ----
    var gridCols by pref("gridCols", 4)
    var gridRows by pref("gridRows", 5)
    var dockCount by pref("dockCount", 4)
    var dockBg by pref("dockBg", true)
    var dockAlpha by pref("dockAlpha", 0.45f)
    var dockRadius by pref("dockRadius", 30)
    var showClock by pref("showClock", true)
    var clockStyle by pref("clockStyle", 0)           // 0 big, 1 compact, 2 split
    var clock24 by pref("clock24", true)
    var showDate by pref("showDate", true)
    var jalali by pref("jalali", true)
    var clockSize by pref("clockSize", 64f)
    var clockAlign by pref("clockAlign", 0)           // 0 start, 1 center
    var showSearchBar by pref("showSearchBar", true)
    var pageIndicator by pref("pageIndicator", true)
    var pageEffect by pref("pageEffect", 1)           // 0 slide, 1 zoom-fade, 2 cube, 3 depth
    var lockLayout by pref("lockLayout", false)

    // ---- Drawer ----
    var drawerCols by pref("drawerCols", 4)
    var drawerList by pref("drawerList", false)
    var drawerAlpha by pref("drawerAlpha", 0.92f)
    var drawerSort by pref("drawerSort", 0)           // 0 A-Z, 1 most used, 2 newest

    // ---- Wallpaper & tilt (Solo Tilt style) ----
    var wpMode by pref("wpMode", 0)                   // 0 gradient preset, 1 image
    var wpPreset by pref("wpPreset", 0)
    var tiltOn by pref("tiltOn", true)
    var tiltDepth by pref("tiltDepth", 0.55f)
    var tiltPersp by pref("tiltPersp", 12f)
    var wpBlur by pref("wpBlur", 0.5f)
    var wpZoom by pref("wpZoom", 1.22f)
    var tiltSens by pref("tiltSens", 2.2f)
    var tiltSmooth by pref("tiltSmooth", 0.85f)
    var wpDim by pref("wpDim", 0.12f)
    var tiltGlare by pref("tiltGlare", true)
    var invX by pref("invX", false)
    var invY by pref("invY", false)
    var autoCenter by pref("autoCenter", true)
    var iconParallax by pref("iconParallax", 0.35f)

    // ---- Gestures ----
    var swipeDown by pref("swipeDown", 0)             // 0 notifications, 1 search, 2 none
    var doubleTap by pref("doubleTap", 0)             // 0 none, 1 settings, 2 drawer

    fun resetAll() {
        all.forEach { it.reset() }
    }
}
