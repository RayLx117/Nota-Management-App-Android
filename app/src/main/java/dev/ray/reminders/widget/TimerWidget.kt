package dev.ray.reminders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.alarm.Timer
import dev.ray.reminders.alarm.TimerReceiver

/** Timer widget: one-tap presets when idle; a live countdown with pause/cancel while running. */
class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { render(ctx, mgr, it) }

    companion object {
        private val PRESET_IDS = intArrayOf(R.id.p0, R.id.p1, R.id.p2, R.id.p3)

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, TimerWidget::class.java)).forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val t = Timer.state(ctx).value
            val rv = RemoteViews(ctx.packageName, R.layout.widget_timer)
            val w = WidgetColors(ctx)
            rv.tintBackground(R.id.root, w.bg)
            rv.setTextColor(R.id.title, w.text)
            rv.setTextColor(R.id.chrono, w.text)
            rv.setTextColor(R.id.paused, w.sub)
            rv.setTextColor(R.id.label, w.sub)
            rv.tintBackground(R.id.toggle, w.accent)
            rv.setTextColor(R.id.toggle, w.ink)
            rv.tintBackground(R.id.cancel, w.chip)
            PRESET_IDS.forEach { rv.tintBackground(it, w.chip); rv.setTextColor(it, w.text) }
            rv.setOnClickPendingIntent(
                R.id.title,
                PendingIntent.getActivity(
                    ctx, 60_000 + widgetId,
                    Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "timer").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            if (t.idle) {
                rv.setTextViewText(R.id.title, "Timer")
                rv.setViewVisibility(R.id.idle, View.VISIBLE)
                rv.setViewVisibility(R.id.running, View.GONE)
                val presets = Timer.presets(ctx).value
                PRESET_IDS.forEachIndexed { i, id ->
                    val p = presets.getOrNull(i)
                    if (p == null) rv.setViewVisibility(id, View.GONE) else {
                        rv.setViewVisibility(id, View.VISIBLE)
                        rv.setTextViewText(id, if (p.name.isBlank()) Timer.shortDuration(p.seconds) else "${p.name}\n${Timer.shortDuration(p.seconds)}")
                        rv.setOnClickPendingIntent(id, action(ctx, Timer.ACTION_START_PRESET, i))
                    }
                }
            } else {
                rv.setTextViewText(R.id.title, if (t.ringing) "Time's up" else "Timer")
                rv.setViewVisibility(R.id.idle, View.GONE)
                rv.setViewVisibility(R.id.running, View.VISIBLE)
                rv.setTextViewText(R.id.label, t.label.ifBlank { Timer.shortDuration((t.durationMs / 1000).toInt()) })
                if (t.running) {
                    rv.setViewVisibility(R.id.chrono, View.VISIBLE)
                    rv.setViewVisibility(R.id.paused, View.GONE)
                    val base = SystemClock.elapsedRealtime() + (t.endAt - System.currentTimeMillis())
                    rv.setChronometer(R.id.chrono, base, null, true)
                    rv.setChronometerCountDown(R.id.chrono, true)
                    rv.setTextViewText(R.id.toggle, "Pause")
                    rv.setOnClickPendingIntent(R.id.toggle, action(ctx, Timer.ACTION_PAUSE))
                } else {
                    rv.setViewVisibility(R.id.chrono, View.GONE)
                    rv.setViewVisibility(R.id.paused, View.VISIBLE)
                    rv.setTextViewText(R.id.paused, if (t.ringing) "0:00" else Timer.format(t.remaining))
                    rv.setTextViewText(R.id.toggle, if (t.ringing) "+1 min" else "Resume")
                    rv.setOnClickPendingIntent(R.id.toggle, action(ctx, if (t.ringing) Timer.ACTION_ADD else Timer.ACTION_RESUME))
                }
                rv.setOnClickPendingIntent(R.id.cancel, action(ctx, Timer.ACTION_CANCEL))
            }
            mgr.updateAppWidget(widgetId, rv)
        }

        private fun action(ctx: Context, action: String, preset: Int = -1) = PendingIntent.getBroadcast(
            ctx, 70_000 + preset,
            Intent(ctx, TimerReceiver::class.java).setAction(action).setData(Uri.parse("nota://timer/$action/$preset"))
                .putExtra("preset", preset),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
