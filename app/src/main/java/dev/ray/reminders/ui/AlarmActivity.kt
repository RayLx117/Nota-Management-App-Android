package dev.ray.reminders.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.ray.reminders.data.Images
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.AlertStyle
import dev.ray.reminders.data.ReminderStore

/** The full-screen "take over the phone" alarm. Rings until Done or Snooze. */
class AlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val id = intent.getIntExtra(ReminderEngine.EXTRA_ID, -1)
        val store = ReminderStore.get(this)
        val reminder = store.get(id)
        if (reminder == null || !reminder.isActive) {
            finish(); return
        }
        startAlarm(reminder.behavior.alertStyle)

        setContent {
            RemindersTheme {
                val items by store.items.collectAsState()
                val r = items.firstOrNull { it.id == id }
                // Done/Snooze from the notification (or anywhere else) closes this screen too.
                LaunchedEffect(r?.isActive) { if (r == null || !r.isActive) finish() }
                if (r != null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(Describe.time(r), fontSize = 64.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                r.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (r.notes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    r.notes, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            val picture = remember(r.imagePath) { r.imagePath?.let(Images::load)?.asImageBitmap() }
                            if (picture != null) {
                                Spacer(Modifier.height(24.dp))
                                Image(
                                    picture, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                                )
                            }
                            Spacer(Modifier.height(48.dp))
                            Button(
                                onClick = { ReminderEngine.done(this@AlarmActivity, id) },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) { Text("Done", fontSize = 22.sp) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { ReminderEngine.snooze(this@AlarmActivity, id) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Snooze ${Describe.minutes(r.behavior.snoozeMinutes)}") }
                        }
                    }
                }
            }
        }
    }

    private fun startAlarm(style: AlertStyle) {
        if (style == AlertStyle.SOUND_AND_VIBRATE) {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                ?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                    isLooping = true
                    play()
                }
        }
        if (style != AlertStyle.SILENT) {
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
            vibrating = true
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        if (vibrating) getSystemService(VibratorManager::class.java).cancel()
        super.onDestroy()
    }

    companion object {
        fun intent(ctx: Context, id: Int): Intent = Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(ReminderEngine.EXTRA_ID, id)
    }
}
