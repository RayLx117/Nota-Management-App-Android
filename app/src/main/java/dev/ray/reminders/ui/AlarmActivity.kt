package dev.ray.reminders.ui

import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
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
                        Modifier.fillMaxSize().background(Nota.Bg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Turning it off takes a deliberate slide at the top, so a sleepy tap can't do it.
                        SlideToStop { ReminderEngine.done(this@AlarmActivity, id) }
                        Spacer(Modifier.weight(1f))
                        Text(Describe.time(r), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 60.sp, color = Nota.Mint)
                        Spacer(Modifier.height(10.dp))
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
                            Spacer(Modifier.height(20.dp))
                            Image(
                                picture, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // The big, easy target: snooze.
                        Column(
                            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(36.dp)).background(Nota.Mint)
                                .clickable { ReminderEngine.snooze(this@AlarmActivity, id, fromAlarm = true) },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Snooze", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, color = Nota.Ink)
                            Text(
                                "for ${Describe.minutes(r.fx.alarmSnoozeMinutes)}", fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp, color = Nota.Ink.copy(alpha = 0.7f),
                            )
                        }
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

/** "Slide to turn off": drag the knob all the way to the right. Let go early and it springs back. */
@Composable
private fun SlideToStop(onStop: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val x = remember { Animatable(0f) }
    var max by remember { mutableFloatStateOf(1f) }
    var stopped by remember { mutableStateOf(false) }
    val knob = 60.dp
    val inset = 6.dp
    val hint = rememberInfiniteTransition(label = "hint").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "hintAlpha",
    )
    fun settle() = scope.launch {
        if (x.value > max * 0.85f) { x.animateTo(max); if (!stopped) { stopped = true; onStop() } }
        else x.animateTo(0f, spring(dampingRatio = 0.6f))
    }
    Box(
        Modifier.fillMaxWidth().height(knob + inset * 2).clip(CircleShape).background(Nota.Raised)
            .onSizeChanged { max = (it.width - with(density) { (knob + inset * 2).toPx() }).coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    change.consume()
                    scope.launch { x.snapTo((x.value + dx).coerceIn(0f, max)) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "Slide to turn off", fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Nota.Text,
            modifier = Modifier.align(Alignment.Center).padding(start = knob).graphicsLayer { alpha = hint.value * (1f - x.value / max) },
        )
        Box(
            Modifier.padding(inset).offset { IntOffset(x.value.roundToInt(), 0) }.size(knob).clip(CircleShape).background(Nota.Mint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Turn off", tint = Nota.Ink, modifier = Modifier.size(34.dp))
        }
    }
}
