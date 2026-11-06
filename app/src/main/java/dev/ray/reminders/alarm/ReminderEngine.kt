package dev.ray.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.ray.reminders.data.Images
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
    const val EXTRA_ID = "id"

    private const val MINUTE = 60_000L

    private fun store(ctx: Context) = ReminderStore.get(ctx)
    private fun now() = System.currentTimeMillis()

    fun nextOccurrence(r: Reminder, afterMillis: Long): Long? =
        Recurrence.next(r, ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), ZoneId.systemDefault()))
            ?.toInstant()?.toEpochMilli()

    // ---------------------------------------------------------------- user actions

    /** Create or update a reminder from the editor. */
    fun save(ctx: Context, edited: Reminder) {
        store(ctx).get(edited.id)?.imagePath?.let { old -> if (old != edited.imagePath) Images.delete(old) }
        val next = if (edited.enabled) nextOccurrence(edited, now()) else null
        var r = edited.copy(nextTriggerAt = next)
        if (!r.enabled) r = r.copy(activeSince = null, fullScreenShownAt = null, snoozedUntil = null)
        store(ctx).upsert(r)
        armFire(ctx, r)
        if (r.snoozedUntil == null) cancel(ctx, ACTION_SNOOZE_END, r.id)
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
        listOf(ACTION_FIRE, ACTION_NAG, ACTION_FULLSCREEN, ACTION_SNOOZE_END).forEach { cancel(ctx, it, id) }
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
        clearActive(ctx, id)
    }

    fun snooze(ctx: Context, id: Int) {
        val r = store(ctx).get(id) ?: return
        val until = now() + r.behavior.snoozeMinutes.coerceAtLeast(1) * MINUTE
        store(ctx).modify(id) { it.copy(activeSince = null, fullScreenShownAt = null, snoozedUntil = until) }
        clearActive(ctx, id)
        setAlarm(ctx, ACTION_SNOOZE_END, id, until)
    }

    /** The notification was swiped away (or cleared). Sticky reminders come straight back. */
    fun swiped(ctx: Context, id: Int) {
        val r = store(ctx).get(id) ?: return
        if (!r.isActive) return
        if (r.behavior.sticky) Notifier.show(ctx, r, alert = false) else done(ctx, id)
    }

    // ---------------------------------------------------------------- alarm callbacks

    fun onAlarm(ctx: Context, action: String, id: Int) {
        val r = store(ctx).get(id) ?: return
        when (action) {
            ACTION_FIRE -> {
                if (!r.enabled) return
                // Schedule the following occurrence first, so a reminder left un-done
                // never blocks the next one.
                val next = nextOccurrence(r, maxOf(now(), r.nextTriggerAt ?: 0L))
                val updated = store(ctx).modify(id) { it.copy(nextTriggerAt = next) } ?: return
                armFire(ctx, updated)
                activate(ctx, id)
            }
            ACTION_SNOOZE_END -> if (r.snoozedUntil != null) activate(ctx, id)
            ACTION_NAG -> if (r.isActive && r.behavior.nag) {
                Notifier.show(ctx, r, alert = true, fullScreen = r.fullScreenShownAt != null)
                setAlarm(ctx, ACTION_NAG, id, now() + nagInterval(r))
            }
            ACTION_FULLSCREEN -> if (r.isActive && r.behavior.fullScreen) {
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
            current.snoozedUntil?.let { setAlarm(ctx, ACTION_SNOOZE_END, r.id, maxOf(it, now + 1_000)) }
            if (current.isActive) {
                Notifier.show(ctx, current, alert = false)
                armActive(ctx, current)
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private fun activate(ctx: Context, id: Int) {
        cancel(ctx, ACTION_SNOOZE_END, id)
        val r = store(ctx).modify(id) {
            it.copy(activeSince = now(), fullScreenShownAt = null, snoozedUntil = null)
        } ?: return
        Notifier.show(ctx, r, alert = true)
        armActive(ctx, r)
    }

    /** (Re)arms the repeat-alert and full-screen alarms for an active reminder. */
    private fun armActive(ctx: Context, r: Reminder) {
        cancel(ctx, ACTION_NAG, r.id)
        cancel(ctx, ACTION_FULLSCREEN, r.id)
        val since = r.activeSince ?: return
        if (r.behavior.nag) setAlarm(ctx, ACTION_NAG, r.id, now() + nagInterval(r))
        if (r.behavior.fullScreen && r.fullScreenShownAt == null) {
            val at = since + r.behavior.fullScreenAfterMinutes.coerceAtLeast(0) * MINUTE
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
    }

    private fun nagInterval(r: Reminder) = r.behavior.nagEveryMinutes.coerceAtLeast(1) * MINUTE

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
