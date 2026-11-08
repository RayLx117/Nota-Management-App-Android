package dev.ray.reminders.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.data.Kind
import dev.ray.reminders.data.Recurrence
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.ReminderStore
import dev.ray.reminders.data.RepeatType
import dev.ray.reminders.ui.Describe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar on the home screen: alarms (accent dots) and reminders (highlight dots).
 * Tap a day to list what's on it right in the widget; arrows change month; chips filter repeat types.
 */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { render(ctx, mgr, it) }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = prefs(ctx).edit(); ids.forEach { e.remove("month_$it").remove("day_$it") }; e.apply()
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val p = prefs(ctx)
        when (intent.action) {
            ACTION_PREV, ACTION_NEXT -> {
                val m = month(ctx, id).plusMonths(if (intent.action == ACTION_NEXT) 1 else -1)
                p.edit().putInt("month_$id", m.year * 12 + m.monthValue - 1).apply()
            }
            ACTION_TODAY -> p.edit().remove("month_$id").remove("day_$id").apply()
            ACTION_SELECT -> p.edit().putLong("day_$id", intent.getLongExtra(EXTRA_DAY, LocalDate.now().toEpochDay())).apply()
            ACTION_FILTER -> {
                val t = intent.getStringExtra(EXTRA_TYPE) ?: return
                val hidden = hidden(ctx)
                p.edit().putStringSet("hidden", if (t in hidden) hidden - t else hidden + t).apply()
                refresh(ctx); return
            }
            else -> return
        }
        if (id >= 0) render(ctx, AppWidgetManager.getInstance(ctx), id)
    }

    companion object {
        private const val ACTION_PREV = "dev.ray.reminders.widget.CAL_PREV"
        private const val ACTION_NEXT = "dev.ray.reminders.widget.CAL_NEXT"
        private const val ACTION_TODAY = "dev.ray.reminders.widget.CAL_TODAY"
        private const val ACTION_SELECT = "dev.ray.reminders.widget.CAL_SELECT"
        private const val ACTION_FILTER = "dev.ray.reminders.widget.CAL_FILTER"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_TYPE = "type"

        private val CELL = intArrayOf(R.id.cal_c0, R.id.cal_c1, R.id.cal_c2, R.id.cal_c3, R.id.cal_c4, R.id.cal_c5, R.id.cal_c6, R.id.cal_c7, R.id.cal_c8, R.id.cal_c9, R.id.cal_c10, R.id.cal_c11, R.id.cal_c12, R.id.cal_c13, R.id.cal_c14, R.id.cal_c15, R.id.cal_c16, R.id.cal_c17, R.id.cal_c18, R.id.cal_c19, R.id.cal_c20, R.id.cal_c21, R.id.cal_c22, R.id.cal_c23, R.id.cal_c24, R.id.cal_c25, R.id.cal_c26, R.id.cal_c27, R.id.cal_c28, R.id.cal_c29, R.id.cal_c30, R.id.cal_c31, R.id.cal_c32, R.id.cal_c33, R.id.cal_c34, R.id.cal_c35, R.id.cal_c36, R.id.cal_c37, R.id.cal_c38, R.id.cal_c39, R.id.cal_c40, R.id.cal_c41)
        private val DAY = intArrayOf(R.id.cal_t0, R.id.cal_t1, R.id.cal_t2, R.id.cal_t3, R.id.cal_t4, R.id.cal_t5, R.id.cal_t6, R.id.cal_t7, R.id.cal_t8, R.id.cal_t9, R.id.cal_t10, R.id.cal_t11, R.id.cal_t12, R.id.cal_t13, R.id.cal_t14, R.id.cal_t15, R.id.cal_t16, R.id.cal_t17, R.id.cal_t18, R.id.cal_t19, R.id.cal_t20, R.id.cal_t21, R.id.cal_t22, R.id.cal_t23, R.id.cal_t24, R.id.cal_t25, R.id.cal_t26, R.id.cal_t27, R.id.cal_t28, R.id.cal_t29, R.id.cal_t30, R.id.cal_t31, R.id.cal_t32, R.id.cal_t33, R.id.cal_t34, R.id.cal_t35, R.id.cal_t36, R.id.cal_t37, R.id.cal_t38, R.id.cal_t39, R.id.cal_t40, R.id.cal_t41)
        private val ALARM_DOT = intArrayOf(R.id.cal_a0, R.id.cal_a1, R.id.cal_a2, R.id.cal_a3, R.id.cal_a4, R.id.cal_a5, R.id.cal_a6, R.id.cal_a7, R.id.cal_a8, R.id.cal_a9, R.id.cal_a10, R.id.cal_a11, R.id.cal_a12, R.id.cal_a13, R.id.cal_a14, R.id.cal_a15, R.id.cal_a16, R.id.cal_a17, R.id.cal_a18, R.id.cal_a19, R.id.cal_a20, R.id.cal_a21, R.id.cal_a22, R.id.cal_a23, R.id.cal_a24, R.id.cal_a25, R.id.cal_a26, R.id.cal_a27, R.id.cal_a28, R.id.cal_a29, R.id.cal_a30, R.id.cal_a31, R.id.cal_a32, R.id.cal_a33, R.id.cal_a34, R.id.cal_a35, R.id.cal_a36, R.id.cal_a37, R.id.cal_a38, R.id.cal_a39, R.id.cal_a40, R.id.cal_a41)
        private val REMINDER_DOT = intArrayOf(R.id.cal_r0, R.id.cal_r1, R.id.cal_r2, R.id.cal_r3, R.id.cal_r4, R.id.cal_r5, R.id.cal_r6, R.id.cal_r7, R.id.cal_r8, R.id.cal_r9, R.id.cal_r10, R.id.cal_r11, R.id.cal_r12, R.id.cal_r13, R.id.cal_r14, R.id.cal_r15, R.id.cal_r16, R.id.cal_r17, R.id.cal_r18, R.id.cal_r19, R.id.cal_r20, R.id.cal_r21, R.id.cal_r22, R.id.cal_r23, R.id.cal_r24, R.id.cal_r25, R.id.cal_r26, R.id.cal_r27, R.id.cal_r28, R.id.cal_r29, R.id.cal_r30, R.id.cal_r31, R.id.cal_r32, R.id.cal_r33, R.id.cal_r34, R.id.cal_r35, R.id.cal_r36, R.id.cal_r37, R.id.cal_r38, R.id.cal_r39, R.id.cal_r40, R.id.cal_r41)
        private val WEEKDAY = intArrayOf(R.id.cal_w0, R.id.cal_w1, R.id.cal_w2, R.id.cal_w3, R.id.cal_w4, R.id.cal_w5, R.id.cal_w6)
        private val CHIP = intArrayOf(R.id.cal_f0, R.id.cal_f1, R.id.cal_f2, R.id.cal_f3, R.id.cal_f4)
        private val FILTERS = listOf(
            RepeatType.DAILY to "Daily", RepeatType.WEEKLY to "Weekly", RepeatType.MONTHLY to "Monthly",
            RepeatType.YEARLY to "Yearly", RepeatType.ONCE to "Once",
        )

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("calendar_widget", Context.MODE_PRIVATE)
        private fun hidden(ctx: Context): Set<String> = prefs(ctx).getStringSet("hidden", emptySet()).orEmpty()
        private fun month(ctx: Context, id: Int): YearMonth {
            val v = prefs(ctx).getInt("month_$id", -1)
            return if (v < 0) YearMonth.now() else YearMonth.of(v / 12, v % 12 + 1)
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, CalendarWidget::class.java)).forEach { render(ctx, mgr, it) }
        }

        private fun broadcast(ctx: Context, widgetId: Int, action: String, code: Int, extra: (Intent) -> Unit = {}) = PendingIntent.getBroadcast(
            ctx, widgetId * 100 + code,
            Intent(ctx, CalendarWidget::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("nota://calendar/$widgetId/$code")).also(extra),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun occursOn(r: Reminder, day: LocalDate, zone: ZoneId) =
            Recurrence.next(r, day.atStartOfDay(zone).minusNanos(1))?.toLocalDate() == day

        private fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val w = WidgetColors(ctx)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val month = month(ctx, widgetId)
            val selected = LocalDate.ofEpochDay(prefs(ctx).getLong("day_$widgetId", today.toEpochDay()))
            val hiddenTypes = hidden(ctx)
            val items = ReminderStore.get(ctx).all().filter { it.enabled && it.repeat.name !in hiddenTypes }

            val rv = RemoteViews(ctx.packageName, R.layout.widget_calendar)
            rv.tintBackground(R.id.root, w.bg)
            rv.tintImage(R.id.cal_prev, w.accent)
            rv.tintImage(R.id.cal_next, w.accent)
            rv.tintImage(R.id.cal_divider, w.chip)
            rv.setTextViewText(R.id.cal_title, month.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            rv.setTextColor(R.id.cal_title, w.text)
            rv.setOnClickPendingIntent(R.id.cal_prev, broadcast(ctx, widgetId, ACTION_PREV, 90))
            rv.setOnClickPendingIntent(R.id.cal_next, broadcast(ctx, widgetId, ACTION_NEXT, 91))
            rv.setOnClickPendingIntent(R.id.cal_title, broadcast(ctx, widgetId, ACTION_TODAY, 92))

            FILTERS.forEachIndexed { i, (type, label) ->
                val on = type.name !in hiddenTypes
                rv.setTextViewText(CHIP[i], label)
                rv.tintBackground(CHIP[i], if (on) w.accent else w.chip)
                rv.setTextColor(CHIP[i], if (on) w.ink else w.sub)
                rv.setOnClickPendingIntent(CHIP[i], broadcast(ctx, widgetId, ACTION_FILTER, 80 + i) { it.putExtra(EXTRA_TYPE, type.name) })
            }
            // Sunday-first, like the in-app calendar.
            listOf(7, 1, 2, 3, 4, 5, 6).forEachIndexed { i, d ->
                rv.setTextViewText(WEEKDAY[i], DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault()))
                rv.setTextColor(WEEKDAY[i], w.sub)
            }

            val first = month.atDay(1)
            val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
            for (i in 0 until 42) {
                val d = gridStart.plusDays(i.toLong())
                val inMonth = d.month == month.month
                val isSel = d == selected
                rv.setTextViewText(DAY[i], d.dayOfMonth.toString())
                when {
                    isSel -> { rv.setInt(DAY[i], "setBackgroundResource", R.drawable.widget_circle); rv.tintBackground(DAY[i], w.accent); rv.setTextColor(DAY[i], w.ink) }
                    d == today -> { rv.setInt(DAY[i], "setBackgroundResource", R.drawable.widget_ring); rv.tintBackground(DAY[i], w.accent); rv.setTextColor(DAY[i], w.accent) }
                    else -> { rv.setInt(DAY[i], "setBackgroundResource", 0); rv.setTextColor(DAY[i], if (inMonth) w.text else w.outline) }
                }
                val on = items.filter { occursOn(it, d, zone) }
                val alarm = on.any { it.kind == Kind.ALARM }
                val reminder = on.any { it.kind == Kind.REMINDER }
                rv.setViewVisibility(ALARM_DOT[i], if (alarm) View.VISIBLE else View.GONE)
                rv.setViewVisibility(REMINDER_DOT[i], if (reminder) View.VISIBLE else View.GONE)
                rv.tintImage(ALARM_DOT[i], if (isSel) w.ink else w.accent)
                rv.tintImage(REMINDER_DOT[i], if (isSel) w.ink else w.highlight)
                rv.setOnClickPendingIntent(CELL[i], broadcast(ctx, widgetId, ACTION_SELECT, i) { it.putExtra(EXTRA_DAY, d.toEpochDay()) })
            }

            // The chosen day's alarms and reminders.
            val dayLabel = when (selected) {
                today -> "TODAY"
                today.plusDays(1) -> "TOMORROW"
                else -> selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase()
            }
            rv.setTextViewText(R.id.cal_day, dayLabel)
            rv.setTextColor(R.id.cal_day, w.sub)
            rv.setTextColor(R.id.cal_empty, w.sub)
            val list = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            items.filter { occursOn(it, selected, zone) }.sortedBy { if (it.allDay) -1 else it.hour * 60 + it.minute }.forEach { r ->
                val row = RemoteViews(ctx.packageName, R.layout.widget_calendar_item)
                row.tintBackground(R.id.row, w.chip)
                row.tintImage(R.id.bar, if (r.kind == Kind.ALARM) w.accent else w.highlight)
                row.setTextViewText(R.id.time, Describe.time(r))
                row.setTextColor(R.id.time, w.sub)
                row.setTextViewText(R.id.title, r.title.ifBlank { if (r.kind == Kind.ALARM) "Alarm" else "Reminder" })
                row.setTextColor(R.id.title, w.text)
                row.setOnClickFillInIntent(R.id.row, Intent().putExtra(MainActivity.EXTRA_TAB, if (r.kind == Kind.ALARM) "alarms" else "reminders"))
                list.addItem(r.id.toLong(), row)
            }
            rv.setRemoteAdapter(R.id.cal_items, list.build())
            rv.setEmptyView(R.id.cal_items, R.id.cal_empty)
            rv.setPendingIntentTemplate(
                R.id.cal_items,
                PendingIntent.getActivity(
                    ctx, widgetId * 100 + 99,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(Uri.parse("nota://calendar/$widgetId/open")),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            mgr.updateAppWidget(widgetId, rv)
        }
    }
}
