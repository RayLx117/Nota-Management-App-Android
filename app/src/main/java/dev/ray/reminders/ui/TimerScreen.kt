package dev.ray.reminders.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.Stopwatch
import dev.ray.reminders.alarm.Timer
import dev.ray.reminders.alarm.TimerPreset
import dev.ray.reminders.data.Sounds
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import dev.ray.reminders.data.AlertStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The Timer tab: the timer, with the stopwatch one swipe to the right.
 * It always comes back to the timer when you leave the tab.
 */
@Composable
fun TimerScreen(visible: Boolean) {
    val pages = rememberPagerState { 2 }
    LaunchedEffect(visible) { if (!visible) pages.scrollToPage(0) }
    Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding()) {
        // "Timer  • •  Stopwatch": the page you're on is bright, the other greyed; follows the swipe.
        val scope = rememberCoroutineScope()
        val progress = (pages.currentPage + pages.currentPageOffsetFraction).coerceIn(0f, 1f)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Timer", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                color = lerp(Nota.Text, Nota.Outline, progress),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch { pages.animateScrollToPage(0) }
                },
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                repeat(2) { i ->
                    val on = if (i == 0) 1f - progress else progress
                    Box(
                        Modifier.size(6.dp + 2.dp * on).clip(CircleShape).background(lerp(Nota.Outline, Nota.Mint, on)),
                    )
                }
            }
            Text(
                "Stopwatch", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
                color = lerp(Nota.Outline, Nota.Text, progress),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch { pages.animateScrollToPage(1) }
                },
            )
        }
        HorizontalPager(pages, modifier = Modifier.weight(1f)) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (page == 0) TimerPage() else StopwatchPage()
            }
        }
    }
}

@Composable
private fun TimerPage() {
    val ctx = LocalContext.current
    val state by Timer.state(ctx).collectAsState()
    AnimatedContent(state.idle, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "timer") { idle ->
        if (idle) TimerSetup() else TimerRunning()
    }
}

@Composable
private fun StopwatchPage() {
    val ctx = LocalContext.current
    val sw by Stopwatch.state(ctx).collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sw.running) {
        while (sw.running) { now = System.currentTimeMillis(); delay(16) }
        now = System.currentTimeMillis()
    }
    val elapsed = sw.elapsed(now)
    // Each lap's own time, newest first; best and worst get coloured once there are a few.
    val splits = sw.laps.mapIndexed { i, total -> total - (if (i == 0) 0 else sw.laps[i - 1]) }
    val best = if (splits.size >= 3) splits.min() else null
    val worst = if (splits.size >= 3) splits.max() else null

    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Text(
                Stopwatch.format(elapsed), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold,
                fontSize = if (elapsed >= 3_600_000) 46.sp else 60.sp,
                color = if (sw.running) Nota.Text else if (elapsed > 0) Nota.Sub else Nota.Text,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (sw.running && sw.laps.isNotEmpty()) item {
                LapRow(sw.laps.size + 1, elapsed - sw.laps.last(), elapsed, Nota.Text)
            }
            items(splits.indices.reversed().toList()) { i ->
                val color = when (splits[i]) { best -> Nota.Mint; worst -> Nota.Error; else -> Nota.Sub }
                LapRow(i + 1, splits[i], sw.laps[i], color)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                sw.running -> SoftButton("Lap", onClick = { Stopwatch.lap(ctx) }, modifier = Modifier.weight(1f))
                elapsed > 0 -> SoftButton("Reset", onClick = { Stopwatch.reset(ctx) }, modifier = Modifier.weight(1f))
                else -> SoftButton("Lap", onClick = {}, modifier = Modifier.weight(1f).alpha(0.4f))
            }
            if (sw.running) SoftButton("Stop", onClick = { Stopwatch.stop(ctx) }, modifier = Modifier.weight(1f), color = Nota.Error)
            else PrimaryButton(if (elapsed > 0) "Resume" else "Start", onClick = { Stopwatch.start(ctx) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LapRow(n: Int, split: Long, total: Long, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Lap $n", fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f))
            Text(Stopwatch.format(split), fontFamily = Nota.Mono, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(Stopwatch.format(total), fontFamily = Nota.Mono, fontSize = 16.sp, color = Nota.Sub, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        androidx.compose.material3.HorizontalDivider(color = Nota.Line)
    }
}

@Composable
private fun TimerSetup() {
    val ctx = LocalContext.current
    val last = remember { Timer.lastDuration(ctx) / 1000 }
    var h by rememberSaveable { mutableIntStateOf((last / 3600).toInt()) }
    var m by rememberSaveable { mutableIntStateOf((last % 3600 / 60).toInt()) }
    var s by rememberSaveable { mutableIntStateOf((last % 60).toInt()) }
    var label by rememberSaveable { mutableStateOf("") }
    // A preset brings its own sound; otherwise the default from Settings.
    var sound by rememberSaveable { mutableStateOf<String?>(null) }
    val total = (h * 3600L + m * 60L + s) * 1000L

    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = label, onValueChange = { label = it }, singleLine = true,
            placeholder = { Text("Name this timer", color = Nota.Sub) },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Raised,
                focusedContainerColor = Nota.Shelf, unfocusedContainerColor = Nota.Shelf, cursorColor = Nota.Mint,
            ),
        )

        PresetRow(
            onPick = { p ->
                h = p.seconds / 3600; m = p.seconds % 3600 / 60; s = p.seconds % 60
                label = p.name
                sound = p.sound
            },
            currentSeconds = (total / 1000).toInt(), currentName = label, currentSound = sound,
        )

        // Three wheels on one card, with a raised band marking the chosen row.
        Box(
            Modifier.fillMaxWidth().height(WHEEL_HEIGHT).clip(RoundedCornerShape(24.dp)).background(Nota.Shelf),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(ITEM_HEIGHT)
                    .clip(RoundedCornerShape(16.dp)).background(Nota.Raised),
            )
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberWheel(100, h, { h = it }, "h", Modifier.weight(1f))
                Colon()
                NumberWheel(60, m, { m = it }, "m", Modifier.weight(1f))
                Colon()
                NumberWheel(60, s, { s = it }, "s", Modifier.weight(1f))
            }
            // Fade the wheels into the card at the top and bottom.
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Nota.Shelf, Color.Transparent))),
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Nota.Shelf))),
            )
        }

        var vibrateOnly by remember { mutableStateOf(Timer.vibrateOnly(ctx)) }
        SoundPicker(
            if (vibrateOnly) AlertStyle.VIBRATE_ONLY else AlertStyle.SOUND_AND_VIBRATE,
            options = listOf(AlertStyle.SOUND_AND_VIBRATE, AlertStyle.VIBRATE_ONLY),
            onPick = { vibrateOnly = it == AlertStyle.VIBRATE_ONLY; Timer.setVibrateOnly(ctx, vibrateOnly) },
        )
        AudioRow(sound, onPick = { sound = it }, enabled = !vibrateOnly, timer = true)

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            "Start", onClick = { Timer.start(ctx, total, label, Sounds.resolve(ctx, sound, timer = true)) },
            modifier = Modifier.fillMaxWidth().alpha(if (total > 0) 1f else 0.4f),
        )
    }
}

@Composable
private fun Colon() {
    Text(":", fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Nota.Sub)
}

private val ITEM_HEIGHT = 60.dp
private val WHEEL_HEIGHT = 228.dp

/** Quick-start chips, plus a pencil to save or remove your own named presets. */
@Composable
private fun PresetRow(onPick: (TimerPreset) -> Unit, currentSeconds: Int, currentName: String, currentSound: String?) {
    val ctx = LocalContext.current
    val presets by Timer.presets(ctx).collectAsState()
    var editing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { p ->
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Nota.Raised).clickable { onPick(p) }
                        .padding(horizontal = 14.dp, vertical = if (p.name.isBlank()) 12.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (p.name.isNotBlank()) Text(
                        p.name, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Nota.Text, maxLines = 1,
                    )
                    Text(
                        Timer.shortDuration(p.seconds), fontFamily = Nota.Outfit,
                        fontWeight = if (p.name.isBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (p.name.isBlank()) 14.sp else 12.sp,
                        color = if (p.name.isBlank()) Nota.Text else Nota.Mint,
                    )
                }
            }
        }
        Box(
            Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).border(2.dp, Nota.Outline, CircleShape)
                .clickable { editing = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Edit, contentDescription = "Edit presets", tint = Nota.Mint, modifier = Modifier.size(20.dp)) }
    }
    if (editing) PresetsDialog(presets, currentSeconds, currentName, currentSound, onDismiss = { editing = false })
}

@Composable
private fun PresetsDialog(presets: List<TimerPreset>, currentSeconds: Int, currentName: String, currentSound: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var soundFor by remember { mutableStateOf<Int?>(null) }
    soundFor?.let { i ->
        presets.getOrNull(i)?.let { p ->
            SoundPickerDialog(Sounds.resolve(ctx, p.sound, timer = true), onPick = { id ->
                Timer.setPresets(ctx, presets.mapIndexed { j, q -> if (j == i) q.copy(sound = id) else q })
                soundFor = null
            }, onDismiss = { soundFor = null })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Presets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised)
                            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifBlank { Timer.shortDuration(p.seconds) }, color = Nota.Text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold)
                            if (p.name.isNotBlank()) Text(Timer.shortDuration(p.seconds), color = Nota.Sub, fontSize = 13.sp)
                            Row(
                                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { soundFor = i }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(dev.ray.reminders.R.drawable.ic_music), null, tint = Nota.Mint, modifier = Modifier.size(14.dp))
                                Text(
                                    Sounds.name(ctx, p.sound, timer = true), color = Nota.Mint, fontSize = 13.sp, maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                        IconButton(onClick = { Timer.setPresets(ctx, presets.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Nota.Error)
                        }
                    }
                }
                val canSave = currentSeconds > 0
                Spacer(Modifier.height(4.dp))
                SoftButton(
                    if (canSave) "Save " + (currentName.trim().ifBlank { Timer.shortDuration(currentSeconds) }) + " as a preset"
                    else "Set a time to save it as a preset",
                    onClick = { if (canSave) Timer.setPresets(ctx, presets + TimerPreset(currentName.trim(), currentSeconds, Sounds.resolve(ctx, currentSound, timer = true))) },
                    color = if (canSave) Nota.Mint else Nota.Sub, height = 46.dp, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Uses the name and time currently set on the timer.",
                    style = MaterialTheme.typography.bodySmall, color = Nota.Sub,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Nota.Mint) } },
    )
}

/** An endless vertical number wheel (0 until [count]); the middle row is the value. */
@Composable
private fun NumberWheel(count: Int, value: Int, onChange: (Int) -> Unit, unit: String, modifier: Modifier = Modifier) {
    // Runs 0..max and stops at both ends (no wrapping round).
    val state = rememberPagerState(initialPage = value) { count }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { state.currentPage }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
        }
    }
    // Report the value once the wheel comes to rest, so a preset can spin it without a tug of war.
    LaunchedEffect(state) { snapshotFlow { state.settledPage }.collect { currentOnChange(it) } }
    LaunchedEffect(value) { if (state.settledPage != value) state.animateScrollToPage(value) }
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        VerticalPager(
            state = state,
            pageSize = PageSize.Fixed(ITEM_HEIGHT),
            contentPadding = PaddingValues(vertical = (WHEEL_HEIGHT - ITEM_HEIGHT) / 2),
            beyondViewportPageCount = 3,
            flingBehavior = PagerDefaults.flingBehavior(state, pagerSnapDistance = PagerSnapDistance.atMost(count)),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val distance = ((page - state.currentPage) - state.currentPageOffsetFraction).absoluteValue
            val closeness = 1f - distance.coerceAtMost(2.5f) / 2.5f
            
            Box(
                Modifier.fillMaxWidth().height(ITEM_HEIGHT)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { state.animateScrollToPage(page) }
                    }
                    .graphicsLayer {
                        val scale = 0.7f + 0.3f * closeness
                        scaleX = scale; scaleY = scale
                        alpha = 0.2f + 0.8f * closeness
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(page), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold,
                    fontSize = 38.sp, color = lerp(Nota.Sub, Nota.Text, closeness),
                )
            }
        }
        // Unit sits just right of the selected number.
        Text(
            unit, color = Nota.Mint, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp, top = 12.dp),
        )
    }
}

@Composable
private fun TimerRunning() {
    val ctx = LocalContext.current
    val t by Timer.state(ctx).collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.running) {
        while (t.running) { now = System.currentTimeMillis(); delay(50) }
        now = System.currentTimeMillis()
    }
    val left = t.left(now)
    val progress = if (t.durationMs > 0) (left.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Nota.Raised, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    Brush.sweepGradient(listOf(Nota.Cyan, Nota.Mint, Nota.Cyan)), -90f, 360f * progress, false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (t.label.isNotBlank()) Text(
                    t.label, color = Nota.Sub, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.width(200.dp),
                )
                Text(
                    if (t.ringing) "0:00" else Timer.format(left), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold,
                    fontSize = if (left >= 3_600_000) 44.sp else 56.sp,
                    color = if (t.paused) Nota.Sub else if (t.ringing) Nota.Mint else Nota.Text,
                )
                Text(
                    when { t.ringing -> "Time's up"; t.paused -> "Paused"; else -> "of ${Timer.format(t.durationMs)}" },
                    color = if (t.ringing) Nota.Mint else Nota.Sub, fontFamily = Nota.Outfit, fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.weight(0.6f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftButton("Cancel", onClick = { Timer.cancel(ctx) }, modifier = Modifier.weight(1f))
            SoftButton("+1 min", onClick = { Timer.addMinute(ctx) }, modifier = Modifier.weight(1f), color = Nota.Mint)
        }
        Spacer(Modifier.height(12.dp))
        when {
            t.ringing -> PrimaryButton("Stop", onClick = { Timer.cancel(ctx) }, modifier = Modifier.fillMaxWidth())
            t.paused -> PrimaryButton("Resume", onClick = { Timer.resume(ctx) }, modifier = Modifier.fillMaxWidth())
            else -> PrimaryButton("Pause", onClick = { Timer.pause(ctx) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
