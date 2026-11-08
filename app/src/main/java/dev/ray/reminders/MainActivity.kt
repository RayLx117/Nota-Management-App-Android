package dev.ray.reminders

import android.graphics.Color
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.data.Kind
import dev.ray.reminders.ui.AlarmsScreen
import dev.ray.reminders.ui.ComingSoonScreen
import dev.ray.reminders.ui.Dot
import dev.ray.reminders.ui.Nota
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.ray.reminders.alarm.Notifier
import dev.ray.reminders.data.ReminderStore
import dev.ray.reminders.ui.EditorScreen
import dev.ray.reminders.ui.ListScreen
import dev.ray.reminders.ui.RemindersTheme

class MainActivity : ComponentActivity() {
    /** Bumped whenever a notification asks us to open the Timer tab. */
    private val openTimer = androidx.compose.runtime.mutableIntStateOf(0)
    private val openLists = androidx.compose.runtime.mutableIntStateOf(0)

    private fun route(intent: android.content.Intent?) {
        when (intent?.getStringExtra(EXTRA_TAB)) {
            "timer" -> openTimer.intValue++
            "lists" -> {
                openLists.intValue++
                intent.getIntExtra(EXTRA_LIST, -1).takeIf { it >= 0 }?.let { dev.ray.reminders.ui.ListsNav.open.value = it }
            }
        }
    }

    // Passwords lock whenever Nota goes to the background.
    override fun onStart() {
        super.onStart()
        AppVisible.now = true
    }

    override fun onStop() {
        super.onStop()
        AppVisible.now = false
        dev.ray.reminders.data.Vault.lock()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_LIST = "list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        // Nota is always dark, so always use light status/navigation bar icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        Notifier.ensureChannels(this)
        setContent { RemindersTheme { App(openTimer.intValue, openLists.intValue) } }
    }
}

/** Whether Nota's main screen is on screen (activities may be started directly then). */
object AppVisible { @Volatile var now = false }

private const val NEW = -1

private enum class Tab(val label: String, val icon: Int) {
    TIMER("Timer", R.drawable.ic_tab_timer),
    ALARMS("Alarms", R.drawable.ic_tab_alarm),
    REMINDERS("Reminders", R.drawable.ic_tab_reminders),
    LISTS("Lists", R.drawable.ic_tab_lists),
    NOTES("Notes", R.drawable.ic_tab_notes),
    // Second row, revealed by dragging the bar up.
    SETTINGS("Settings", R.drawable.ic_tab_settings),
}

private const val ROW_SIZE = 5

@Composable
private fun App(openTimer: Int, openLists: Int) {
    val ctx = LocalContext.current
    CrashReport()
    val store = remember { ReminderStore.get(ctx) }
    val items by store.items.collectAsState()
    // What's open in the editor: null = nothing, otherwise (kind, id or NEW).
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingKind by rememberSaveable { mutableStateOf(Kind.REMINDER) }
    // Calendar open for alarms or reminders (null = closed), and what a new item from it starts with.
    var calendarKind by rememberSaveable { mutableStateOf<Kind?>(null) }
    var prefill by remember { mutableStateOf<dev.ray.reminders.ui.Prefill?>(null) }
    val pager = rememberPagerState(initialPage = Tab.REMINDERS.ordinal) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(openTimer) {
        if (openTimer > 0) { editingId = null; calendarKind = null; pager.scrollToPage(Tab.TIMER.ordinal) }
    }
    androidx.compose.runtime.LaunchedEffect(openLists) {
        if (openLists > 0) { editingId = null; calendarKind = null; pager.scrollToPage(Tab.LISTS.ordinal) }
    }

    fun edit(kind: Kind, id: Int) { editingKind = kind; editingId = id }

    // The editor rises in gently over the lists, and sinks back out when closed.
    // Screens stack: tabs (0) < calendar (1) < editor (2). Deeper ones rise in; going back sinks them.
    androidx.compose.animation.AnimatedContent(
        Triple(editingId, calendarKind, editingKind),
        contentKey = { if (it.first != null) 2 else if (it.second != null) 1 else 0 },
        transitionSpec = {
            val ease = androidx.compose.animation.core.FastOutSlowInEasing
            fun depth(t: Triple<Int?, Kind?, Kind>) = if (t.first != null) 2 else if (t.second != null) 1 else 0
            if (depth(targetState) > depth(initialState)) {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, easing = ease)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320, easing = ease)) { it / 10 } +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(320, easing = ease), initialScale = 0.98f)) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            } else {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240, delayMillis = 60)) +
                    androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300, easing = ease), initialScale = 0.98f)) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(280, easing = ease)) { it / 10 })
            }
        },
        modifier = Modifier.fillMaxSize().background(Nota.Bg),
        label = "editor",
    ) { (current, calendar, kind) ->
    if (current != null) {
        BackHandler { editingId = null; prefill = null }
        EditorScreen(
            existing = items.firstOrNull { it.id == current },
            kind = kind,
            newId = { store.newId() },
            onClose = { editingId = null; prefill = null },
            prefill = prefill,
        )
    } else if (calendar != null) {
        BackHandler { calendarKind = null }
        dev.ray.reminders.ui.CalendarScreen(
            kind = calendar,
            items = items.filter { it.kind == calendar },
            onBack = { calendarKind = null },
            onCreate = { prefill = it; edit(calendar, NEW) },
            onEdit = { edit(calendar, it) },
        )
    } else
    Column(Modifier.fillMaxSize().background(Nota.Bg)) {
        HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1, userScrollEnabled = false) { page ->
            when (Tab.entries[page]) {
                Tab.TIMER -> dev.ray.reminders.ui.TimerScreen(visible = pager.currentPage == Tab.TIMER.ordinal)
                Tab.ALARMS -> AlarmsScreen(
                    items.filter { it.kind == Kind.ALARM },
                    onAdd = { edit(Kind.ALARM, NEW) }, onEdit = { edit(Kind.ALARM, it) },
                    onCalendar = { calendarKind = Kind.ALARM },
                )
                Tab.REMINDERS -> ListScreen(
                    items.filter { it.kind == Kind.REMINDER },
                    onAdd = { edit(Kind.REMINDER, NEW) }, onEdit = { edit(Kind.REMINDER, it) },
                    onCalendar = { calendarKind = Kind.REMINDER },
                )
                Tab.LISTS -> dev.ray.reminders.ui.ListsScreen()
                Tab.NOTES -> dev.ray.reminders.ui.NotesScreen(visible = pager.currentPage == Tab.NOTES.ordinal)
                Tab.SETTINGS -> dev.ray.reminders.ui.SettingsScreen(visible = pager.currentPage == Tab.SETTINGS.ordinal)
            }
        }
        ToolBar(pager.currentPage, onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } })
    }
    }
}

/**
 * Bottom bar. The handle on top drags it up (or tap it) to reveal a second row of tools.
 * Picking a tool from the first row tucks the second row away again.
 */
@Composable
private fun ToolBar(current: Int, onSelect: (Tab) -> Unit) {
    val scope = rememberCoroutineScope()
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    var rowPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    fun settle(target: Float) = scope.launch { open.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f)) }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { dy ->
        scope.launch { open.snapTo((open.value - dy / rowPx).coerceIn(0f, 1f)) }
    }
    val rows = Tab.entries.chunked(ROW_SIZE)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Nota.Shelf)
            .draggable(
                drag, androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStopped = { v -> settle(if (v < -600f) 1f else if (v > 600f) 0f else if (open.value > 0.5f) 1f else 0f) },
            )
            .navigationBarsPadding(),
    ) {
        // The handle.
        Box(
            Modifier.fillMaxWidth().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            ) { settle(if (open.targetValue > 0.5f) 0f else 1f) }.padding(top = 8.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Nota.Outline))
        }
        TabRow(rows[0], current) { onSelect(it); settle(0f) }
        // Second row: grows from nothing as the bar is pulled up.
        Box(
            Modifier.fillMaxWidth().clipToBounds().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                rowPx = p.height.toFloat().coerceAtLeast(1f)
                // The spring overshoots a touch past closed, so never go below zero height.
                val h = (p.height * open.value.coerceIn(0f, 1f)).toInt()
                layout(p.width, h) { p.place(0, 0) }
            }.graphicsLayer { alpha = open.value.coerceIn(0f, 1f) },
        ) {
            rows.drop(1).forEach { TabRow(it, current, onSelect) }
        }
    }
}

@Composable
private fun TabRow(tabs: List<Tab>, current: Int, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        tabs.forEach { tab -> TabItem(tab, current == tab.ordinal, Modifier.weight(1f)) { onSelect(tab) } }
        repeat(ROW_SIZE - tabs.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp),
                tint = if (selected) Nota.Text else Nota.Sub,
            )
            if (selected) Dot(size = 8.dp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
        }
        Text(
            tab.label, fontFamily = Nota.Outfit, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Nota.Text else Nota.Sub, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** After a crash: offer to copy what went wrong, so it can be sent over and fixed. */
@Composable
private fun CrashReport() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(ctx)) }
    val text = report ?: return
    fun close() { CrashLog.clear(ctx); report = null }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = ::close,
        containerColor = Nota.Shelf,
        title = { Text("Nota crashed last time") },
        text = {
            Text(
                text.lineSequence().take(8).joinToString("\n"), fontFamily = Nota.Mono, fontSize = 11.sp, color = Nota.Sub,
                modifier = Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Nota crash", text))
                close()
            }) { Text("Copy details", color = Nota.Mint) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = ::close) { Text("Dismiss", color = Nota.Sub) } },
    )
}

