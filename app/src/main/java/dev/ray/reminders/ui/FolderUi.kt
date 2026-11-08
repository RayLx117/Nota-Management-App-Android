package dev.ray.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R

/** A folder in a Notes or Passwords list. */
@Composable
fun FolderRow(name: String, count: Int, locked: Boolean, highlight: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (highlight) Nota.Raised else Nota.Shelf)
            .then(if (highlight) Modifier.border(2.dp, Nota.Mint, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = Nota.Cyan, modifier = Modifier.size(22.dp))
        Text(
            name, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Nota.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (locked) Icon(painterResource(R.drawable.ic_lock), contentDescription = "Locked", tint = Nota.Mint, modifier = Modifier.padding(end = 8.dp).size(16.dp))
        Text(if (locked) "" else count.toString(), style = MaterialTheme.typography.bodyMedium, color = Nota.Sub)
    }
}

/** Header inside an open folder: back, name, and a menu. [onToggleLock] null = no lock option. */
@Composable
fun FolderHeader(
    name: String, locked: Boolean, onBack: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onToggleLock: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nota.Text) }
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = Nota.Cyan, modifier = Modifier.size(20.dp))
        Text(
            name, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Nota.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (locked) Icon(painterResource(R.drawable.ic_lock), contentDescription = "Locked", tint = Nota.Mint, modifier = Modifier.size(18.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Folder options", tint = Nota.Sub) }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Nota.Raised) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                if (onToggleLock != null) DropdownMenuItem(
                    text = { Text(if (locked) "Unlock folder" else "Lock folder") }, onClick = { menu = false; onToggleLock() },
                )
                DropdownMenuItem(text = { Text("Delete folder", color = Nota.Error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun FolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("Folder name", color = Nota.Sub) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Outline,
                    focusedContainerColor = Nota.Raised, unfocusedContainerColor = Nota.Raised, cursorColor = Nota.Mint,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save", color = Nota.Mint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
fun ConfirmFolderDelete(name: String, what: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Delete $name?") },
        text = { Text("The $what inside move out of the folder; nothing else is deleted.", color = Nota.Sub) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = Nota.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

/** "Folder: Work ⌄" row with a dropdown of folders plus "No folder". */
@Composable
fun FolderPicker(folders: List<Pair<Int, String>>, current: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Shelf).clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = Nota.Cyan, modifier = Modifier.size(18.dp))
            Text(
                folders.firstOrNull { it.first == current }?.second ?: "No folder",
                style = MaterialTheme.typography.bodyLarge, color = Nota.Text, modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose folder", tint = Nota.Mint)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Nota.Raised) {
            DropdownMenuItem(text = { Text("No folder") }, onClick = { open = false; onPick(null) })
            folders.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) }) }
        }
    }
}

/** Folder-plus button for the selection bar: moves the selected items into a folder. */
@Composable
fun MoveButton(folders: List<Pair<Int, String>>, current: Int?, onMove: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = "Move to folder", tint = Nota.Cyan, modifier = Modifier.size(26.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Nota.Raised) {
            if (current != null) DropdownMenuItem(text = { Text("Out of this folder") }, onClick = { open = false; onMove(null) })
            folders.filter { it.first != current }.forEach { (id, name) ->
                DropdownMenuItem(text = { Text("Move to $name") }, onClick = { open = false; onMove(id) })
            }
            if (folders.none { it.first != current } && current == null) DropdownMenuItem(
                text = { Text("Make a folder first", color = Nota.Sub) }, onClick = { open = false },
            )
        }
    }
}

/** Shown at the top of an open folder while dragging: drop here to take it out of the folder. */
@Composable
fun DropOutRow(highlight: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (highlight) Nota.Raised else Nota.Shelf)
            .border(2.dp, if (highlight) Nota.Mint else Nota.Outline, RoundedCornerShape(18.dp)).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Drop here to take it out of the folder", color = if (highlight) Nota.Text else Nota.Sub, style = MaterialTheme.typography.bodyMedium)
    }
}
