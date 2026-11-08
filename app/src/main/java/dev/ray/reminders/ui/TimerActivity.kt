package dev.ray.reminders.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.Timer

/** Full-screen "time's up" for the timer, shown over the lock screen. */
class TimerActivity : ComponentActivity() {
    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (!Timer.state(this).value.ringing) { finish(); return }
        registerReceiver(closer, IntentFilter(ACTION_CLOSE), RECEIVER_NOT_EXPORTED)
        // The sound comes from the ringing service, which keeps going until Stop or +1 min.
        setContent {
            RemindersTheme {
                val t by Timer.state(this).collectAsState()
                LaunchedEffect(t.ringing) { if (!t.ringing) finish() }
                Column(
                    Modifier.fillMaxSize().background(Nota.Bg).padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Dot(size = 14.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Time's up", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = Nota.Mint)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t.label.ifBlank { Timer.format(t.durationMs) + " timer" }, textAlign = TextAlign.Center,
                        color = Nota.Text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                    )
                    Spacer(Modifier.height(48.dp))
                    PrimaryButton("Stop", onClick = { Timer.cancel(this@TimerActivity) }, modifier = Modifier.fillMaxWidth(), height = 64.dp)
                    Spacer(Modifier.height(12.dp))
                    SoftButton("+1 min", onClick = { Timer.addMinute(this@TimerActivity) }, modifier = Modifier.fillMaxWidth(), height = 56.dp)
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        super.onDestroy()
    }

    companion object { const val ACTION_CLOSE = "dev.ray.reminders.timer.CLOSE_SCREEN" }
}
