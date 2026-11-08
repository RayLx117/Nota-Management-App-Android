package dev.ray.reminders.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R
import dev.ray.reminders.alarm.Ringer
import dev.ray.reminders.data.Sound
import dev.ray.reminders.data.Sounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the file picker ("Upload audio") or the phone's own sound list, and adds the result to Nota's sounds. */
class SoundAdder(val upload: () -> Unit, val phone: () -> Unit)

@Composable
fun rememberSoundAdder(onAdded: (Sound) -> Unit): SoundAdder {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { Sounds.upload(ctx, uri) }?.let(onAdded)
        }
    }
    val tones = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = if (res.resultCode == Activity.RESULT_OK)
            res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else null
        if (uri != null) onAdded(Sounds.addPhoneSound(ctx, uri))
    }
    return remember {
        SoundAdder(
            upload = { files.launch(arrayOf("audio/*")) },
            phone = {
                tones.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pick a sound"),
                )
            },
        )
    }
}

/** Every sound, with a play button to hear it and a dot on the selected one. */
@Composable
fun SoundList(selected: String, onSelect: (String) -> Unit, onDelete: ((Sound) -> Unit)? = null) {
    val ctx = LocalContext.current
    val sounds by Sounds.all(ctx).collectAsState()
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { Ringer.stopPreview(ctx) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sounds.forEach { s ->
            val on = s.id == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised)
                    .clickable { onSelect(s.id) }.padding(start = 14.dp, end = 4.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .then(if (on) Modifier.background(Nota.Mint) else Modifier.border(2.dp, Nota.Outline, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (on) Dot(size = 8.dp, color = Nota.Ink) }
                Text(
                    s.name, color = if (on) Nota.Text else Nota.Sub, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                IconButton(onClick = {
                    if (playing == s.id) { Ringer.stopPreview(ctx); playing = null }
                    else { Ringer.preview(ctx, s.id); playing = s.id }
                }) {
                    if (playing == s.id) Icon(painterResource(R.drawable.ic_stop), "Stop", tint = Nota.Cyan)
                    else Icon(Icons.Filled.PlayArrow, "Play", tint = Nota.Cyan)
                }
                if (onDelete != null && s.id != Sounds.SYSTEM) IconButton(onClick = { onDelete(s) }) {
                    Icon(Icons.Filled.Delete, "Remove", tint = Nota.Sub, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun AddSoundButtons(onAdded: (Sound) -> Unit) {
    val adder = rememberSoundAdder(onAdded)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoftButton("Upload audio", onClick = adder.upload, color = Nota.Mint, height = 46.dp, modifier = Modifier.weight(1f))
        SoftButton("Phone sounds", onClick = adder.phone, height = 46.dp, modifier = Modifier.weight(1f))
    }
}

/** "Sound  ·  Phone alarm  >" row for an alarm or timer; opens the sound picker. */
@Composable
fun AudioRow(sound: String?, onPick: (String) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val sounds by Sounds.all(ctx).collectAsState()
    val default by Sounds.default(ctx).collectAsState()
    val name = remember(sound, sounds, default) { Sounds.name(ctx, sound) }
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(14.dp)).background(Nota.Raised)
            .clickable(enabled = enabled) { open = true }.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_music), null, tint = Nota.Mint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            name, color = Nota.Text, fontFamily = Nota.Outfit, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Nota.Sub)
    }
    if (open) SoundPickerDialog(Sounds.resolve(ctx, sound), onPick = { onPick(it); open = false }, onDismiss = { open = false })
}

@Composable
fun SoundPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Sound") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoundList(chosen, onSelect = { chosen = it })
                AddSoundButtons(onAdded = { chosen = it.id })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text("Done", color = Nota.Mint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}
