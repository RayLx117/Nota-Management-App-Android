package dev.ray.reminders.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.data.Kind
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.ReminderStore
import dev.ray.reminders.ui.Describe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** "Up next": the next alarm and the next reminder, with how long until each. */
class NextWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TICK) refresh(ctx)
    }

    override fun onDisabled(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(tick(ctx))
    }

    companion object {
        private const val ACTION_TICK = "dev.ray.reminders.widget.NEXT_TICK"

        private fun tick(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 80_000, Intent(ctx, NextWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun open(ctx: Context, tab: String) = PendingIntent.getActivity(
            ctx, 80_001 + tab.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** When it next goes off: snoozed time, else the next scheduled time. */
        private fun at(r: Reminder) = r.snoozedUntil ?: r.nextTriggerAt

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextWidget::class.java))
            if (ids.isEmpty()) return
            val all = ReminderStore.get(ctx).all()
            val now = System.currentTimeMillis()
            val alarm = all.filter { it.kind == Kind.ALARM && it.enabled && !it.isActive && at(it) != null }.minByOrNull { at(it)!! }
            val ringing = all.firstOrNull { it.kind == Kind.ALARM && it.isActive }
            val reminder = all.filter { it.kind == Kind.REMINDER && it.enabled && !it.isActive && at(it) != null }.minByOrNull { at(it)!! }
            val w = WidgetColors(ctx)
            val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

            val rv = RemoteViews(ctx.packageName, R.layout.widget_next)
            rv.tintBackground(R.id.root, w.bg)
            rv.tintImage(R.id.divider, w.chip)
            listOf(R.id.alarm_label, R.id.alarm_title, R.id.reminder_label, R.id.reminder_time).forEach { rv.setTextColor(it, w.sub) }
            rv.setTextColor(R.id.alarm_time, w.text)
            rv.setTextColor(R.id.reminder_title, w.text)
            rv.setTextColor(R.id.alarm_in, w.accent)
            rv.setTextColor(R.id.reminder_in, w.highlight)
            rv.setOnClickPendingIntent(R.id.alarm, open(ctx, "alarms"))
            rv.setOnClickPendingIntent(R.id.reminder, open(ctx, "reminders"))

            // Alarm
            when {
                ringing != null -> {
                    rv.setTextViewText(R.id.alarm_time, Describe.time(ringing))
                    rv.setTextViewText(R.id.alarm_title, ringing.title.takeIf { it.isNotBlank() && it != "Alarm" } ?: "Alarm")
                    rv.setTextViewText(R.id.alarm_in, "Ringing")
                }
                alarm != null -> {
                    val t = at(alarm)!!
                    rv.setTextViewText(R.id.alarm_time, Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt))
                    rv.setTextViewText(R.id.alarm_title, listOfNotNull(dayLabel(t), alarm.title.takeIf { it.isNotBlank() && it != "Alarm" }).joinToString(" · "))
                    rv.setTextViewText(R.id.alarm_in, until(t, now))
                }
                else -> {
                    rv.setTextViewText(R.id.alarm_time, "—")
                    rv.setTextViewText(R.id.alarm_title, "No alarms set")
                    rv.setTextViewText(R.id.alarm_in, "")
                }
            }
            // Reminder
            if (reminder != null) {
                val t = at(reminder)!!
                rv.setTextViewText(R.id.reminder_title, reminder.title)
                if (reminder.allDay && reminder.snoozedUntil == null) {
                    // All day: just the day.
                    val day = dayLabel(t) ?: "Today"
                    rv.setTextViewText(R.id.reminder_time, "All day")
                    rv.setTextViewText(R.id.reminder_in, day)
                } else {
                    val time = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
                    rv.setTextViewText(R.id.reminder_time, listOfNotNull(dayLabel(t), time).joinToString(" · "))
                    rv.setTextViewText(R.id.reminder_in, until(t, now))
                }
            } else {
                rv.setTextViewText(R.id.reminder_title, "Nothing coming up")
                rv.setTextViewText(R.id.reminder_time, "")
                rv.setTextViewText(R.id.reminder_in, "")
            }
            ids.forEach { mgr.updateAppWidget(it, rv) }

            // Tick again at the next minute (only delivered while the phone is awake; no battery cost when asleep).
            val next = (now / 60_000 + 1) * 60_000
            ctx.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, tick(ctx))
        }

        /** null for today, else "Tomorrow", a weekday this week, or a date. */
        private fun dayLabel(t: Long): String? {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val today = LocalDate.now(zone)
            return when {
                day == today -> null
                day == today.plusDays(1) -> "Tomorrow"
                day.isBefore(today.plusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                else -> day.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }

        /** "in 25 min", "in 3 h 5 min", "in 2 d 4 h". */
        private fun until(t: Long, now: Long): String {
            val mins = ((t - now + 59_999) / 60_000).coerceAtLeast(0)
            val d = mins / (24 * 60); val h = mins % (24 * 60) / 60; val m = mins % 60
            return when {
                mins <= 0 -> "now"
                d > 0 -> "in ${d} d" + (if (h > 0) " $h h" else "")
                h > 0 -> "in $h h" + (if (m > 0) " $m min" else "")
                else -> "in $m min"
            }
        }
    }
}
