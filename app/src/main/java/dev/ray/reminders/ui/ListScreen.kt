package dev.ray.reminders.ui

import dev.ray.reminders.data.RepeatType
import android.Manifest
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import dev.ray.reminders.R
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.Reminder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.key

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ListScreen(reminders: List<Reminder>, onAdd: () -> Unit, onEdit: (Int) -> Unit, onCalendar: () -> Unit) {
    val pinned = reminders.filter { it.isActive }.sortedBy { it.activeSince }
    val upcoming = reminders.filterNot { it.isActive }
    // Every reminder lives in both places; each side only shows the ones that belong there, so a
    // reminder that gets pinned (or dismissed) can glide between the shelf and Coming up.
    val shelfOrder = reminders.sortedBy { it.activeSince ?: Long.MAX_VALUE }
    val listOrder = reminders.sortedWith(
        compareByDescending<Reminder> { it.enabled }.thenBy { it.snoozedUntil ?: it.nextTriggerAt ?: Long.MAX_VALUE },
    )
    val sel = rememberSelection(reminders.map { it.id }.toSet())
    SharedTransitionLayout(Modifier.fillMaxSize().background(Nota.Bg)) {
    val fx = this
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
            item { Shelf(pinned, shelfOrder, fx, sel, onEdit, onCalendar) }
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PermissionCards()
                }
            }
            if (upcoming.isEmpty()) item {
                Text(
                    if (reminders.isEmpty()) "No reminders yet. Tap New reminder to add your first one."
                    else "Nothing else scheduled.",
                    style = MaterialTheme.typography.bodyMedium, color = Nota.Sub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            // Grouped by how often they come back: daily at the top, yearly (then one-time) at the bottom.
            REPEAT_SECTIONS.forEach { (type, title) ->
                val group = listOrder.filter { it.repeat == type }
                if (group.isEmpty()) return@forEach
                item(key = "section-$type") {
                    AnimatedVisibility(group.any { !it.isActive }, enter = SLIDE_IN, exit = SLIDE_OUT) {
                        RepeatHeader(title, group.count { !it.isActive })
                    }
                }
                items(group, key = { it.id }) { r ->
                    AnimatedVisibility(!r.isActive, enter = SLIDE_IN, exit = SLIDE_OUT) {
                        UpcomingRow(r, sel, onClick = { onEdit(r.id) }, modifier = Modifier.glide(fx, r.id, this))
                    }
                }
            }
        }
        if (!sel.active) AddButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    }
}

private val REPEAT_SECTIONS = listOf(
    RepeatType.DAILY to "Daily", RepeatType.WEEKLY to "Weekly", RepeatType.MONTHLY to "Monthly",
    RepeatType.YEARLY to "Yearly", RepeatType.ONCE to "One time",
)

/** A section title with the theme's accent underline. */
@Composable
private fun RepeatHeader(title: String, count: Int) {
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            Text(title, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Nota.Text)
            Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Nota.Mint))
        }
        Text("$count", style = MaterialTheme.typography.bodySmall, color = Nota.Sub, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
    }
}

private val SLIDE_IN = fadeIn(tween(300)) + expandVertically(tween(380, easing = FastOutSlowInEasing))
private val SLIDE_OUT = fadeOut(tween(200)) + shrinkVertically(tween(380, easing = FastOutSlowInEasing))

/** The same reminder on the shelf and in Coming up share bounds, so it slides from one to the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.glide(fx: SharedTransitionScope, id: Int, scope: AnimatedVisibilityScope): Modifier = with(fx) {
    this@glide.sharedBounds(
        rememberSharedContentState("reminder-$id"), scope,
        boundsTransform = { _, _ -> tween(420, easing = FastOutSlowInEasing) },
    )
}

/** Round mint-to-cyan "+" button for a new reminder or alarm. */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(62.dp)
            .shadow(14.dp, CircleShape, ambientColor = Nota.Cyan, spotColor = Nota.Cyan)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Nota.Mint, Nota.Cyan)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Nota.Ink, fontFamily = Nota.Outfit, fontSize = 34.sp, fontWeight = FontWeight.Normal)
    }
}

/** The top "shelf": reminders currently sitting in your notification bar. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Shelf(
    pinned: List<Reminder>, all: List<Reminder>, fx: SharedTransitionScope, sel: Selection, onEdit: (Int) -> Unit, onCalendar: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Nota.Shelf)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
    ) {
        if (sel.active) SelectionBar(sel, "reminder", Modifier.padding(top = 0.dp))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Text("Reminders", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Nota.Text)
                Dot(size = 9.dp, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
            }
            CalendarButton(onCalendar, Modifier.padding(end = if (pinned.isNotEmpty()) 10.dp else 0.dp))
            if (pinned.isNotEmpty()) {
                Text(
                    "${pinned.size} waiting", color = Nota.Ink, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Nota.Mint).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Text("PINNED TO YOUR BAR", style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
        AnimatedVisibility(pinned.isEmpty(), enter = SLIDE_IN, exit = SLIDE_OUT) {
            Text("Nothing waiting on you right now.", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub)
        }
        Column {
            all.forEach { r ->
                key(r.id) {
                    AnimatedVisibility(r.isActive, enter = SLIDE_IN, exit = SLIDE_OUT) {
                        PinnedBar(
                            r, sel, onClick = { onEdit(r.id) },
                            modifier = Modifier.padding(top = if (r.id == pinned.firstOrNull()?.id) 0.dp else 12.dp).glide(fx, r.id, this),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedBar(r: Reminder, sel: Selection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nota.Raised).selectable(sel, r.id, onClick)
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sel.active) SelectCircle(sel.has(r.id))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Describe.pinnedLine(r), style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
            }
            if (!sel.active) RoundButton("✓", filled = true, onClick = { ReminderEngine.done(ctx, r.id) })
        }
        Dot(
            size = 14.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).border(3.dp, Nota.Shelf, CircleShape),
        )
    }
}

/** A row in "Coming up": coloured line for the repeat type, time on the right. */
@Composable
private fun UpcomingRow(r: Reminder, sel: Selection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val alpha = if (r.enabled) 1f else 0.45f
    Column(modifier.fillMaxWidth().background(Nota.Bg).selectable(sel, r.id, onClick).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sel.active) SelectCircle(sel.has(r.id), Modifier.padding(end = 12.dp))
            Row(Modifier.weight(1f).fillMaxHeight().alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Nota.accent(r.repeat)))
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Describe.shortSchedule(r), style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
            }
            val (time, day) = Describe.whenParts(r)
            Column(horizontalAlignment = Alignment.End) {
                Text(time, fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Nota.Text)
                Text(day, style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
            }
            // Pin: put it in the notification bar right now (only if it uses notifications).
            // Same round outline style as the snooze button on the shelf.
            if (r.behavior.notification && !sel.active) {
                val ctx = LocalContext.current
                Box(
                    Modifier.padding(start = 12.dp).size(42.dp).clip(CircleShape).border(2.dp, Nota.Outline, CircleShape)
                        .clickable { ReminderEngine.testNow(ctx, r.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_pin), contentDescription = "Pin", tint = Nota.Mint, modifier = Modifier.size(18.dp))
                }
            }
            }
        }
        HorizontalDivider(color = Nota.Line)
    }
}

/** Android 14+ needs a few permissions for this to work fully; show what's missing and how to fix it. */
@Composable
private fun PermissionCards() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Re-checked every time the app comes back to the foreground (e.g. from Settings).
    val (notificationsOk, exactOk) = remember(refresh) {
        Pair(
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notificationsOk) PermissionCard(
            "Notifications are off",
            "Needed to show reminders.",
            "Allow",
        ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (!exactOk) PermissionCard(
            "Exact timing is off",
            "Reminders may arrive a few minutes late.",
            "Open settings",
        ) { ctx.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nota.Raised)
            .border(1.5.dp, Nota.Error.copy(alpha = 0.6f), RoundedCornerShape(18.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(size = 9.dp, color = Nota.Error)
            Text(title, style = MaterialTheme.typography.titleSmall, color = Nota.Text, modifier = Modifier.padding(start = 8.dp))
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.padding(vertical = 8.dp))
        SoftButton(button, onClick = onClick, height = 42.dp, color = Nota.Mint)
    }
}

fun Context.openSettings(action: String) {
    startActivity(Intent(action, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.fullScreenAllowed(): Boolean =
    getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

fun Context.openFullScreenSettings() = openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)

/** Bold red warning on the Alarms tab when Android won't let alarms take over the screen. */
@Composable
fun FullScreenWarning(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val ok = remember(refresh) { ctx.fullScreenAllowed() }
    if (ok) return
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nota.Error.copy(alpha = 0.16f))
            .border(2.dp, Nota.Error, RoundedCornerShape(20.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(size = 12.dp, color = Nota.Error)
            Text(
                "Full-screen alarms are blocked", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp, color = Nota.Text, modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            "Your alarms can't take over the screen when they ring.",
            style = MaterialTheme.typography.bodyMedium, color = Nota.Text,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Nota.Error)
                .clickable { ctx.openFullScreenSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Allow full-screen alarms", color = Nota.Ink, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}
