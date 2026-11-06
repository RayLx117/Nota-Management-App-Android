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
import dev.ray.reminders.alarm.Timer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** The Timer tab: set it with three wheels, then watch the ring count down. */
@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val state by Timer.state(ctx).collectAsState()
    Column(
        Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().padding(horizontal = 16.dp),
    ) {
        PageHeader("Timer")
        AnimatedContent(state.idle, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "timer") { idle ->
            if (idle) TimerSetup() else TimerRunning()
        }
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
    val total = (h * 3600L + m * 60L + s) * 1000L

    Column(Modifier.fillMaxSize().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutlinedTextField(
            value = label, onValueChange = { label = it }, singleLine = true,
            placeholder = { Text("Name this timer", color = Nota.Sub) },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Raised,
                focusedContainerColor = Nota.Shelf, unfocusedContainerColor = Nota.Shelf, cursorColor = Nota.Mint,
            ),
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
                NumberWheel(24, h, { h = it }, "h", Modifier.weight(1f))
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

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            "Start", onClick = { Timer.start(ctx, total, label) },
            modifier = Modifier.fillMaxWidth().alpha(if (total > 0) 1f else 0.4f),
        )
    }
}

@Composable
private fun Colon() {
    Text(":", fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Nota.Sub)
}

private val ITEM_HEIGHT = 60.dp
private val WHEEL_HEIGHT = 300.dp

/** An endless vertical number wheel (0 until [count]); the middle row is the value. */
@Composable
private fun NumberWheel(count: Int, value: Int, onChange: (Int) -> Unit, unit: String, modifier: Modifier = Modifier) {
    val middle = remember(count) { (Int.MAX_VALUE / 2).let { it - it % count } }
    val state = rememberPagerState(initialPage = middle + value) { Int.MAX_VALUE }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { state.currentPage }.collect { page ->
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
            currentOnChange(page % count)
        }
    }
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
                    "%02d".format(page % count), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold,
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
