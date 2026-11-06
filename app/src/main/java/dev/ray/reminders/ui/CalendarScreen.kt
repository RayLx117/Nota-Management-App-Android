package dev.ray.reminders.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.data.Kind
import dev.ray.reminders.data.Recurrence
import dev.ray.reminders.data.Reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Does [r] go off on [day]? */
private fun occursOn(r: Reminder, day: LocalDate, zone: ZoneId): Boolean =
    Recurrence.next(r, day.atStartOfDay(zone).minusNanos(1))?.toLocalDate() == day

/**
 * Month calendar for the Alarms or Reminders tab: dots mark days with something on them,
 * picking a day lists what's on it and lets you start a new one there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    kind: Kind,
    items: List<Reminder>,
    onBack: () -> Unit,
    onCreate: (Prefill) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    var selected by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var minute by rememberSaveable { mutableIntStateOf(9 * 60) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val day = LocalDate.ofEpochDay(selected)
    val noun = if (kind == Kind.ALARM) "alarm" else "reminder"

    // Sunday-first grid, 6 weeks.
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val cells = (0 until 42).map { gridStart.plusDays(it.toLong()) }
    val busy = remember(items, month) {
        cells.filter { d -> items.any { it.enabled && occursOn(it, d, zone) } }.toSet()
    }
    val onDay = remember(items, selected) {
        items.filter { occursOn(it, day, zone) }.sortedBy { it.hour * 60 + it.minute }
    }

    Column(
        Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nota.Text) }
            Text(
                if (kind == Kind.ALARM) "Alarm calendar" else "Reminder calendar",
                fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Nota.Text,
            )
        }

        // Month card.
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).background(Nota.Shelf).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month", tint = Nota.Mint)
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f),
                    fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Nota.Text,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { monthOffset++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month", tint = Nota.Mint)
                }
            }
            Row(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                listOf(7, 1, 2, 3, 4, 5, 6).forEach { d ->
                    Text(
                        DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = Nota.Label, color = Nota.Sub,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row {
                    week.forEach { d ->
                        val inMonth = d.month == month.month
                        val isSel = d.toEpochDay() == selected
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(CircleShape)
                                .then(
                                    when {
                                        isSel -> Modifier.background(Nota.Mint)
                                        d == today -> Modifier.border(1.5.dp, Nota.Mint, CircleShape)
                                        else -> Modifier
                                    },
                                )
                                .clickable { selected = d.toEpochDay() }
                                .alpha(if (inMonth) 1f else 0.35f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(), fontFamily = Nota.Outfit,
                                fontWeight = if (isSel || d == today) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = 15.sp, color = if (isSel) Nota.Ink else Nota.Text,
                            )
                            if (d in busy) Box(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape)
                                    .background(if (isSel) Nota.Ink else Nota.Cyan),
                            )
                        }
                    }
                }
            }
        }

        // The chosen day.
        Text(
            day.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase(), style = Nota.Label, color = Nota.Sub,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        if (onDay.isEmpty()) {
            Text("Nothing on this day.", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub)
        } else onDay.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Nota.Shelf)
                    .clickable { onEdit(r.id) }.padding(horizontal = 14.dp, vertical = 12.dp).alpha(if (r.enabled) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Describe.time(r), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Nota.Mint,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(r.title, style = MaterialTheme.typography.bodyLarge, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // New one on this day.
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised).clickable { pickTime = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Time", style = MaterialTheme.typography.bodyLarge, color = Nota.Sub, modifier = Modifier.weight(1f))
                Text(Describe.clock(minute), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Nota.Text)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Repeat every year", style = MaterialTheme.typography.bodyLarge, color = Nota.Text, modifier = Modifier.weight(1f))
                Switch(checked = yearly, onCheckedChange = { yearly = it }, colors = notaSwitchColors())
            }
            PrimaryButton(
                "New $noun on ${day.format(DateTimeFormatter.ofPattern("MMM d"))}",
                onClick = { onCreate(Prefill(selected, minute, yearly)) }, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pickTime) {
        val state = rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { minute = state.hour * 60 + state.minute; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

/** Calendar button for the Alarms and Reminders headers. */
@Composable
fun CalendarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(CircleShape).border(2.dp, Nota.Outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(dev.ray.reminders.R.drawable.ic_calendar), contentDescription = "Calendar",
            tint = Nota.Mint, modifier = Modifier.size(20.dp),
        )
    }
}
