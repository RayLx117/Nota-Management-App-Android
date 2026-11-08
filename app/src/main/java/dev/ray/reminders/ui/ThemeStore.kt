package dev.ray.reminders.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The three colors someone picks in Settings > Theme (ARGB ints). */
data class Palette(val bg: Int, val accent: Int, val highlight: Int)

/** Every color the app draws with, worked out from a [Palette]. */
data class ThemeColors(
    val bg: Color, val shelf: Color, val raised: Color, val line: Color, val outline: Color,
    val text: Color, val sub: Color, val ink: Color, val accent: Color, val highlight: Color,
)

object ThemeStore {
    val presets = listOf(
        "Aurora" to Palette(0xFF0B1020.toInt(), 0xFF2EE59D.toInt(), 0xFF22C3E6.toInt()),
        "Ember" to Palette(0xFF170E0B.toInt(), 0xFFFF8A4C.toInt(), 0xFFFFC857.toInt()),
        "Orchid" to Palette(0xFF130B1F.toInt(), 0xFFC38BFF.toInt(), 0xFFFF7AB6.toInt()),
        "Forest" to Palette(0xFF0A1510.toInt(), 0xFF7BD88F.toInt(), 0xFFD4E157.toInt()),
        "Ocean" to Palette(0xFF06141D.toInt(), 0xFF4FC3F7.toInt(), 0xFF64FFDA.toInt()),
        "Rose" to Palette(0xFF1A0C12.toInt(), 0xFFFF6F91.toInt(), 0xFFFFB38A.toInt()),
        "Graphite" to Palette(0xFF111214.toInt(), 0xFFE9ECF1.toInt(), 0xFF8FA3BF.toInt()),
    )
    val default get() = presets[0].second

    var palette by mutableStateOf(default)
        private set
    var colors by mutableStateOf(derive(default))
        private set

    private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        if (p.contains("bg")) use(Palette(p.getInt("bg", default.bg), p.getInt("accent", default.accent), p.getInt("highlight", default.highlight)))
    }

    fun set(ctx: Context, p: Palette) {
        prefs(ctx).edit().putInt("bg", p.bg).putInt("accent", p.accent).putInt("highlight", p.highlight).apply()
        use(p)
    }

    /** Shows a palette without saving it (while dragging the color sliders). */
    fun preview(p: Palette) = use(p)

    private fun use(p: Palette) {
        palette = p
        colors = derive(p)
    }

    fun hsv(color: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    fun fromHsv(h: Float, s: Float, v: Float): Int =
        android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    /** Surfaces step up from the background in its own hue, the way the original navy shelf did. */
    fun derive(p: Palette): ThemeColors {
        val (h, s, v) = hsv(p.bg).let { Triple(it[0], it[1], it[2]) }
        fun c(sat: Float, value: Float) = Color(fromHsv(h, sat, value))
        val tint = s.coerceAtMost(0.6f)
        val accent = Color(p.accent)
        return ThemeColors(
            bg = Color(p.bg),
            shelf = c(s * 0.85f, v + 0.063f),
            raised = c(s * 0.8f, v + 0.134f),
            line = c(s * 0.84f, v + 0.095f),
            outline = c(s * 0.68f, v + 0.248f),
            text = c(tint * 0.08f, 0.98f),
            sub = c(tint * 0.38f, 0.72f),
            ink = if (accent.luminance() > 0.3f) c(s.coerceAtLeast(0.4f), 0.1f) else Color.White,
            accent = accent,
            highlight = Color(p.highlight),
        )
    }
}
