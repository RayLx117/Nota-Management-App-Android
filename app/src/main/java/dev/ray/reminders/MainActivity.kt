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
            "lists" -> openLists.intValue++
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    companion object { const val EXTRA_TAB = "tab" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        // Nota is always dark, so always use light status/navigation bar icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        Notifier.ensureChannels(this)
        setContent { RemindersTheme { App(openTimer.intValue, openLists.intValue) } }
    }
}

private const val NEW = -1

private enum class Tab(val label: String, val icon: Int) {
    TIMER("Timer", R.drawable.ic_tab_timer),
    ALARMS("Alarms", R.drawable.ic_tab_alarm),
    REMINDERS("Reminders", R.drawable.ic_tab_reminders),
    LISTS("Lists", R.drawable.ic_tab_lists),
}

@Composable
private fun App(openTimer: Int, openLists: Int) {
    val ctx = LocalContext.current
    val store = remember { ReminderStore.get(ctx) }
    val items by store.items.collectAsState()
    // What's open in the editor: null = nothing, otherwise (kind, id or NEW).
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingKind by rememberSaveable { mutableStateOf(Kind.REMINDER) }
    val pager = rememberPagerState(initialPage = Tab.REMINDERS.ordinal) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(openTimer) {
        if (openTimer > 0) { editingId = null; pager.scrollToPage(Tab.TIMER.ordinal) }
    }
    androidx.compose.runtime.LaunchedEffect(openLists) {
        if (openLists > 0) { editingId = null; pager.scrollToPage(Tab.LISTS.ordinal) }
    }

    fun edit(kind: Kind, id: Int) { editingKind = kind; editingId = id }

    // The editor rises in gently over the lists, and sinks back out when closed.
    androidx.compose.animation.AnimatedContent(
        editingId,
        contentKey = { it != null },
        transitionSpec = {
            val ease = androidx.compose.animation.core.FastOutSlowInEasing
            if (targetState != null) {
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
    ) { current ->
    if (current != null) {
        BackHandler { editingId = null }
        EditorScreen(
            existing = items.firstOrNull { it.id == current },
            kind = editingKind,
            newId = { store.newId() },
            onClose = { editingId = null },
        )
    } else
    Column(Modifier.fillMaxSize().background(Nota.Bg)) {
        HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1, userScrollEnabled = false) { page ->
            when (Tab.entries[page]) {
                Tab.TIMER -> dev.ray.reminders.ui.TimerScreen(visible = pager.currentPage == Tab.TIMER.ordinal)
                Tab.ALARMS -> AlarmsScreen(
                    items.filter { it.kind == Kind.ALARM },
                    onAdd = { edit(Kind.ALARM, NEW) }, onEdit = { edit(Kind.ALARM, it) },
                )
                Tab.REMINDERS -> ListScreen(
                    items.filter { it.kind == Kind.REMINDER },
                    onAdd = { edit(Kind.REMINDER, NEW) }, onEdit = { edit(Kind.REMINDER, it) },
                )
                Tab.LISTS -> dev.ray.reminders.ui.ListsScreen()
            }
        }
        // Bottom bar: tap to jump, or swipe between screens.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Nota.Shelf)
                .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Tab.entries.forEach { tab ->
                val selected = pager.currentPage == tab.ordinal
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(tab.ordinal) } }
                        .padding(vertical = 8.dp),
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
        }
    }
    }
}
