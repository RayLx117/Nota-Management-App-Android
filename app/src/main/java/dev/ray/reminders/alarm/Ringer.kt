package dev.ray.reminders.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import dev.ray.reminders.R
import dev.ray.reminders.data.Sounds

/**
 * Rings alarms and finished timers with the sound picked for them, looping until stopped.
 * A foreground service keeps Nota alive while it rings; it shows the alarm's own notification.
 */
object Ringer {
    /** The notification each ringing alarm posted, for the ringing service to wear. */
    val posted = java.util.concurrent.ConcurrentHashMap<Int, Notification>()

    private var player: MediaPlayer? = null
    private var vibrating = false
    /** Which notification is ringing, so the service can wear it. */
    private var ringingId = 0
    private var previewing = false

    fun start(ctx: Context, notificationId: Int, soundId: String?, sound: Boolean, vibrate: Boolean) {
        val app = ctx.applicationContext
        stopSound(app)
        ringingId = notificationId
        if (sound) play(app, soundId, loop = true)
        if (vibrate) {
            app.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
            vibrating = true
        }
        runCatching { app.startForegroundService(Intent(app, RingService::class.java).putExtra("id", notificationId)) }
    }

    /** Stops ringing, but only if [notificationId] is what's ringing (or always, when null). */
    fun stop(ctx: Context, notificationId: Int? = null) {
        if (notificationId != null && notificationId != ringingId) return
        val app = ctx.applicationContext
        stopSound(app)
        ringingId = 0
        // Drop the foreground notification right now, so a notification posted next under the same id survives.
        RingService.instance?.let { runCatching { it.stopForeground(Service.STOP_FOREGROUND_REMOVE) } }
        runCatching { app.stopService(Intent(app, RingService::class.java)) }
    }

    /** Plays a sound once so it can be heard before picking it. */
    fun preview(ctx: Context, soundId: String) {
        if (ringingId != 0) return
        stopSound(ctx.applicationContext)
        previewing = true
        play(ctx.applicationContext, soundId, loop = false)
    }

    fun stopPreview(ctx: Context) { if (previewing) stopSound(ctx.applicationContext) }

    private fun play(ctx: Context, soundId: String?, loop: Boolean) {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun open(uri: android.net.Uri) = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(ctx, uri)
            isLooping = loop
            setOnCompletionListener { if (!loop) { it.release(); if (player === it) player = null } }
            prepare()
            start()
        }
        player = runCatching { open(Sounds.uri(ctx, soundId)) }.getOrNull()
            ?: runCatching { open(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) }.getOrNull()
    }

    private fun stopSound(ctx: Context) {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        previewing = false
        if (vibrating) ctx.getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        vibrating = false
    }

    internal fun currentId() = ringingId
}

class RingService : Service() {
    companion object { var instance: RingService? = null }

    override fun onCreate() { super.onCreate(); instance = this }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", 0)?.takeIf { it != 0 } ?: Ringer.currentId()
        val nm = getSystemService(NotificationManager::class.java)
        // Wear the alarm's own notification so nothing extra shows up in the bar.
        // Use the exact notification the alarm just posted. Looking it up can race the post and fall back to
        // the quiet "Ringing" one, which replaced the alarm's heads-up banner when you were in another app.
        val n = Ringer.posted[id]
            ?: nm.activeNotifications.firstOrNull { it.id == id }?.notification
            ?: Notification.Builder(this, Notifier.CH_QUIET_ID).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ringing").build()
        runCatching {
            startForeground(id.takeIf { it != 0 } ?: 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
        if (Ringer.currentId() == 0) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
