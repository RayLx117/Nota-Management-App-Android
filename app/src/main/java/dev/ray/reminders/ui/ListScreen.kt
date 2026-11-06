package dev.ray.reminders.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.ray.reminders.alarm.ReminderEngine
import dev.ray.reminders.data.Reminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(reminders: List<Reminder>, onAdd: () -> Unit, onEdit: (Int) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Nota") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reminder") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionCards(needsFullScreen = reminders.any { it.enabled && it.behavior.fullScreen }) }
            if (reminders.isEmpty()) item {
                Text(
                    "No reminders yet. Tap \"New reminder\" to set one up, for example a daily supplement " +
                        "reminder that stays in your notification bar until you tap Done.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
            val sorted = reminders.sortedWith(
                compareByDescending<Reminder> { it.isActive }.thenBy { it.nextTriggerAt ?: Long.MAX_VALUE },
            )
            items(sorted, key = { it.id }) { r -> ReminderRow(r, onClick = { onEdit(r.id) }) }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val colors = if (r.isActive) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = colors) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(Describe.schedule(r), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = r.enabled, onCheckedChange = { ReminderEngine.setEnabled(ctx, r.id, it) })
            }
            Spacer(Modifier.height(4.dp))
            Text(Describe.status(r), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(behaviorTags(r), style = MaterialTheme.typography.bodySmall)
            if (r.isActive) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ReminderEngine.done(ctx, r.id) }) { Text("Done") }
                    TextButton(onClick = { ReminderEngine.snooze(ctx, r.id) }) {
                        Text("Snooze ${Describe.minutes(r.behavior.snoozeMinutes)}")
                    }
                }
            }
        }
    }
}

private fun behaviorTags(r: Reminder): String {
    val b = r.behavior
    return buildList {
        add(if (b.sticky) "Stays until Done" else "Swipeable")
        if (b.nag) add("Repeats every ${Describe.minutes(b.nagEveryMinutes)}")
        if (b.fullScreen) add("Full-screen after ${Describe.minutes(b.fullScreenAfterMinutes)}")
    }.joinToString(" · ")
}

/** Android 14+ needs a few permissions for this to work fully; show what's missing and how to fix it. */
@Composable
private fun PermissionCards(needsFullScreen: Boolean) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Re-checked every time the app comes back to the foreground (e.g. from Settings).
    val (notificationsOk, exactOk, fullScreenOk) = remember(refresh) {
        Triple(
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notificationsOk) PermissionCard(
            "Notifications are off",
            "Reminders can't appear in your notification bar until you allow notifications.",
            "Allow",
        ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (!exactOk) PermissionCard(
            "Exact timing is off",
            "Without the \"Alarms & reminders\" permission, Android may deliver reminders a few minutes late.",
            "Open settings",
        ) { ctx.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
        if (needsFullScreen && !fullScreenOk) PermissionCard(
            "Full-screen alarms are blocked",
            "Android 14 requires you to allow full-screen notifications for this app. Until then they show as a pop-up instead.",
            "Open settings",
        ) { ctx.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onClick) { Text(button) }
        }
    }
}

private fun Context.openSettings(action: String) {
    startActivity(Intent(action, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
