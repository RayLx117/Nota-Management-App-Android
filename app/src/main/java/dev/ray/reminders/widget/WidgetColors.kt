package dev.ray.reminders.widget

import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import dev.ray.reminders.ui.ThemeStore

/** The app theme's colors as plain ints, for painting widgets. */
internal class WidgetColors(ctx: Context) {
    private val c = ThemeStore.run { ensure(ctx); colors }
    val bg = c.shelf.toArgb()
    val chip = c.raised.toArgb()
    /** The shaded corner tabs on the all-lists widget. */
    val corner = lerp(c.raised, c.outline, 0.3f).toArgb()
    val outline = c.outline.toArgb()
    val text = c.text.toArgb()
    val sub = c.sub.toArgb()
    val accent = c.accent.toArgb()
    val highlight = c.highlight.toArgb()
    val ink = c.ink.toArgb()
}

/** Recolors a view's (rounded) background drawable. */
internal fun RemoteViews.tintBackground(id: Int, color: Int) =
    setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color))

/** Recolors an ImageView's picture. */
internal fun RemoteViews.tintImage(id: Int, color: Int) = setInt(id, "setColorFilter", color)
