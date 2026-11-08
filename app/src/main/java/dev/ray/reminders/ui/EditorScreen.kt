package dev.ray.reminders.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.res.painterResource
import dev.ray.reminders.R
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.AlertStyle
import dev.ray.reminders.data.Behavior
import dev.ray.reminders.data.ImageStyle
import dev.ray.reminders.data.Kind
import dev.ray.reminders.data.Images
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.ReminderStore
import dev.ray.reminders.data.RepeatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate

private val ReminderSaver = Saver<Reminder, String>(
    save = { ReminderStore.json.encodeToString(it) },
    restore = { ReminderStore.json.decodeFromString<Reminder>(it) },
)

private const val DAY_MS = 86_400_000L

/** A new reminder/alarm started from the calendar: its date, time, and whether it repeats yearly. */
data class Prefill(val epochDay: Long, val minute: Int, val yearly: Boolean)

/** The old "Silent" alert style becomes "Alarm off" now that alarms have their own box. */
private fun migrateSilent(r: Reminder): Reminder =
    if (r.behavior.alertStyle != AlertStyle.SILENT) r
    else r.copy(behavior = r.behavior.copy(alarm = false, alertStyle = AlertStyle.SOUND_AND_VIBRATE))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(existing: Reminder?, kind: Kind, newId: () -> Int, onClose: () -> Unit, prefill: Prefill? = null) {
    val ctx = LocalContext.current
    var r by rememberSaveable(stateSaver = ReminderSaver) {
        mutableStateOf(
            existing?.let(::migrateSilent) ?: LocalDate.now().let { today ->
                val day = prefill?.let { LocalDate.ofEpochDay(it.epochDay) } ?: today
                Reminder(
                    id = newId(), kind = kind, title = "",
                    // From the calendar: that date, once (or every year).
                    repeat = when {
                        prefill != null -> if (prefill.yearly) RepeatType.YEARLY else RepeatType.ONCE
                        kind == Kind.ALARM -> RepeatType.ONCE
                        else -> RepeatType.DAILY
                    },
                    startEpochDay = day.toEpochDay(), hour = (prefill?.minute ?: 540) / 60, minute = (prefill?.minute ?: 540) % 60,
                    weekdays = setOf(day.dayOfWeek.value),
                    behavior = if (kind == Kind.REMINDER) Behavior(alarm = false) else Behavior(),
                    sound = dev.ray.reminders.data.Sounds.default(ctx).value,
                )
            },
        )
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askFullScreen by remember { mutableStateOf(false) }

    fun behavior(change: Behavior.() -> Behavior) { r = r.copy(behavior = r.behavior.change()) }
    fun save(): Reminder {
        var cleaned = r.copy(
            title = r.title.trim().ifEmpty { if (r.kind == Kind.ALARM) "Alarm" else "Reminder" }, notes = r.notes.trim(),
        )
        cleaned = rollAlarm(cleaned)
        // All-day reminders go off at 9 AM.
        if (cleaned.allDay) cleaned = cleaned.copy(hour = 9, minute = 0)
        // Weekly and daily have no date picker: make sure they start no later than today.
        if (cleaned.repeat == RepeatType.WEEKLY || cleaned.repeat == RepeatType.DAILY) {
            cleaned = cleaned.copy(startEpochDay = minOf(cleaned.startEpochDay, LocalDate.now().toEpochDay()))
        }
        ReminderEngine.save(ctx, cleaned)
        return cleaned
    }

    Column(
        Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------------------------------------------------------------- header
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nota.Text) }
            Text(
                when {
                    existing != null -> "Edit"
                    r.kind == Kind.ALARM -> "New alarm"
                    else -> "New reminder"
                }, modifier = Modifier.weight(1f),
                fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Nota.Text,
            )
            // Pin: save and put it in the notification bar right now.
            if (r.kind == Kind.REMINDER && r.behavior.notification) {
                Row(
                    Modifier.padding(end = 4.dp).clip(RoundedCornerShape(12.dp)).background(Nota.Raised)
                        .clickable { val saved = save(); ReminderEngine.testNow(ctx, saved.id); onClose() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(18.dp))
                    Text("Pin", color = Nota.Text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (existing != null) {
                Switch(checked = r.enabled, onCheckedChange = { r = r.copy(enabled = it) }, colors = notaSwitchColors())
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Nota.Sub) }
            }
        }

        if (r.kind == Kind.ALARM) {
            NotaField(r.title.takeIf { it != "Alarm" } ?: "", { r = r.copy(title = it) }, "Label", singleLine = true)
        } else {
            NotaField(r.title, { r = r.copy(title = it) }, "Title", singleLine = true)
            NotaField(r.notes, { r = r.copy(notes = it) }, "Notes")
        }

        // ---------------------------------------------------------------- schedule
        SectionLabel("REPEAT")
        // The repeat wheel and everything that depends on it live in one card.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(bottom = 14.dp),
        ) {
            LoopWheel(
                options = RepeatType.entries, selected = r.repeat, label = { it.label },
                onSelect = { if (it != r.repeat) r = r.copy(repeat = it) },
                modifier = Modifier.padding(top = 6.dp),
            )
            AnimatedContent(
                targetState = r.repeat,
                transitionSpec = {
                    // Slide in the direction the wheel moved (it wraps, so take the short way round).
                    val n = RepeatType.entries.size
                    val forward = (targetState.ordinal - initialState.ordinal + n) % n <= n / 2
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 3 * dir } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 * dir } + fadeOut(tween(160)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(260) })
                },
                label = "repeat settings",
            ) { type ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (type != RepeatType.ONCE) {
                        Stepper("Every", "${r.interval} ${type.unit(r.interval)}",
                            onMinus = { r = r.copy(interval = (r.interval - 1).coerceAtLeast(1)) },
                            onPlus = { r = r.copy(interval = (r.interval + 1).coerceAtMost(99)) })
                    }
                    when (type) {
                        // Weekly is anchored by the chosen days, so it has no date.
                        RepeatType.WEEKLY -> WeekdayPicker(r.weekdays) { day ->
                            val days = if (day in r.weekdays) r.weekdays - day else r.weekdays + day
                            if (days.isNotEmpty()) r = r.copy(weekdays = days)
                        }
                        // Daily runs from today, so its date row just says so.
                        RepeatType.DAILY -> ValueRow("Date", if (r.interval == 1) "Every day" else "Every ${r.interval} days", onClick = null)
                        else -> ValueRow("Date", Describe.date(r.startEpochDay)) { showDate = true }
                    }
                    if (r.kind == Kind.REMINDER) {
                        ToggleRow("All day", r.allDay) { r = r.copy(allDay = it) }
                        // Start and end grey out when it's all day.
                        Column(
                            Modifier.alpha(if (r.allDay) 0.35f else 1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ValueRow("Start time", Describe.clock(r.hour * 60 + r.minute), if (r.allDay) null else ({ showTime = true }))
                            ValueRow("End time", r.endMinute?.let(Describe::clock) ?: "None", if (r.allDay) null else ({ showEnd = true }))
                        }
                    } else {
                        ValueRow("Time", Describe.time(r)) { showTime = true }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- behaviour
        if (r.kind == Kind.ALARM) {
            SectionLabel("ALARM")
            AlarmOnlySettings(r, onChange = { r = it })
        } else {
            ReminderDelivery(r, onChange = { r = it })
        }

        Summary(r)

        PrimaryButton("Save", onClick = {
            // An alarm without the full-screen permission only shows as a notification: offer to fix that first.
            if ((r.kind == Kind.ALARM || r.fx.alarmOn) && r.enabled && !ctx.fullScreenAllowed()) askFullScreen = true
            else { save(); onClose() }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = r.startEpochDay * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { r = r.copy(startEpochDay = it / DAY_MS) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(r.hour, r.minute, DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { r = r.copy(hour = state.hour, minute = state.minute); showTime = false }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
    if (askFullScreen) {
        AlertDialog(
            onDismissRequest = { askFullScreen = false },
            title = { Text("Allow full-screen alarms?") },
            text = { Text("Nota needs this so the alarm can take over your screen when it rings. Without it, you'll only get a notification.") },
            confirmButton = {
                TextButton(onClick = { askFullScreen = false; save(); ctx.openFullScreenSettings(); onClose() }) {
                    Text("Allow", color = Nota.Mint)
                }
            },
            dismissButton = {
                TextButton(onClick = { askFullScreen = false; save(); onClose() }) { Text("Not now", color = Nota.Sub) }
            },
        )
    }
    if (showEnd) {
        val m = r.endMinute ?: ((r.hour * 60 + r.minute + 60) % (24 * 60))
        val state = rememberTimePickerState(m / 60, m % 60, DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { showEnd = false },
            title = { Text("End time") },
            confirmButton = {
                TextButton(onClick = { r = r.copy(endMinute = state.hour * 60 + state.minute); showEnd = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { r = r.copy(endMinute = null); showEnd = false }) { Text("No end time") }
            },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            confirmButton = {
                TextButton(onClick = { ReminderEngine.delete(ctx, r.id); confirmDelete = false; onClose() }) {
                    Text("Delete", color = Nota.Error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Two short lines: when it repeats, and when it will first go off. */
@Composable
private fun Summary(r: Reminder) {
    val first = remember(r) { ReminderEngine.nextOccurrence(rollAlarm(r), System.currentTimeMillis()) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(Nota.Shelf).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Dot(size = 9.dp, color = if (first == null) Nota.Error else Nota.Mint, modifier = Modifier.padding(top = 6.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(Describe.schedule(r), style = MaterialTheme.typography.titleSmall, color = Nota.Text)
            Text(
                if (first == null) "That time has passed. Pick a later one."
                else Describe.status(r.copy(nextTriggerAt = first, enabled = true, activeSince = null, snoozedUntil = null))
                    .replace("Next:", "First:"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (first == null) Nota.Error else Nota.Sub,
            )
        }
    }
}

/** Reminders take banner pictures only: a strip across the notification. */
@Composable
private fun BannerSection(r: Reminder, onChange: (Reminder) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val stored = withContext(Dispatchers.IO) { Images.import(ctx, uri) }
            failed = stored == null
            stored?.let { onChange(r.copy(imagePath = it, imageStyle = ImageStyle.BANNER)) }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val bitmap = remember(r.imagePath) { r.imagePath?.let(Images::load)?.asImageBitmap() }
    var finding by remember { mutableStateOf(false) }
    if (finding) FindPictureDialog(r.title, onPicked = { onChange(r.copy(imagePath = it, imageStyle = ImageStyle.BANNER)); finding = false }, onDismiss = { finding = false })
    if (bitmap != null) {
        Image(
            bitmap, contentDescription = "Banner", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(14.dp)).clickable(onClick = ::pick),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::pick) { Text("Change banner", color = Nota.Mint) }
            TextButton(onClick = { finding = true }) { Text("Find another", color = Nota.Mint) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onChange(r.copy(imagePath = null)) }) { Text("Remove", color = Nota.Sub) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Add Banner", onClick = ::pick, height = 44.dp, color = Nota.Mint, modifier = Modifier.weight(1f))
            SoftButton("Find a picture", onClick = { finding = true }, height = 44.dp, modifier = Modifier.weight(1f))
        }
    }
    if (failed) Text("Couldn't open that picture.", color = Nota.Error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NotaField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Raised,
            focusedContainerColor = Nota.Shelf, unfocusedContainerColor = Nota.Shelf,
            focusedLabelColor = Nota.Mint, unfocusedLabelColor = Nota.Sub, cursorColor = Nota.Mint,
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(
    title: String, checked: Boolean, indent: Boolean = false, enabled: Boolean = true, subtitle: String? = null,
    modifier: Modifier = Modifier,
    /** If set, tapping the row while it's disabled calls this (e.g. to explain why). */
    onLockedTap: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled || onLockedTap != null) { if (enabled) onChange(!checked) else onLockedTap?.invoke() }
            .padding(start = if (indent) 16.dp else 0.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (indent) Nota.Sub else Nota.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
        }
        Switch(
            checked = checked, onCheckedChange = if (enabled) onChange else null,
            enabled = enabled, colors = notaSwitchColors(),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Nota.Sub, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Nota.Text)
    }
}

@Composable
private fun Stepper(
    label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit,
    indent: Boolean = false, enabled: Boolean = true,
) {
    // Disabled steppers stay visible but greyed out until their option is switched on.
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f).padding(start = if (indent) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (indent) Nota.Sub else Nota.Text, modifier = Modifier.weight(1f))
        StepButton("−", onMinus, enabled)
        Text(
            valueText, modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Nota.Text,
        )
        StepButton("+", onPlus, enabled)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Nota.Raised).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Nota.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(7, 1, 2, 3, 4, 5, 6).forEach { day -> // Sunday first
            val on = day in selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (on) Nota.Mint else Nota.Raised).clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Describe.dayName(day, java.time.format.TextStyle.NARROW),
                    color = if (on) Nota.Ink else Nota.Sub, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Minute steps that stay comfortable: 1-min steps up to 10, 5 up to 60, 15 up to 3 h, then 60. */
private fun stepMinutes(value: Int, dir: Int, min: Int): Int {
    val step = when {
        dir > 0 && value < 10 || dir < 0 && value <= 10 -> 1
        dir > 0 && value < 60 || dir < 0 && value <= 60 -> 5
        dir > 0 && value < 180 || dir < 0 && value <= 180 -> 15
        else -> 60
    }
    return (value + dir * step).coerceIn(min, 24 * 60)
}

private val RepeatType.label: String
    get() = when (this) {
        RepeatType.ONCE -> "Once"
        RepeatType.DAILY -> "Daily"
        RepeatType.WEEKLY -> "Weekly"
        RepeatType.MONTHLY -> "Monthly"
        RepeatType.YEARLY -> "Yearly"
    }

private fun RepeatType.unit(n: Int): String {
    val base = when (this) {
        RepeatType.DAILY -> "day"; RepeatType.WEEKLY -> "week"
        RepeatType.MONTHLY -> "month"; RepeatType.YEARLY -> "year"; RepeatType.ONCE -> ""
    }
    return if (n == 1) base else base + "s"
}

val AlertStyle.label: String
    get() = when (this) {
        AlertStyle.SOUND_AND_VIBRATE -> "Sound + vibrate"
        AlertStyle.VIBRATE_ONLY -> "Vibrate only"
        AlertStyle.SOUND_ONLY -> "Sound only"
        AlertStyle.SILENT -> "Silent"
    }

// ------------------------------------------------------------------------ Notification / Alarm

/** Reminders: always a notification, plus an optional alarm (checkbox). */
@Composable
private fun ReminderDelivery(r: Reminder, onChange: (Reminder) -> Unit) {
    val b = r.behavior
    fun update(nb: Behavior) = onChange(r.copy(behavior = nb))

    SectionLabel("NOTIFICATION")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToggleRow("Silent", b.notificationSilent) { update(b.copy(notificationSilent = it)) }
        ToggleRow("Pin until Dismissed", b.sticky) { update(b.copy(sticky = it)) }
        BannerSection(r, onChange)
    }

    // Alarm: a checkbox; its settings stay visible but greyed out until it's checked.
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
            .clickable { update(b.copy(alarm = !b.alarmOn, alertStyle = b.alertStyle.takeIf { it != AlertStyle.SILENT } ?: AlertStyle.SOUND_AND_VIBRATE)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = b.alarmOn,
            onCheckedChange = { update(b.copy(alarm = it, alertStyle = b.alertStyle.takeIf { s -> s != AlertStyle.SILENT } ?: AlertStyle.SOUND_AND_VIBRATE)) },
            colors = CheckboxDefaults.colors(checkedColor = Nota.Mint, checkmarkColor = Nota.Ink, uncheckedColor = Nota.Outline),
        )
        Text("ALARM", style = Nota.Label, color = if (b.alarmOn) Nota.Text else Nota.Sub)
    }
    Box {
        Column(
            Modifier.fillMaxWidth().alpha(if (b.alarmOn) 1f else 0.35f)
                .clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { AlarmSettings(r, onChange) }
        // Greyed out: tapping the settings checks the box.
        if (!b.alarmOn) Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { update(b.copy(alarm = true, alertStyle = b.alertStyle.takeIf { it != AlertStyle.SILENT } ?: AlertStyle.SOUND_AND_VIBRATE)) },
        )
    }
}

@Composable
private fun AlarmSettings(r: Reminder, onChange: (Reminder) -> Unit) {
    val b = r.behavior
    fun update(nb: Behavior) = onChange(r.copy(behavior = nb))
    SoundPicker(b.alertStyle, onPick = { update(b.copy(alertStyle = it)) })
    AudioRow(r.sound, onPick = { onChange(r.copy(sound = it)) }, enabled = b.alertStyle != AlertStyle.VIBRATE_ONLY)
    Stepper("Snooze", Describe.minutes(b.alarmSnoozeMinutes),
        onMinus = { update(b.copy(alarmSnoozeMinutes = stepMinutes(b.alarmSnoozeMinutes, -1, min = 1))) },
        onPlus = { update(b.copy(alarmSnoozeMinutes = stepMinutes(b.alarmSnoozeMinutes, +1, min = 1))) })
    // Delay counts from the notification appearing, so it needs the Notification box.
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var explain by remember { mutableStateOf(false) }
    LaunchedEffect(explain) { if (explain) { delay(2500); explain = false } }
    val delayAllowed = b.notification
    ToggleRow(
        "Delay alarm", b.fullScreenDelay && delayAllowed, enabled = delayAllowed,
        subtitle = "Wait after the notification appears before the alarm takes over",
        modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
        onLockedTap = if (!delayAllowed) {
            {
                explain = true
                scope.launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = 400
                        -24f at 50; 24f at 120; -16f at 190; 16f at 260; -8f at 330
                    })
                }
            }
        } else null,
    ) { update(b.copy(fullScreenDelay = it)) }
    AnimatedVisibility(explain) {
        Text(
            "Notification reminder must be on for this feature",
            color = Nota.Error, style = MaterialTheme.typography.bodySmall,
        )
    }
    Stepper(
        "Wait", Describe.minutes(b.fullScreenAfterMinutes.coerceAtLeast(1)), indent = true,
        enabled = b.fullScreenDelay && delayAllowed,
        onMinus = { update(b.copy(fullScreenAfterMinutes = stepMinutes(b.fullScreenAfterMinutes, -1, min = 1))) },
        onPlus = { update(b.copy(fullScreenAfterMinutes = stepMinutes(b.fullScreenAfterMinutes, +1, min = 1))) },
    )
}

@Composable
fun SoundPicker(
    current: AlertStyle,
    options: List<AlertStyle> = listOf(AlertStyle.SOUND_AND_VIBRATE, AlertStyle.SOUND_ONLY, AlertStyle.VIBRATE_ONLY),
    onPick: (AlertStyle) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Sound type: no title, just the choice and a green arrow. Opening it extends the same
    // grey box downward to reveal the other options, rather than a separate popup.
    val arrow by animateFloatAsState(if (menu) 180f else 0f, label = "arrow")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { menu = !menu }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.label, style = MaterialTheme.typography.bodyLarge, color = Nota.Text, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown, contentDescription = "Choose sound", tint = Nota.Mint,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = arrow },
            )
        }
        if (menu) {
            options
                .filter { it != current }
                .forEach { style ->
                    HorizontalDivider(color = Nota.Outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    Text(
                        style.label, style = MaterialTheme.typography.bodyLarge, color = Nota.Sub,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(style); menu = false }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
        }
    }
}

/** Settings for an alarm (Alarms tab): sound, snooze, and an optional heads-up before it rings. */
@Composable
private fun AlarmOnlySettings(r: Reminder, onChange: (Reminder) -> Unit) {
    val b = r.behavior
    fun update(nb: Behavior) = onChange(r.copy(behavior = nb))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SoundPicker(if (b.alertStyle == AlertStyle.SILENT) AlertStyle.SOUND_AND_VIBRATE else b.alertStyle, onPick = {
            update(b.copy(alertStyle = it))
        })
        AudioRow(r.sound, onPick = { onChange(r.copy(sound = it)) }, enabled = b.alertStyle != AlertStyle.VIBRATE_ONLY)
        Stepper("Snooze", Describe.minutes(b.alarmSnoozeMinutes),
            onMinus = { update(b.copy(alarmSnoozeMinutes = stepMinutes(b.alarmSnoozeMinutes, -1, min = 1))) },
            onPlus = { update(b.copy(alarmSnoozeMinutes = stepMinutes(b.alarmSnoozeMinutes, +1, min = 1))) })
        ToggleRow(
            "Notify before it rings", b.preAlert,
            subtitle = "A quiet notification ahead of time, with an option to skip",
        ) { update(b.copy(preAlert = it)) }
        Stepper("Before", Describe.minutes(b.preAlertMinutes), indent = true, enabled = b.preAlert,
            onMinus = { update(b.copy(preAlertMinutes = stepMinutes(b.preAlertMinutes, -1, min = 1))) },
            onPlus = { update(b.copy(preAlertMinutes = stepMinutes(b.preAlertMinutes, +1, min = 1))) })
    }
}

private fun rollAlarm(r: Reminder): Reminder = ReminderEngine.rollOnceAlarm(r)
