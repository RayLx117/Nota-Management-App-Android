package dev.ray.reminders.ui

import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

/** Which repeat types show on the calendar, remembered per tab. */
private object CalendarFilter {
    val types = listOf(
        dev.ray.reminders.data.RepeatType.DAILY to "Daily", dev.ray.reminders.data.RepeatType.WEEKLY to "Weekly",
        dev.ray.reminders.data.RepeatType.MONTHLY to "Monthly", dev.ray.reminders.data.RepeatType.YEARLY to "Yearly",
        dev.ray.reminders.data.RepeatType.ONCE to "One time",
    )
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("calendar", android.content.Context.MODE_PRIVATE)
    fun hidden(ctx: android.content.Context, kind: Kind): Set<dev.ray.reminders.data.RepeatType> =
        prefs(ctx).getStringSet("hidden_$kind", emptySet()).orEmpty()
            .mapNotNull { n -> dev.ray.reminders.data.RepeatType.entries.firstOrNull { it.name == n } }.toSet()
    fun save(ctx: android.content.Context, kind: Kind, hidden: Set<dev.ray.reminders.data.RepeatType>) =
        prefs(ctx).edit().putStringSet("hidden_$kind", hidden.map { it.name }.toSet()).apply()
}

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
    var hidden by remember { mutableStateOf(CalendarFilter.hidden(ctx, kind)) }
    var filtering by remember { mutableStateOf(false) }
    var pickingMonth by remember { mutableStateOf(false) }
    if (pickingMonth) MonthYearDialog(month, onDismiss = { pickingMonth = false }) { picked ->
        monthOffset = ((picked.year - today.year) * 12 + (picked.monthValue - today.monthValue))
        pickingMonth = false
    }
    val shown = remember(items, hidden) { items.filter { it.repeat !in hidden } }
    val busy = remember(shown, month) {
        cells.filter { d -> shown.any { it.enabled && occursOn(it, d, zone) } }.toSet()
    }
    val onDay = remember(shown, selected) {
        shown.filter { occursOn(it, day, zone) }.sortedBy { it.hour * 60 + it.minute }
    }
    if (filtering) androidx.compose.material3.AlertDialog(
        onDismissRequest = { filtering = false },
        containerColor = Nota.Shelf,
        title = { Text("Show on calendar") },
        text = {
            Column {
                CalendarFilter.types.forEach { (type, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            hidden = if (type in hidden) hidden - type else hidden + type; CalendarFilter.save(ctx, kind, hidden)
                        }.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = Nota.Text, fontFamily = Nota.Outfit, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = type !in hidden,
                            onCheckedChange = { on -> hidden = if (on) hidden - type else hidden + type; CalendarFilter.save(ctx, kind, hidden) },
                            colors = notaSwitchColors(),
                        )
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { filtering = false }) { Text("Done", color = Nota.Mint) } },
    )

    Column(
        Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nota.Text) }
            Text(
                if (kind == Kind.ALARM) "Alarm calendar" else "Reminder calendar",
                fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Nota.Text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { filtering = true }) {
                Icon(
                    androidx.compose.ui.res.painterResource(dev.ray.reminders.R.drawable.ic_filter), contentDescription = "Filter",
                    tint = if (hidden.isEmpty()) Nota.Sub else Nota.Mint, modifier = Modifier.size(22.dp),
                )
            }
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
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { pickingMonth = true }.padding(vertical = 8.dp),
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
            // Swipe left/right across the days for the next/previous month.
            Column(
                Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -size.width / 6f) monthOffset++ else if (total > size.width / 6f) monthOffset--
                        },
                    ) { _, dx -> total += dx }
                },
            ) {
            // Months slide in from the side you swiped toward.
            androidx.compose.animation.AnimatedContent(
                month,
                transitionSpec = {
                    val forward = targetState > initialState
                    val ease = androidx.compose.animation.core.FastOutSlowInEasing
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { if (forward) it else -it } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { if (forward) -it else it } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))) using
                        androidx.compose.animation.SizeTransform(clip = true)
                },
                label = "month",
            ) { month ->
            val first = month.atDay(1)
            val cells = (0 until 42).map { first.minusDays((first.dayOfWeek.value % 7).toLong()).plusDays(it.toLong()) }
            val busy = remember(shown, month) {
                cells.filter { d -> shown.any { it.enabled && occursOn(it, d, zone) } }.toSet()
            }
            Column {
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

/** "Scroll window": month and year wheels. */
@Composable
private fun MonthYearDialog(current: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    val years = remember { (YearMonth.now().year - 10..YearMonth.now().year + 30).toList() }
    var monthIndex by remember { mutableIntStateOf(current.monthValue - 1) }
    var yearIndex by remember { mutableIntStateOf(years.indexOf(current.year).coerceAtLeast(0)) }
    val months = remember { java.time.Month.entries.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Go to month") },
        text = {
            Box(Modifier.fillMaxWidth().height(WHEEL_H), contentAlignment = Alignment.Center) {
                // The band behind the selected row.
                Box(Modifier.fillMaxWidth().height(ROW_H).clip(RoundedCornerShape(14.dp)).background(Nota.Raised))
                Row(Modifier.fillMaxSize()) {
                    TextWheel(months, monthIndex, { monthIndex = it }, Modifier.weight(1.4f))
                    TextWheel(years.map { it.toString() }, yearIndex, { yearIndex = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onPick(YearMonth.of(years[yearIndex], monthIndex + 1)) }) { Text("Go", color = Nota.Mint) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

private val ROW_H = 44.dp
private val WHEEL_H = 220.dp

@Composable
private fun TextWheel(labels: List<String>, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = androidx.compose.foundation.pager.rememberPagerState(initialPage = value) { labels.size }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val report by androidx.compose.runtime.rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        var first = true
        androidx.compose.runtime.snapshotFlow { state.currentPage }.collect {
            if (!first) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            first = false
        }
    }
    LaunchedEffect(state) { androidx.compose.runtime.snapshotFlow { state.settledPage }.collect { report(it) } }
    androidx.compose.foundation.pager.VerticalPager(
        state = state,
        pageSize = androidx.compose.foundation.pager.PageSize.Fixed(ROW_H),
        contentPadding = PaddingValues(vertical = (WHEEL_H - ROW_H) / 2),
        beyondViewportPageCount = 3,
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
            state, pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(labels.size),
        ),
        modifier = modifier.fillMaxHeight(),
    ) { page ->
        val distance = kotlin.math.abs((page - state.currentPage) - state.currentPageOffsetFraction)
        val closeness = 1f - distance.coerceAtMost(2.5f) / 2.5f
        Box(
            Modifier.fillMaxWidth().height(ROW_H)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    scope.launch { state.animateScrollToPage(page) }
                }
                .graphicsLayer { val sc = 0.75f + 0.25f * closeness; scaleX = sc; scaleY = sc; alpha = 0.25f + 0.75f * closeness },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                labels[page], fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                color = androidx.compose.ui.graphics.lerp(Nota.Sub, Nota.Text, closeness), maxLines = 1,
            )
        }
    }
}
