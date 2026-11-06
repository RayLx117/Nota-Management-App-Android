package dev.ray.reminders.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.AlertStyle
import dev.ray.reminders.data.Behavior
import dev.ray.reminders.data.Reminder
import dev.ray.reminders.data.ReminderStore
import dev.ray.reminders.data.RepeatType
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New reminder" else "Edit reminder") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = r.title, onValueChange = { r = r.copy(title = it) },
                label = { Text("What should I remind you about?") },
                placeholder = { Text("Take supplements") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = r.notes, onValueChange = { r = r.copy(notes = it) },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Vitamin D, magnesium, omega-3") },
                modifier = Modifier.fillMaxWidth(),
            )

            // ------------------------------------------------------------- schedule
            SectionTitle("When")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatType.entries.forEach { type ->
                    FilterChip(
                        selected = r.repeat == type,
                        onClick = { r = r.copy(repeat = type) },
                        label = { Text(type.label) },
                    )
                }
            }
            if (r.repeat != RepeatType.ONCE) {
                Stepper(
                    label = "Repeat every",
                    valueText = "${r.interval} ${r.repeat.unit(r.interval)}",
                    onMinus = { r = r.copy(interval = (r.interval - 1).coerceAtLeast(1)) },
                    onPlus = { r = r.copy(interval = (r.interval + 1).coerceAtMost(99)) },
                )
            }
            if (r.repeat == RepeatType.WEEKLY) {
                Text("On these days", style = MaterialTheme.typography.bodyMedium)
                WeekdayPicker(r.weekdays) { day ->
                    val days = if (day in r.weekdays) r.weekdays - day else r.weekdays + day
                    if (days.isNotEmpty()) r = r.copy(weekdays = days)
                }
            }
            ValueRow(
                label = when (r.repeat) {
                    RepeatType.ONCE -> "Date"
                    RepeatType.MONTHLY -> "Starting (repeats on this day of the month)"
                    RepeatType.YEARLY -> "Starting (repeats on this date)"
                    else -> "Starting"
                },
                value = Describe.date(r.startEpochDay),
                onClick = { showDate = true },
            )
            ValueRow(label = "Time", value = Describe.time(r), onClick = { showTime = true })

            // ------------------------------------------------------------- behaviour
            SectionTitle("How to remind me")
            Text("Alert", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertStyle.entries.forEach { style ->
                    FilterChip(
                        selected = r.behavior.alertStyle == style,
                        onClick = { behavior { copy(alertStyle = style) } },
                        label = { Text(style.label) },
                    )
                }
            }
            ToggleRow(
                title = "Stay in the notification bar until Done",
                subtitle = "Can't be swiped away. Only the Done (or Snooze) button clears it.",
                checked = r.behavior.sticky,
            ) { behavior { copy(sticky = it) } }
            ToggleRow(
                title = "Keep alerting until Done",
                subtitle = "Re-alerts (with your alert style above) on a timer.",
                checked = r.behavior.nag,
            ) { behavior { copy(nag = it) } }
            if (r.behavior.nag) Stepper(
                label = "Alert again every",
                valueText = Describe.minutes(r.behavior.nagEveryMinutes),
                onMinus = { behavior { copy(nagEveryMinutes = stepMinutes(nagEveryMinutes, -1, min = 1)) } },
                onPlus = { behavior { copy(nagEveryMinutes = stepMinutes(nagEveryMinutes, +1, min = 1)) } },
                indent = true,
            )
            ToggleRow(
                title = "Full-screen alarm",
                subtitle = "Takes over the screen and rings until you tap Done or Snooze.",
                checked = r.behavior.fullScreen,
            ) { behavior { copy(fullScreen = it) } }
            if (r.behavior.fullScreen) Stepper(
                label = "Show it after",
                valueText = if (r.behavior.fullScreenAfterMinutes == 0) "Right away"
                else Describe.minutes(r.behavior.fullScreenAfterMinutes),
                onMinus = { behavior { copy(fullScreenAfterMinutes = stepMinutes(fullScreenAfterMinutes, -1, min = 0)) } },
                onPlus = { behavior { copy(fullScreenAfterMinutes = stepMinutes(fullScreenAfterMinutes, +1, min = 0)) } },
                indent = true,
            )
            Stepper(
                label = "Snooze length",
                valueText = Describe.minutes(r.behavior.snoozeMinutes),
                onMinus = { behavior { copy(snoozeMinutes = stepMinutes(snoozeMinutes, -1, min = 1)) } },
                onPlus = { behavior { copy(snoozeMinutes = stepMinutes(snoozeMinutes, +1, min = 1)) } },
            )

            // ------------------------------------------------------------- summary
            Summary(r)

            Button(onClick = { save(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            OutlinedButton(
                onClick = { val saved = save(); ReminderEngine.testNow(ctx, saved.id); onClose() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save and try it now") }
            Spacer(Modifier.height(24.dp))
        }
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
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Summary(r: Reminder) {
    val first = remember(r) { ReminderEngine.nextOccurrence(r, System.currentTimeMillis()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What will happen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(Describe.schedule(r), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Describe.behavior(r).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            Text(
                if (first == null) "⚠ That time has already passed, so this won't go off. Pick a later date or time."
                else Describe.status(r.copy(nextTriggerAt = first, enabled = true, activeSince = null, snoozedUntil = null))
                    .replace("Next:", "First reminder:"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (first == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stepper(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit, indent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(valueText, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { day ->
            val on = day in selected
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Describe.dayName(day, java.time.format.TextStyle.NARROW),
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        RepeatType.ONCE -> "One time"
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
        AlertStyle.SOUND_AND_VIBRATE -> "Sound + vibrate"
        AlertStyle.VIBRATE_ONLY -> "Vibrate only"
        AlertStyle.SILENT -> "Silent"
    }
