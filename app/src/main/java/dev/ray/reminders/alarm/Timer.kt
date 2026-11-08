package dev.ray.reminders.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.ui.TimerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString

/** One countdown timer. endAt > 0 = running; remaining > 0 with endAt == 0 = paused; ringing = done. */
data class TimerState(
    val label: String = "",
    val durationMs: Long = 0,
    val endAt: Long = 0,
    val remaining: Long = 0,
    val ringing: Boolean = false,
    /** Sounds id; null = the default from Settings. */
    val sound: String? = null,
) {
    val running get() = endAt > 0 && !ringing
    val paused get() = endAt == 0L && remaining > 0 && !ringing
    val idle get() = !running && !paused && !ringing
    fun left(now: Long) = when {
        running -> (endAt - now).coerceAtLeast(0)
        paused -> remaining
        else -> 0
    }
}

@kotlinx.serialization.Serializable
data class TimerPreset(val name: String = "", val seconds: Int, val sound: String? = null)

object Timer {
    private val DEFAULT_PRESETS = listOf(TimerPreset(seconds = 600), TimerPreset(seconds = 900), TimerPreset(seconds = 1800))
    private var presetFlow: MutableStateFlow<List<TimerPreset>>? = null

    fun presets(ctx: Context): StateFlow<List<TimerPreset>> = presetFlow ?: MutableStateFlow(
        prefs(ctx).getString("presets", null)?.let {
            runCatching { dev.ray.reminders.data.ReminderStore.json.decodeFromString<List<TimerPreset>>(it) }.getOrNull()
        } ?: DEFAULT_PRESETS,
    ).also { presetFlow = it }

    fun setPresets(ctx: Context, list: List<TimerPreset>) {
        prefs(ctx).edit().putString("presets", dev.ray.reminders.data.ReminderStore.json.encodeToString(list)).apply()
        (presets(ctx) as MutableStateFlow).value = list
        dev.ray.reminders.widget.TimerWidget.refresh(ctx)
    }

    /** "10 min", "1 h 30 min", "45 s", "2 min 30 s". */
    fun shortDuration(seconds: Int): String {
        val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 }?.let { "$it s" })
            .joinToString(" ").ifEmpty { "0 s" }
    }

    const val ACTION_DONE = "dev.ray.reminders.timer.DONE"
    const val ACTION_PAUSE = "dev.ray.reminders.timer.PAUSE"
    const val ACTION_RESUME = "dev.ray.reminders.timer.RESUME"
    const val ACTION_CANCEL = "dev.ray.reminders.timer.CANCEL"
    const val ACTION_STOP = "dev.ray.reminders.timer.STOP"
    const val ACTION_ADD = "dev.ray.reminders.timer.ADD"
    const val ACTION_START_PRESET = "dev.ray.reminders.timer.START_PRESET"

    private const val CH_RUNNING = "timer_running"
    // Live Updates need a channel that isn't low importance; this one stays silent anyway.
    private const val CH_LIVE = "timer_live"
    // Silent: the ringing service plays the timer's sound (see Ringer).
    private const val CH_DONE = "timer_ring"
    private const val NOTIF_ID = 2_000_000

    private var flow: MutableStateFlow<TimerState>? = null

    fun state(ctx: Context): StateFlow<TimerState> = flow ?: MutableStateFlow(load(ctx)).also { flow = it }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("timer", Context.MODE_PRIVATE)

    private fun load(ctx: Context) = prefs(ctx).let {
        TimerState(
            it.getString("label", "") ?: "", it.getLong("duration", 0), it.getLong("endAt", 0),
            it.getLong("remaining", 0), it.getBoolean("ringing", false), it.getString("sound", null),
        )
    }

    private fun set(ctx: Context, s: TimerState) {
        prefs(ctx).edit().putString("label", s.label).putLong("duration", s.durationMs).putLong("endAt", s.endAt)
            .putLong("remaining", s.remaining).putBoolean("ringing", s.ringing).putString("sound", s.sound).apply()
        (state(ctx) as MutableStateFlow).value = s
        dev.ray.reminders.widget.TimerWidget.refresh(ctx)
    }

    /** Vibrate only, or sound + vibrate (remembered for every timer). */
    fun vibrateOnly(ctx: Context) = prefs(ctx).getBoolean("vibrateOnly", false)
    fun setVibrateOnly(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("vibrateOnly", on).apply()

    /** The wheels remember the last time you set. */
    fun lastDuration(ctx: Context) = prefs(ctx).getLong("last", 5 * 60_000L)

    fun start(ctx: Context, durationMs: Long, label: String, sound: String? = null) {
        if (durationMs <= 0) return
        prefs(ctx).edit().putLong("last", durationMs).apply()
        run(ctx, TimerState(label = label.trim(), durationMs = durationMs, sound = sound), durationMs)
    }

    private fun run(ctx: Context, base: TimerState, left: Long) {
        val s = base.copy(endAt = System.currentTimeMillis() + left, remaining = 0, ringing = false)
        set(ctx, s)
        schedule(ctx, s.endAt)
        showRunning(ctx, s)
    }

    fun pause(ctx: Context) {
        val s = state(ctx).value
        if (!s.running) return
        unschedule(ctx)
        val p = s.copy(endAt = 0, remaining = s.left(System.currentTimeMillis()).coerceAtLeast(1000))
        set(ctx, p)
        showRunning(ctx, p)
    }

    fun resume(ctx: Context) {
        val s = state(ctx).value
        if (s.paused) run(ctx, s, s.remaining)
    }

    /** Adds a minute; when it's ringing, starts a fresh minute. */
    fun addMinute(ctx: Context) {
        val s = state(ctx).value
        val now = System.currentTimeMillis()
        when {
            s.ringing -> { stopRinging(ctx); run(ctx, s.copy(durationMs = 60_000), 60_000) }
            s.running -> run(ctx, s.copy(durationMs = s.durationMs + 60_000), s.left(now) + 60_000)
            s.paused -> { val p = s.copy(durationMs = s.durationMs + 60_000, remaining = s.remaining + 60_000); set(ctx, p); showRunning(ctx, p) }
        }
    }

    fun cancel(ctx: Context) {
        unschedule(ctx)
        stopRinging(ctx)
        set(ctx, TimerState())
        nm(ctx).cancel(NOTIF_ID)
    }

    fun onDone(ctx: Context) {
        val s = state(ctx).value
        if (!s.running) return
        set(ctx, s.copy(ringing = true))
        showDone(ctx, state(ctx).value)
    }

    private fun stopRinging(ctx: Context) {
        Ringer.stop(ctx, NOTIF_ID)
        ctx.sendBroadcast(Intent(TimerActivity.ACTION_CLOSE).setPackage(ctx.packageName))
    }

    /** After a reboot: put the alarm back, or ring now if it ran out while the phone was off. */
    fun restore(ctx: Context) {
        val s = state(ctx).value
        when {
            s.running && s.endAt <= System.currentTimeMillis() -> onDone(ctx)
            s.running -> { schedule(ctx, s.endAt); showRunning(ctx, s) }
            s.paused -> showRunning(ctx, s)
            s.ringing -> showDone(ctx, s)
        }
    }

    // ---------------------------------------------------------------- alarms & notifications

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    private fun pi(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, ACTION_DONE))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, ACTION_DONE))
    }

    private fun unschedule(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, ACTION_DONE))

    private fun channels(ctx: Context) {
        listOf("timer_done", "timer_done_vibrate").forEach { if (nm(ctx).getNotificationChannel(it) != null) nm(ctx).deleteNotificationChannel(it) }
        nm(ctx).createNotificationChannels(
            listOf(
                NotificationChannel(CH_RUNNING, "Timer: counting down", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_LIVE, "Timer: live countdown", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
                NotificationChannel(CH_DONE, "Timer: time's up", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            ),
        )
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, NOTIF_ID, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "timer")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, title: String, action: String) =
        Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), title, pi(ctx, action)).build()

    private fun showRunning(ctx: Context, s: TimerState) {
        channels(ctx)
        val b = Notification.Builder(ctx, if (s.running) CH_LIVE else CH_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.label.ifBlank { "Timer" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(ctx))
            .setGroup("timer")
        if (s.running) {
            // Android 16 Live Update: shows on the lock screen's Now Bar and as a status bar chip.
            // Same extra NotificationCompat.setRequestPromotedOngoing() sets; ignored before Android 16.
            b.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(s.endAt).setShowWhen(true)
                .addAction(action(ctx, "Pause", ACTION_PAUSE))
                .addAction(action(ctx, "+1 min", ACTION_ADD))
        } else {
            b.setContentText("Paused · ${format(s.remaining)} left").setShowWhen(false)
                .addAction(action(ctx, "Resume", ACTION_RESUME))
        }
        b.addAction(action(ctx, "Cancel", ACTION_CANCEL))
        nm(ctx).notify(NOTIF_ID, b.build())
    }

    private fun showDone(ctx: Context, s: TimerState) {
        channels(ctx)
        val full = PendingIntent.getActivity(
            ctx, NOTIF_ID + 1, Intent(ctx, TimerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time's up")
            .setContentText(s.label.ifBlank { format(s.durationMs) + " timer" })
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setDeleteIntent(pi(ctx, ACTION_STOP))
            .addAction(action(ctx, "Stop", ACTION_STOP))
            .addAction(action(ctx, "+1 min", ACTION_ADD))
            .build()
        nm(ctx).notify(NOTIF_ID, n)
        Ringer.start(ctx, NOTIF_ID, s.sound, sound = !vibrateOnly(ctx), vibrate = true)
        // Screen already on and unlocked: Android shows a heads-up instead, so open the ringing screen ourselves
        // only when we're allowed to (the full-screen intent covers the locked case).
    }

    fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Timer.ACTION_DONE -> Timer.onDone(ctx)
            Timer.ACTION_PAUSE -> Timer.pause(ctx)
            Timer.ACTION_RESUME -> Timer.resume(ctx)
            Timer.ACTION_ADD -> Timer.addMinute(ctx)
            Timer.ACTION_CANCEL, Timer.ACTION_STOP -> Timer.cancel(ctx)
            Timer.ACTION_START_PRESET -> Timer.presets(ctx).value.getOrNull(intent.getIntExtra("preset", -1))?.let {
                if (!Timer.state(ctx).value.idle) Timer.cancel(ctx)
                Timer.start(ctx, it.seconds * 1000L, it.name, it.sound)
            }
        }
    }
}
