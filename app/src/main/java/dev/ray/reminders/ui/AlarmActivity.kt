package dev.ray.reminders.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
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
import dev.ray.reminders.data.ReminderStore

/** The full-screen "take over the phone" alarm. Rings until Done or Snooze. */
class AlarmActivity : ComponentActivity() {

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
        // The sound and vibration come from the ringing service, so they keep going if this screen closes.

        setContent {
            RemindersTheme {
                val items by store.items.collectAsState()
                val r = items.firstOrNull { it.id == id }
                // Done/Snooze from the notification (or anywhere else) closes this screen too.
                LaunchedEffect(r?.isActive) { if (r == null || !r.isActive) finish() }
                if (r != null) {
                    Column(
                        Modifier.fillMaxSize().background(Nota.Bg).padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Dot(size = 14.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(Describe.time(r), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, color = Nota.Mint)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            r.title, textAlign = TextAlign.Center, color = Nota.Text,
                            fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                        )
                        if (r.notes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(r.notes, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Nota.Sub)
                        }
                        val picture = remember(r.imagePath) { r.imagePath?.let(Images::load)?.asImageBitmap() }
                        if (picture != null) {
                            Spacer(Modifier.height(24.dp))
                            Image(
                                picture, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)),
                            )
                        }
                        Spacer(Modifier.height(48.dp))
                        PrimaryButton("Done", onClick = { ReminderEngine.done(this@AlarmActivity, id) }, modifier = Modifier.fillMaxWidth(), height = 64.dp)
                        Spacer(Modifier.height(12.dp))
                        SoftButton(
                            "Snooze ${Describe.minutes(r.fx.alarmSnoozeMinutes)}",
                            onClick = { ReminderEngine.snooze(this@AlarmActivity, id, fromAlarm = true) }, modifier = Modifier.fillMaxWidth(), height = 56.dp,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun intent(ctx: Context, id: Int): Intent = Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(ReminderEngine.EXTRA_ID, id)
    }
}
