package dev.ray.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.ray.reminders.data.Images
import dev.ray.reminders.data.Kind
import dev.ray.reminders.data.RepeatType
import dev.ray.reminders.data.Recurrence
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.ReminderStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * All state changes for reminders go through here so that the stored state, the scheduled
 * alarms and the visible notification stay in sync.
 *
 * Alarm kinds per reminder:
 *  - FIRE: the next scheduled occurrence
 *  - NAG: the next repeat alert while the reminder is waiting for Done
 *  - FULLSCREEN: when the full-screen alarm should take over
 *  - SNOOZE: when a snoozed reminder comes back
 */
object ReminderEngine {

    const val ACTION_FIRE = "dev.ray.reminders.action.FIRE"
    const val ACTION_NAG = "dev.ray.reminders.action.NAG"
    const val ACTION_FULLSCREEN = "dev.ray.reminders.action.FULLSCREEN"
    const val ACTION_SNOOZE_END = "dev.ray.reminders.action.SNOOZE_END"
    const val ACTION_DONE = "dev.ray.reminders.action.DONE"
    const val ACTION_SNOOZE = "dev.ray.reminders.action.SNOOZE"
    const val ACTION_SWIPED = "dev.ray.reminders.action.SWIPED"
    const val ACTION_PREALERT = "dev.ray.reminders.action.PREALERT"
    const val ACTION_SKIP = "dev.ray.reminders.action.SKIP"
    const val ACTION_END = "dev.ray.reminders.action.END"
    const val ACTION_CANCEL_SNOOZE = "dev.ray.reminders.action.CANCEL_SNOOZE"
    const val EXTRA_ID = "id"

    private const val MINUTE = 60_000L

    private fun store(ctx: Context) = ReminderStore.get(ctx)
    private fun now() = System.currentTimeMillis()

    fun nextOccurrence(r: Reminder, afterMillis: Long): Long? =
        Recurrence.next(r, ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), ZoneId.systemDefault()))
            ?.toInstant()?.toEpochMilli()

    // ---------------------------------------------------------------- user actions

    /**
     * A one-time alarm set for a time that has already passed rings at that time today, or
     * tomorrow if today's has gone too (like a clock app).
     */
    fun rollOnceAlarm(r: Reminder): Reminder {
        if (r.kind != Kind.ALARM || r.repeat != RepeatType.ONCE) return r
        val now = now()
        if (nextOccurrence(r, now) != null) return r
        val today = java.time.LocalDate.now().toEpochDay()
        return listOf(today, today + 1).map { r.copy(startEpochDay = it) }
            .firstOrNull { nextOccurrence(it, now) != null } ?: r
    }

    /** Create or update a reminder from the editor. */
    fun save(ctx: Context, input: Reminder) {
        val edited = if (input.enabled) rollOnceAlarm(input) else input
        store(ctx).get(edited.id)?.imagePath?.let { old -> if (old != edited.imagePath) Images.delete(old) }
        val next = if (edited.enabled) nextOccurrence(edited, now()) else null
        var r = edited.copy(nextTriggerAt = next)
        if (!r.enabled) r = r.copy(activeSince = null, fullScreenShownAt = null, snoozedUntil = null)
        store(ctx).upsert(r)
        armFire(ctx, r)
        if (r.snoozedUntil == null) { cancel(ctx, ACTION_SNOOZE_END, r.id); Notifier.cancelSnoozed(ctx, r.id) }
        if (r.isActive) {
            // Behaviour may have changed: refresh the notification quietly and re-arm alerts.
            Notifier.show(ctx, r, alert = false)
            armActive(ctx, r)
        } else {
            clearActive(ctx, r.id)
        }
    }

    fun setEnabled(ctx: Context, id: Int, enabled: Boolean) {
        store(ctx).get(id)?.let { save(ctx, it.copy(enabled = enabled)) }
    }

    fun delete(ctx: Context, id: Int) {
        listOf(ACTION_FIRE, ACTION_NAG, ACTION_FULLSCREEN, ACTION_SNOOZE_END, ACTION_PREALERT, ACTION_END).forEach { cancel(ctx, it, id) }
        Notifier.cancelUpcoming(ctx, id)
        Notifier.cancelSnoozed(ctx, id)
        Notifier.cancel(ctx, id)
        store(ctx).get(id)?.imagePath?.let(Images::delete)
        store(ctx).delete(id)
    }

    /** Make the reminder go off right now without touching its schedule (used by "Test"). */
    fun testNow(ctx: Context, id: Int) = activate(ctx, id)

    /** Done: stop everything for the current occurrence. The next occurrence stays scheduled. */
    fun done(ctx: Context, id: Int) {
        store(ctx).modify(id) { it.copy(activeSince = null, fullScreenShownAt = null, snoozedUntil = null) }
        cancel(ctx, ACTION_SNOOZE_END, id)
        Notifier.cancelSnoozed(ctx, id)
        clearActive(ctx, id)
    }

    /** Called off a snooze: it won't ring again for this occurrence. */
    fun cancelSnooze(ctx: Context, id: Int) {
        store(ctx).modify(id) { it.copy(snoozedUntil = null) }
        cancel(ctx, ACTION_SNOOZE_END, id)
        Notifier.cancelSnoozed(ctx, id)
    }

    fun snooze(ctx: Context, id: Int, fromAlarm: Boolean = false) {
        val r = store(ctx).get(id) ?: return
        val minutes = if (fromAlarm) r.fx.alarmSnoozeMinutes else r.fx.snoozeMinutes
        val until = now() + minutes.coerceAtLeast(1) * MINUTE
        val snoozed = store(ctx).modify(id) { it.copy(activeSince = null, fullScreenShownAt = null, snoozedUntil = until) }
        clearActive(ctx, id)
        setAlarm(ctx, ACTION_SNOOZE_END, id, until)
        // Alarms show a live countdown to the next ring (status bar, lock screen, Now Bar).
        if (snoozed != null && snoozed.kind == Kind.ALARM) Notifier.showSnoozed(ctx, snoozed, until)
    }

    /** Alarm heads-up "Skip": drop the upcoming occurrence and move on to the one after. */
    fun skipNext(ctx: Context, id: Int) {
        val r = store(ctx).get(id) ?: return
        val skipped = r.nextTriggerAt ?: return
        val next = nextOccurrence(r, skipped)
        val updated = store(ctx).modify(id) { it.copy(nextTriggerAt = next) } ?: return
        Notifier.cancelUpcoming(ctx, id)
        armFire(ctx, updated)
    }

    /** The notification was swiped away (or cleared). Sticky reminders come straight back. */
    fun swiped(ctx: Context, id: Int) {
        val r = store(ctx).get(id) ?: return
        if (!r.isActive) return
        if (r.fx.effectiveSticky) Notifier.show(ctx, r, alert = false) else done(ctx, id)
    }

    // ---------------------------------------------------------------- alarm callbacks

    fun onAlarm(ctx: Context, action: String, id: Int) {
        val r = store(ctx).get(id) ?: return
        when (action) {
            ACTION_PREALERT -> if (r.enabled && r.nextTriggerAt != null) Notifier.showUpcoming(ctx, r)
            ACTION_FIRE -> {
                if (!r.enabled) return
                Notifier.cancelUpcoming(ctx, id)
                // Schedule the following occurrence first, so a reminder left un-done
                // never blocks the next one.
                val next = nextOccurrence(r, maxOf(now(), r.nextTriggerAt ?: 0L))
                // A one-time alarm switches itself off once it has rung.
                val updated = store(ctx).modify(id) {
                    it.copy(nextTriggerAt = next, enabled = it.enabled && !(it.kind == Kind.ALARM && next == null))
                } ?: return
                armFire(ctx, updated)
                activate(ctx, id)
            }
            ACTION_SNOOZE_END -> if (r.snoozedUntil != null) { Notifier.cancelSnoozed(ctx, id); activate(ctx, id) }
            ACTION_END -> if (r.isActive || r.snoozedUntil != null) done(ctx, id)
            ACTION_NAG -> if (r.isActive && r.fx.effectiveNag) {
                Notifier.show(ctx, r, alert = true, fullScreen = r.fullScreenShownAt != null)
                setAlarm(ctx, ACTION_NAG, id, now() + nagInterval(r))
            }
            ACTION_FULLSCREEN -> if (r.isActive && r.fx.effectiveFullScreen) {
                val shown = store(ctx).modify(id) { it.copy(fullScreenShownAt = now()) } ?: return
                Notifier.show(ctx, shown, alert = true, fullScreen = true)
            }
        }
    }

    /** After a reboot, app update or clock change: rebuild every alarm from stored state. */
    fun restoreAll(ctx: Context) {
        val now = now()
        for (r in store(ctx).all()) {
            when {
                // A one-off occurrence was missed while the phone was off: deliver it now.
                r.enabled && r.nextTriggerAt != null && r.nextTriggerAt <= now ->
                    onAlarm(ctx, ACTION_FIRE, r.id)
                r.enabled -> {
                    val next = nextOccurrence(r, now)
                    armFire(ctx, store(ctx).modify(r.id) { it.copy(nextTriggerAt = next) } ?: r)
                }
            }
            val current = store(ctx).get(r.id) ?: continue
            current.snoozedUntil?.let {
                setAlarm(ctx, ACTION_SNOOZE_END, r.id, maxOf(it, now + 1_000))
                if (current.kind == Kind.ALARM) Notifier.showSnoozed(ctx, current, maxOf(it, now + 1_000))
            }
            if (current.isActive) {
                Notifier.show(ctx, current, alert = false)
                armActive(ctx, current)
            }
        }
    }

    // ---------------------------------------------------------------- internals

    /** When a just-fired reminder should clear itself: its end time, or midnight for all-day ones. */
    private fun endAt(r: Reminder): Long? {
        if (r.kind != Kind.REMINDER) return null
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        if (r.allDay) return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = r.endMinute ?: return null
        var at = today.atStartOfDay(zone).toInstant().toEpochMilli() + end * MINUTE
        if (at <= now()) at += 24 * 60 * MINUTE // ends after midnight
        return at
    }

    private fun activate(ctx: Context, id: Int) {
        cancel(ctx, ACTION_SNOOZE_END, id)
        val wasSnoozed = store(ctx).get(id)?.snoozedUntil != null
        val r = store(ctx).modify(id) {
            it.copy(activeSince = now(), fullScreenShownAt = null, snoozedUntil = null)
        } ?: return
        // A snooze keeps the original end time; a fresh occurrence sets a new one.
        if (!wasSnoozed) endAt(r)?.let { setAlarm(ctx, ACTION_END, id, it) } ?: cancel(ctx, ACTION_END, id)
        // No delay (and always for alarms): go straight to the full-screen alarm.
        if (r.fx.effectiveFullScreen && r.fx.fullScreenDelayMinutes == 0) {
            val shown = store(ctx).modify(id) { it.copy(fullScreenShownAt = now()) } ?: return
            Notifier.show(ctx, shown, alert = true, fullScreen = true)
            armActive(ctx, shown)
            return
        }
        Notifier.show(ctx, r, alert = true)
        armActive(ctx, r)
    }

    /** (Re)arms the repeat-alert and full-screen alarms for an active reminder. */
    private fun armActive(ctx: Context, r: Reminder) {
        cancel(ctx, ACTION_NAG, r.id)
        cancel(ctx, ACTION_FULLSCREEN, r.id)
        val since = r.activeSince ?: return
        if (r.fx.effectiveNag) setAlarm(ctx, ACTION_NAG, r.id, now() + nagInterval(r))
        if (r.fx.effectiveFullScreen && r.fullScreenShownAt == null) {
            val at = since + r.fx.fullScreenDelayMinutes * MINUTE
            setAlarm(ctx, ACTION_FULLSCREEN, r.id, maxOf(at, now() + 1_000))
        }
    }

    private fun clearActive(ctx: Context, id: Int) {
        cancel(ctx, ACTION_NAG, id)
        cancel(ctx, ACTION_FULLSCREEN, id)
        Notifier.cancel(ctx, id)
    }

    private fun armFire(ctx: Context, r: Reminder) {
        val at = r.nextTriggerAt
        if (r.enabled && at != null) setAlarm(ctx, ACTION_FIRE, r.id, at) else cancel(ctx, ACTION_FIRE, r.id)
        // Alarms can post a heads-up a little before they ring.
        val pre = at?.minus(r.behavior.preAlertMinutes.coerceAtLeast(1) * MINUTE)
        if (r.enabled && r.kind == Kind.ALARM && r.behavior.preAlert && at != null && pre != null && at > now()) {
            if (pre > now()) setAlarm(ctx, ACTION_PREALERT, r.id, pre) else Notifier.showUpcoming(ctx, r)
        } else {
            cancel(ctx, ACTION_PREALERT, r.id)
            Notifier.cancelUpcoming(ctx, r.id)
        }
    }

    private fun nagInterval(r: Reminder) = r.fx.nagEveryMinutes.coerceAtLeast(1) * MINUTE

    private fun pending(ctx: Context, action: String, id: Int): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(action)
            // A distinct data URI keeps each (action, reminder) PendingIntent separate.
            .setData(Uri.parse("reminder://$id/$action"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun actionIntent(ctx: Context, action: String, id: Int) = pending(ctx, action, id)

    private fun setAlarm(ctx: Context, action: String, id: Int, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, action, id)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without exact-alarm access Android may delay this by several minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, action: String, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, action, id))
    }
}
