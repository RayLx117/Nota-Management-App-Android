package dev.ray.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.Reminder

/** The Alarms tab: big times, a switch on each, ringing ones highlighted. */
@Composable
fun AlarmsScreen(alarms: List<Reminder>, onAdd: () -> Unit, onEdit: (Int) -> Unit) {
    val sorted = alarms.sortedWith(compareBy<Reminder> { it.hour * 60 + it.minute }.thenBy { it.id })
    Box(Modifier.fillMaxSize().background(Nota.Bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PageHeader("Alarms", Modifier.statusBarsPadding()) }
            if (sorted.isEmpty()) item {
                Text(
                    "No alarms yet.", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            items(sorted, key = { it.id }) { a -> AlarmRow(a, onClick = { onEdit(a.id) }) }
        }
        AddButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

@Composable
private fun AlarmRow(a: Reminder, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val ringing = a.isActive
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (ringing) Nota.Raised else Nota.Shelf)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (a.enabled) 1f else 0.45f)) {
            Text(
                Describe.time(a), fontFamily = Nota.Mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp,
                color = if (ringing) Nota.Mint else Nota.Text,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ringing) Dot(size = 8.dp, modifier = Modifier.padding(end = 6.dp))
                Text(
                    listOf(a.title.takeIf { it.isNotBlank() && it != "Alarm" }, Describe.shortSchedule(a), Describe.whenParts(a).second)
                        .filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, maxLines = 1,
                )
            }
        }
        Switch(
            checked = a.enabled, onCheckedChange = { ReminderEngine.setEnabled(ctx, a.id, it) },
            colors = notaSwitchColors(),
        )
    }
}

/** Big page title with the logo dot, used by tabs without a shelf. */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Text(title, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Nota.Text)
        Dot(size = 9.dp, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
    }
}

/** Timer and Lists are planned but not built yet. */
@Composable
fun ComingSoonScreen(title: String, line: String) {
    Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().padding(horizontal = 16.dp)) {
        PageHeader(title)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Dot(size = 12.dp, color = Nota.Cyan)
                Text("Coming soon", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                    color = Nota.Text, modifier = Modifier.padding(top = 12.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Nota.Sub)
            }
        }
    }
}
