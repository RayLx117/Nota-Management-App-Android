package dev.ray.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        setContent { RemindersTheme { App() } }
    }
}

private const val NEW = -1

@Composable
private fun App() {
    val ctx = LocalContext.current
    val store = remember { ReminderStore.get(ctx) }
    val items by store.items.collectAsState()
    // null = list, NEW = new reminder, otherwise the id being edited
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }

    val current = editing
    if (current == null) {
        ListScreen(items, onAdd = { editing = NEW }, onEdit = { editing = it })
    } else {
        BackHandler { editing = null }
        EditorScreen(
            existing = items.firstOrNull { it.id == current },
            newId = { store.newId() },
            onClose = { editing = null },
        )
    }
}
