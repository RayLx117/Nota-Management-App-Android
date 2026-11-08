package dev.ray.reminders.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Everything Nota asks Android for, with how to check it and where to turn it on. */
enum class Perm(val title: String, val why: String, val required: Boolean) {
    NOTIFICATIONS("Notifications", "Shows reminders, alarms and timers.", true),
    EXACT("Exact timing", "Makes alarms and reminders go off on the minute.", true),
    FULL_SCREEN("Full-screen alarms", "Lets alarms and timers take over the screen when they ring.", true),
    BATTERY("Unrestricted battery", "Stops the phone from putting Nota to sleep and delaying alarms.", false),
    LIVE("Live countdown", "Shows a running timer on the lock screen and in the status bar.", false),
}

fun Context.granted(p: Perm): Boolean = when (p) {
    Perm.NOTIFICATIONS -> checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    Perm.EXACT -> getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    Perm.FULL_SCREEN -> fullScreenAllowed()
    Perm.BATTERY -> getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    // Android 16 call, looked up by name so older phones (and older SDKs) don't trip over it.
    Perm.LIVE -> runCatching {
        NotificationManager::class.java.getMethod("canPostPromotedNotifications")
            .invoke(getSystemService(NotificationManager::class.java)) as Boolean
    }.getOrDefault(true)
}

/** The essentials that are missing (shown as a prompt when Nota opens). */
fun Context.missingRequired(): List<Perm> = Perm.entries.filter { it.required && !granted(it) }

@SuppressLint("BatteryLife")
private fun Context.openFor(p: Perm) {
    val pkg = Uri.parse("package:$packageName")
    val intent = when (p) {
        Perm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        Perm.EXACT -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
        Perm.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
        Perm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        Perm.LIVE -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Settings > Permissions: each one with a tick, or a button to turn it on. */
@Composable
fun PermissionsList() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var asked by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true; refresh++ }
    val states = remember(refresh) { Perm.entries.associateWith { ctx.granted(it) } }
    Perm.entries.forEach { p ->
        val ok = states[p] == true
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nota.Shelf).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, color = Nota.Text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (ok) Icon(Icons.Filled.Check, contentDescription = "Allowed", tint = Nota.Mint, modifier = Modifier.size(22.dp))
                else Text(if (p.required) "NEEDED" else "RECOMMENDED", style = Nota.Label, color = if (p.required) Nota.Error else Nota.Amber)
            }
            Text(p.why, style = MaterialTheme.typography.bodyMedium, color = Nota.Sub)
            if (!ok) SoftButton(
                "Turn on", color = Nota.Mint, height = 42.dp, modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                onClick = {
                    // Ask in-app first; once Android stops asking, send them to the app's notification settings.
                    if (p == Perm.NOTIFICATIONS && !asked) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.openFor(p)
                },
            )
        }
    }
}

/** Shown once when Nota opens with something essential switched off. */
@Composable
fun PermissionPrompt(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(!PermissionPromptShown.done && ctx.missingRequired().isNotEmpty()) }
    if (!show) return
    fun close() { PermissionPromptShown.done = true; show = false }
    val missing = remember { ctx.missingRequired() }
    AlertDialog(
        onDismissRequest = ::close,
        containerColor = Nota.Shelf,
        title = { Text("A few permissions are off") },
        text = {
            Text(
                missing.joinToString("\n") { "• " + it.title } + "\n\nAlarms and reminders won't work properly without them.",
                color = Nota.Sub,
            )
        },
        confirmButton = { TextButton(onClick = { close(); onOpen() }) { Text("Set them up", color = Nota.Mint) } },
        dismissButton = { TextButton(onClick = ::close) { Text("Later", color = Nota.Sub) } },
    )
}

/** Once per app launch. */
object PermissionPromptShown { var done = false }
