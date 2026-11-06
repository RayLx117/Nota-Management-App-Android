package dev.ray.reminders.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import dev.ray.reminders.MainActivity
import dev.ray.reminders.R
import dev.ray.reminders.data.AlertStyle
import dev.ray.reminders.data.ImageStyle
import dev.ray.reminders.data.Images
import android.view.View
import android.widget.RemoteViews
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.ui.AlarmActivity

object Notifier {

    // Channel settings can't be changed by the app after creation, so each alert style has its own.
    private const val CH_SOUND = "alert_sound"
    private const val CH_VIBRATE = "alert_vibrate"
    private const val CH_SOUND_ONLY = "alert_sound_only"
    private const val CH_NOTIFY = "notification_sound"
    private const val CH_QUIET = "quiet"
    private const val CH_ALARM = "alarm"

    private val VIBRATION = longArrayOf(0, 400, 250, 400, 250, 400)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_SOUND, "Reminders: sound + vibration", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                },
                NotificationChannel(CH_VIBRATE, "Reminders: vibration only", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                },
                NotificationChannel(CH_SOUND_ONLY, "Reminders: sound only", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(false)
                },
                NotificationChannel(CH_NOTIFY, "Reminders: notification sound", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_QUIET, "Reminders: silent", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Silent reminders, and sticky reminders brought back after a swipe"
                },
                NotificationChannel(CH_ALARM, "Full-screen alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                },
            ),
        )
    }

    /**
     * Posts (or re-posts) the notification for an active reminder.
     * @param alert make noise/vibrate according to the reminder's alert style; false = update quietly.
     * @param fullScreen attach a full-screen intent that opens the alarm screen.
     */
    fun show(ctx: Context, r: Reminder, alert: Boolean, fullScreen: Boolean = false) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val style = r.behavior.alertStyle
        val channel = when {
            fullScreen && alert -> CH_ALARM
            !alert -> CH_QUIET
            !r.behavior.alarmOn -> if (r.behavior.notificationSilent) CH_QUIET else CH_NOTIFY
            style == AlertStyle.VIBRATE_ONLY -> CH_VIBRATE
            style == AlertStyle.SOUND_ONLY -> CH_SOUND_ONLY
            else -> CH_SOUND
        }

        val openApp = PendingIntent.getActivity(
            ctx, r.id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_check), "Done",
            ReminderEngine.actionIntent(ctx, ReminderEngine.ACTION_DONE, r.id),
        ).build()
        val snooze = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_snooze), "Snooze ${r.behavior.snoozeMinutes} min",
            ReminderEngine.actionIntent(ctx, ReminderEngine.ACTION_SNOOZE, r.id),
        ).build()

        val text = r.notes
        val builder = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(text)
            .setCategory(if (fullScreen) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setWhen(r.activeSince ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(r.behavior.effectiveSticky)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alert)
            // Android bundles several ungrouped notifications from one app behind a dropdown.
            // A group of its own per reminder (with no summary) keeps each one separate.
            .setGroup("reminder-${r.id}")
            .setContentIntent(openApp)
            // Android 14+ lets people swipe away even "ongoing" notifications while unlocked.
            // We get told when that happens and put sticky reminders straight back.
            .setDeleteIntent(ReminderEngine.actionIntent(ctx, ReminderEngine.ACTION_SWIPED, r.id))
            .addAction(done)
            .addAction(snooze)

        // Pictures belong to the Notification box.
        val picture = if (r.behavior.notification) r.imagePath?.let(Images::load) else null
        if (picture != null && r.imageStyle == ImageStyle.BANNER) {
            // A picture strip across the notification, with the title on top of it.
            val strip = Images.banner(picture)
            val collapsed = RemoteViews(ctx.packageName, R.layout.notification_banner_collapsed).apply {
                setImageViewBitmap(R.id.banner, strip)
                setTextViewText(R.id.title, r.title)
            }
            val expanded = RemoteViews(ctx.packageName, R.layout.notification_banner_expanded).apply {
                setImageViewBitmap(R.id.banner, strip)
                setTextViewText(R.id.title, r.title)
                setTextViewText(R.id.notes, text)
                setViewVisibility(R.id.notes, if (text.isBlank()) View.GONE else View.VISIBLE)
            }
            builder.setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed)
                .setCustomHeadsUpContentView(collapsed)
                .setCustomBigContentView(expanded)
        } else if (picture != null) {
            // Thumbnail on the right when collapsed, full-width banner when expanded.
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(picture)
                    .showBigPictureWhenCollapsed(true)
                    .setSummaryText(text),
            )
        } else {
            builder.setStyle(Notification.BigTextStyle().bigText(text))
        }

        if (fullScreen) {
            val alarm = PendingIntent.getActivity(
                ctx, r.id, AlarmActivity.intent(ctx, r.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(alarm, true)
        }
        if (alert) nm.cancel(r.id) // a fresh post re-alerts reliably and moves it to the top
        nm.notify(r.id, builder.build())
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
