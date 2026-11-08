package dev.ray.reminders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.ui.QuickNoteActivity

/**
 * "Quick note": tap the pencil to type a note and save it in one go.
 * Home-screen widgets can't hold a text box, so typing happens in a small popup over the home screen.
 * The top banner opens a new note in the full app instead.
 */
class NoteWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NoteWidget::class.java))
            if (ids.isEmpty()) return
            val w = WidgetColors(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_note)
            rv.tintBackground(R.id.root, w.bg)
            rv.tintBackground(R.id.write, w.accent)
            rv.tintImage(R.id.write, w.ink)
            rv.tintBackground(R.id.banner, w.chip)
            rv.setTextColor(R.id.banner, w.accent)

            val quick = PendingIntent.getActivity(
                ctx, 81_000, Intent(ctx, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.write, quick)
            rv.setOnClickPendingIntent(R.id.write_area, quick)
            rv.setOnClickPendingIntent(R.id.banner, fullNote(ctx, ""))
            ids.forEach { mgr.updateAppWidget(it, rv) }
        }

        /** Opens Nota on a new note in the full editor, starting with [text]. */
        fun fullNote(ctx: Context, text: String): PendingIntent = PendingIntent.getActivity(
            ctx, 81_001,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "notes")
                .putExtra(MainActivity.EXTRA_NOTE_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
