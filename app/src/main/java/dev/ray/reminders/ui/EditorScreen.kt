package dev.ray.reminders.ui

import android.text.format.DateFormat
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(existing: Reminder?, newId: () -> Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var r by rememberSaveable(stateSaver = ReminderSaver) {
        mutableStateOf(
            existing ?: LocalDate.now().let { today ->
                Reminder(
                    id = newId(), title = "", repeat = RepeatType.DAILY,
                    startEpochDay = today.toEpochDay(), hour = 9, minute = 0,
                    weekdays = setOf(today.dayOfWeek.value),
                )
            },
        )
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun behavior(change: Behavior.() -> Behavior) { r = r.copy(behavior = r.behavior.change()) }
    fun save(): Reminder {
        val cleaned = r.copy(title = r.title.trim().ifEmpty { "Reminder" }, notes = r.notes.trim())
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
                if (existing == null) "New reminder" else "Edit", modifier = Modifier.weight(1f),
                fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Nota.Text,
            )
            if (existing != null) {
                Switch(checked = r.enabled, onCheckedChange = { r = r.copy(enabled = it) }, colors = notaSwitchColors())
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Nota.Sub) }
            }
        }

        NotaField(r.title, { r = r.copy(title = it) }, "Title", singleLine = true)
        NotaField(r.notes, { r = r.copy(notes = it) }, "Notes")
        PictureSection(r, onChange = { r = it })

        // ---------------------------------------------------------------- schedule
        SectionLabel("REPEAT")
        ChipRow {
            RepeatType.entries.forEach { type ->
                DotChip(type.label, r.repeat == type) { r = r.copy(repeat = type) }
            }
        }
        if (r.repeat != RepeatType.ONCE) {
            Stepper("Every", "${r.interval} ${r.repeat.unit(r.interval)}",
                onMinus = { r = r.copy(interval = (r.interval - 1).coerceAtLeast(1)) },
                onPlus = { r = r.copy(interval = (r.interval + 1).coerceAtMost(99)) })
        }
        if (r.repeat == RepeatType.WEEKLY) {
            WeekdayPicker(r.weekdays) { day ->
                val days = if (day in r.weekdays) r.weekdays - day else r.weekdays + day
                if (days.isNotEmpty()) r = r.copy(weekdays = days)
            }
        }
        ValueRow(if (r.repeat == RepeatType.ONCE) "Date" else "Starts", Describe.date(r.startEpochDay)) { showDate = true }
        ValueRow("Time", Describe.time(r)) { showTime = true }

        // ---------------------------------------------------------------- behaviour
        SectionLabel("ALERT")
        ChipRow {
            AlertStyle.entries.forEach { style ->
                DotChip(style.label, r.behavior.alertStyle == style) { behavior { copy(alertStyle = style) } }
            }
        }
        ToggleRow("Pin until Done", r.behavior.sticky) { behavior { copy(sticky = it) } }
        ToggleRow("Repeat alert", r.behavior.nag) { behavior { copy(nag = it) } }
        Stepper("Every", Describe.minutes(r.behavior.nagEveryMinutes), indent = true, enabled = r.behavior.nag,
            onMinus = { behavior { copy(nagEveryMinutes = stepMinutes(nagEveryMinutes, -1, min = 1)) } },
            onPlus = { behavior { copy(nagEveryMinutes = stepMinutes(nagEveryMinutes, +1, min = 1)) } })
        ToggleRow("Full-screen alarm", r.behavior.fullScreen) { behavior { copy(fullScreen = it) } }
        ToggleRow(
            "Delay alarm", r.behavior.fullScreenDelay, indent = true, enabled = r.behavior.fullScreen,
            subtitle = "Wait after the notification appears before the alarm takes over",
        ) { behavior { copy(fullScreenDelay = it) } }
        Stepper(
            "Wait", Describe.minutes(r.behavior.fullScreenAfterMinutes.coerceAtLeast(1)), indent = true,
            enabled = r.behavior.fullScreen && r.behavior.fullScreenDelay,
            onMinus = { behavior { copy(fullScreenAfterMinutes = stepMinutes(fullScreenAfterMinutes, -1, min = 1)) } },
            onPlus = { behavior { copy(fullScreenAfterMinutes = stepMinutes(fullScreenAfterMinutes, +1, min = 1)) } },
        )
        Stepper("Snooze", Describe.minutes(r.behavior.snoozeMinutes),
            onMinus = { behavior { copy(snoozeMinutes = stepMinutes(snoozeMinutes, -1, min = 1)) } },
            onPlus = { behavior { copy(snoozeMinutes = stepMinutes(snoozeMinutes, +1, min = 1)) } })

        Summary(r)

        PrimaryButton("Save", onClick = { save(); onClose() }, modifier = Modifier.fillMaxWidth())
        SoftButton(
            "Save and start", modifier = Modifier.fillMaxWidth(),
            onClick = { val saved = save(); ReminderEngine.testNow(ctx, saved.id); onClose() },
        )
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
    val first = remember(r) { ReminderEngine.nextOccurrence(r, System.currentTimeMillis()) }
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

@Composable
private fun PictureSection(r: Reminder, onChange: (Reminder) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val stored = withContext(Dispatchers.IO) { Images.import(ctx, uri) }
            failed = stored == null
            stored?.let { onChange(r.copy(imagePath = it)) }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val bitmap = remember(r.imagePath) { r.imagePath?.let(Images::load)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap, contentDescription = "Picture", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = ::pick),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DotChip("Picture", r.imageStyle == ImageStyle.PICTURE) { onChange(r.copy(imageStyle = ImageStyle.PICTURE)) }
            DotChip("Banner", r.imageStyle == ImageStyle.BANNER) { onChange(r.copy(imageStyle = ImageStyle.BANNER)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onChange(r.copy(imagePath = null)) }) { Text("Remove", color = Nota.Sub) }
        }
    } else {
        SoftButton("+ Picture", onClick = ::pick, height = 44.dp, color = Nota.Sub)
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
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = if (indent) 16.dp else 0.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (indent) Nota.Sub else Nota.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = notaSwitchColors())
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Shelf).clickable(onClick = onClick)
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
                Modifier.size(40.dp).clip(CircleShape).background(if (on) Nota.Mint else Nota.Shelf).clickable { onToggle(day) },
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

private val AlertStyle.label: String
    get() = when (this) {
        AlertStyle.SOUND_AND_VIBRATE -> "Sound"
        AlertStyle.VIBRATE_ONLY -> "Vibrate"
        AlertStyle.SILENT -> "Silent"
    }
