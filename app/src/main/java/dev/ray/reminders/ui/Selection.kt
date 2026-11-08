package dev.ray.reminders.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.alarm.ReminderEngine

/** Press-and-hold multi-select for the Alarms and Reminders lists. Empty set = not selecting. */
class Selection(private val ids: () -> Set<Int>, private val set: (Set<Int>) -> Unit) {
    val selected: Set<Int> get() = ids()
    val active: Boolean get() = selected.isNotEmpty()
    fun has(id: Int) = id in selected
    fun toggle(id: Int) = set(if (id in selected) selected - id else selected + id)
    fun clear() = set(emptySet())
}

@Composable
fun rememberSelection(existing: Set<Int>): Selection {
    var ids by remember { mutableStateOf(emptySet<Int>()) }
    // Drop anything that no longer exists (deleted elsewhere).
    if (!existing.containsAll(ids)) ids = ids intersect existing
    val sel = Selection({ ids }, { ids = it })
    BackHandler(enabled = sel.active) { sel.clear() }
    return sel
}

/** Tap opens (or toggles while selecting); press and hold starts selecting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.selectable(sel: Selection, id: Int, onOpen: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = { if (sel.active) sel.toggle(id) else onOpen() },
        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); sel.toggle(id) },
    )
}

/** The check circle shown on every row while selecting. */
@Composable
fun SelectCircle(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(26.dp).clip(CircleShape)
            .then(if (checked) Modifier.background(Nota.Mint) else Modifier.border(2.dp, Nota.Outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Nota.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

/** Replaces the page title while selecting: close, count, and the trash can on the right. */
@Composable
fun SelectionBar(
    sel: Selection, noun: String, modifier: Modifier = Modifier,
    /** What deleting does; reminders/alarms by default. */
    onDelete: ((Set<Int>) -> Unit)? = null,
    /** Extra buttons before the trash can (e.g. Move to folder). */
    extra: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val n = sel.selected.size
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { sel.clear() }) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Nota.Text)
        }
        Text(
            "$n selected", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Nota.Text,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        extra?.invoke()
        IconButton(onClick = { confirm = true }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Nota.Error, modifier = Modifier.size(28.dp))
        }
    }
    if (confirm) {
        val what = if (n == 1) noun else "$n ${noun}s"
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete $what?") },
            confirmButton = {
                TextButton(onClick = { (onDelete ?: { deleteAll(ctx, it) })(sel.selected); sel.clear(); confirm = false }) {
                    Text("Delete", color = Nota.Error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun deleteAll(ctx: Context, ids: Set<Int>) = ids.forEach { ReminderEngine.delete(ctx, it) }
