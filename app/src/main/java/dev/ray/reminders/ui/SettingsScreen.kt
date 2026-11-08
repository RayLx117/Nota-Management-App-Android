package dev.ray.reminders.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R
import dev.ray.reminders.data.Sound
import dev.ray.reminders.data.Sounds

enum class SettingsPage { THEME, SOUNDS, BACKUP, PERMISSIONS, ABOUT }

/** Ask the Settings tool to open a page (e.g. Permissions from the start-up prompt). */
object SettingsNav { val open = kotlinx.coroutines.flow.MutableStateFlow<SettingsPage?>(null) }

@Composable
fun SettingsScreen(visible: Boolean) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val requested by SettingsNav.open.collectAsState()
    LaunchedEffect(requested) { requested?.let { page = it; SettingsNav.open.value = null } }
    LaunchedEffect(visible) { if (!visible && requested == null) page = null }
    BackHandler(visible && page != null) { page = null }
    AnimatedContent(
        page,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
            else fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(160)))
        },
        modifier = Modifier.fillMaxSize().background(Nota.Bg),
        label = "settings",
    ) { p ->
        when (p) {
            null -> Categories(onOpen = { page = it })
            SettingsPage.THEME -> SubPage("Theme", onBack = { page = null }) { ThemePage() }
            SettingsPage.SOUNDS -> SubPage("Alarm sounds", onBack = { page = null }) { SoundsPage() }
            SettingsPage.BACKUP -> SubPage("Import / Export", onBack = { page = null }) { BackupPage() }
            SettingsPage.PERMISSIONS -> SubPage("Permissions", onBack = { page = null }) { PermissionsList() }
            SettingsPage.ABOUT -> SubPage("About", onBack = { page = null }) { AboutPage() }
        }
    }
}

@Composable
private fun Categories(onOpen: (SettingsPage) -> Unit) {
    val ctx = LocalContext.current
    val default by Sounds.default(ctx).collectAsState()
    val timerDefault by Sounds.timerDefault(ctx).collectAsState()
    val sounds by Sounds.all(ctx).collectAsState()
    val palette = ThemeStore.palette
    val themeName = ThemeStore.nameOf(palette)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(
            "Settings", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Nota.Text,
            modifier = Modifier.padding(top = 18.dp, bottom = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryRow(
                icon = {
                    // The theme's own three colors, overlapping like paint swatches.
                    Box(Modifier.size(width = 40.dp, height = 24.dp)) {
                        listOf(Color(palette.bg), Nota.Mint, Nota.Cyan).forEachIndexed { i, c ->
                            Box(
                                Modifier.offset(x = (i * 8).dp).size(24.dp).clip(CircleShape).background(c)
                                    .border(2.dp, Nota.Shelf, CircleShape),
                            )
                        }
                    }
                },
                title = "Theme", value = themeName, onClick = { onOpen(SettingsPage.THEME) },
            )
            CategoryRow(
                icon = { Icon(painterResource(R.drawable.ic_tab_alarm), null, tint = Nota.Mint, modifier = Modifier.size(24.dp)) },
                title = "Alarm sounds", value = remember(default, timerDefault, sounds) { "Alarms: ${Sounds.name(ctx, default)} · Timers: ${Sounds.name(ctx, timerDefault, timer = true)}" },
                onClick = { onOpen(SettingsPage.SOUNDS) },
            )
            CategoryRow(
                icon = { Icon(painterResource(R.drawable.ic_folder), null, tint = Nota.Mint, modifier = Modifier.size(24.dp)) },
                title = "Import / Export", value = "Move everything to another phone",
                onClick = { onOpen(SettingsPage.BACKUP) },
            )
            var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { refresh++ }
            val missing = remember(refresh) { ctx.missingRequired().size }
            CategoryRow(
                icon = { Icon(painterResource(R.drawable.ic_lock_open), null, tint = if (missing > 0) Nota.Error else Nota.Mint, modifier = Modifier.size(24.dp)) },
                title = "Permissions", value = if (missing == 0) "All set" else "$missing need turning on",
                onClick = { onOpen(SettingsPage.PERMISSIONS) },
            )
            CategoryRow(
                icon = { Dot(size = 14.dp) },
                title = "About", value = "What Nota does",
                onClick = { onOpen(SettingsPage.ABOUT) },
            )
        }
    }
}

@Composable
private fun CategoryRow(icon: @Composable () -> Unit, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nota.Shelf).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Nota.Text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(value, color = Nota.Sub, fontFamily = Nota.Outfit, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Nota.Sub)
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nota.Text) }
            Text(title, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Nota.Text)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp))
}

// ------------------------------------------------------------------------ Theme

private enum class Slot(val title: String) { BG("Background"), ACCENT("Accent"), HIGHLIGHT("Highlight") }

@Composable
private fun ThemePage() {
    val ctx = LocalContext.current
    val current = ThemeStore.palette
    Label("PRESETS")
    ThemeStore.presets.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (name, p) ->
                PresetCard(name, p, selected = p == current, onClick = { ThemeStore.set(ctx, p) }, modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    // Themes you saved: tap to use, hold to rename or delete.
    var managing by remember { mutableStateOf<SavedTheme?>(null) }
    if (ThemeStore.saved.isNotEmpty()) {
        Label("MY THEMES")
        ThemeStore.saved.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    PresetCard(
                        t.name, t.palette, selected = t.palette == current, onClick = { ThemeStore.set(ctx, t.palette) },
                        onLongClick = { managing = t }, modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    managing?.let { t ->
        ThemeNameDialog(
            "Edit theme", t.name, confirm = "Save", onDismiss = { managing = null },
            onDelete = { ThemeStore.deleteTheme(ctx, t.name); managing = null },
        ) { name -> ThemeStore.renameTheme(ctx, t.name, name); managing = null }
    }
    Label("CUSTOM")
    var editing by remember { mutableStateOf<Slot?>(null) }
    var naming by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nota.Shelf).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Slot.entries.forEach { slot ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { editing = slot }.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(slot.title, color = Nota.Text, fontFamily = Nota.Outfit, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(current.get(slot))).border(2.dp, Nota.Outline, CircleShape))
            }
        }
    }
    // Keep this combination as a named theme.
    val alreadySaved = ThemeStore.presets.any { it.second == current } || ThemeStore.saved.any { it.palette == current }
    SoftButton(
        if (alreadySaved) "Saved as ${ThemeStore.nameOf(current)}" else "Save as a theme",
        onClick = { if (!alreadySaved) naming = true }, color = if (alreadySaved) Nota.Sub else Nota.Mint, height = 48.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    if (naming) ThemeNameDialog("Name this theme", "", confirm = "Save", onDismiss = { naming = false }) { name ->
        ThemeStore.saveTheme(ctx, name, current); naming = false
    }
    editing?.let { slot ->
        ColorDialog(slot, start = current, onDone = { editing = null })
    }
}

@Composable
private fun ThemeNameDialog(
    title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val ok = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it.take(24) }, singleLine = true, placeholder = { Text("e.g. Midnight", color = Nota.Sub) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Outline, cursorColor = Nota.Mint,
                        focusedTextColor = Nota.Text, unfocusedTextColor = Nota.Text,
                    ),
                )
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Delete theme", color = Nota.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = ok) { Text(confirm, color = if (ok) Nota.Mint else Nota.Outline) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

private fun Palette.get(slot: Slot) = when (slot) { Slot.BG -> bg; Slot.ACCENT -> accent; Slot.HIGHLIGHT -> highlight }
private fun Palette.with(slot: Slot, c: Int) = when (slot) {
    Slot.BG -> copy(bg = c); Slot.ACCENT -> copy(accent = c); Slot.HIGHLIGHT -> copy(highlight = c)
}

/** A tiny picture of the app in that theme: the shelf, a card with the accent dot, a highlight bar. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PresetCard(
    name: String, p: Palette, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
) {
    val c = remember(p) { ThemeStore.derive(p) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.bg)
                .border(2.dp, if (selected) c.accent else c.raised, RoundedCornerShape(18.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.shelf).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.raised))
            }
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.highlight))
            Text(name, color = c.text, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        if (selected) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(13.dp).clip(CircleShape)
                .background(c.accent).border(2.dp, Nota.Bg, CircleShape),
        )
    }
}

/** Hue, color and brightness sliders. The app recolors live while you drag. */
@Composable
private fun ColorDialog(slot: Slot, start: Palette, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val initial = remember { ThemeStore.hsv(start.get(slot)) }
    // Backgrounds stay dark so text and the status bar stay readable; accents stay bright.
    val vRange = if (slot == Slot.BG) 0f..0.22f else 0.45f..1f
    var h by remember { mutableFloatStateOf(initial[0]) }
    var s by remember { mutableFloatStateOf(initial[1]) }
    var v by remember { mutableFloatStateOf(initial[2].coerceIn(vRange)) }
    fun color() = ThemeStore.fromHsv(h, s, v)
    fun live() = ThemeStore.preview(start.with(slot, color()))

    AlertDialog(
        onDismissRequest = { ThemeStore.preview(start); onDone() },
        containerColor = Nota.Shelf,
        title = { Text(slot.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(color()))
                        .border(2.dp, Nota.Outline, RoundedCornerShape(16.dp)),
                )
                GradientSlider(
                    h / 360f, { h = it * 360f; live() },
                    Brush.horizontalGradient((0..6).map { Color(ThemeStore.fromHsv(it * 60f, 1f, 1f)) }),
                )
                GradientSlider(
                    s, { s = it; live() },
                    Brush.horizontalGradient(listOf(Color(ThemeStore.fromHsv(h, 0f, v)), Color(ThemeStore.fromHsv(h, 1f, v.coerceAtLeast(0.5f))))),
                )
                GradientSlider(
                    (v - vRange.start) / (vRange.endInclusive - vRange.start),
                    { v = vRange.start + it * (vRange.endInclusive - vRange.start); live() },
                    Brush.horizontalGradient(listOf(Color(ThemeStore.fromHsv(h, s, vRange.start)), Color(ThemeStore.fromHsv(h, s, vRange.endInclusive)))),
                )
            }
        },
        confirmButton = { TextButton(onClick = { ThemeStore.set(ctx, start.with(slot, color())); onDone() }) { Text("Done", color = Nota.Mint) } },
        dismissButton = { TextButton(onClick = { ThemeStore.preview(start); onDone() }) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, brush: Brush) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(brush))
        Slider(
            value = value.coerceIn(0f, 1f), onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

// ------------------------------------------------------------------------ Alarm sounds

@Composable
private fun SoundsPage() {
    val ctx = LocalContext.current
    val default by Sounds.default(ctx).collectAsState()
    val timerDefault by Sounds.timerDefault(ctx).collectAsState()
    var removing by remember { mutableStateOf<Sound?>(null) }
    Label("DEFAULT SOUNDS")
    AudioRow(default, onPick = { Sounds.setDefault(ctx, it) }, label = "New alarms")
    AudioRow(timerDefault, onPick = { Sounds.setTimerDefault(ctx, it) }, timer = true, label = "Timers")
    Label("YOUR SOUNDS")
    SoundList(null, onSelect = { }, onDelete = { removing = it })
    Spacer(Modifier.height(4.dp))
    AddSoundButtons(onAdded = { })
    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Nota.Shelf,
            title = { Text("Remove ${s.name}?") },
            text = { Text("Alarms and timers using it will ring with the default instead.", color = Nota.Sub) },
            confirmButton = { TextButton(onClick = { Sounds.remove(ctx, s.id); removing = null }) { Text("Remove", color = Nota.Error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = Nota.Sub) } },
        )
    }
}

// ------------------------------------------------------------------------ About

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text("Nota", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = Nota.Text)
        Dot(size = 12.dp, modifier = Modifier.padding(start = 3.dp, top = 10.dp))
    }
    AboutCard(
        "Everything in one place",
        "Timers and a stopwatch, alarms, reminders that stay pinned until they're done, lists, and notes with a locked password vault. One app instead of five.",
    )
    AboutCard(
        "Made to be yours",
        "Pick a theme or mix your own colors. Give every alarm and timer its own sound, add pictures to reminders and list items, and sort notes into folders, locked behind your PIN or fingerprint if you like.",
    )
    AboutCard(
        "Widgets that do things",
        "Check items off a list, flip between lists, or start a timer preset right from your home screen, without opening the app.",
    )
    Text("Version $version", style = MaterialTheme.typography.bodySmall, color = Nota.Outline, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun AboutCard(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nota.Shelf).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Nota.Mint, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Nota.Text)
    }
}

// ------------------------------------------------------------------------ Import / Export

/** Export everything (passwords too, locked with the PIN) to a file, or replace everything from one. */
@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Export: PIN first (when there are passwords), then where to save.
    var askExportPin by remember { mutableStateOf(false) }
    var exportPin by remember { mutableStateOf<String?>(null) }
    var exportVault by remember { mutableStateOf<dev.ray.reminders.data.VaultData?>(null) }
    // Import: the chosen file, then PIN (if locked), then confirm.
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importPin by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) { exportVault = null; exportPin = null; return@rememberLauncherForActivityResult }
        busy = "Exporting…"
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = dev.ray.reminders.data.Backup.export(ctx, exportVault, exportPin)
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }.isSuccess
            }
            busy = null; exportVault = null; exportPin = null
            message = if (ok) "Exported. Open this file in Nota on your other phone with Import." else "Couldn't save the file."
        }
    }
    fun pickSaveSpot() = save.launch("Nota backup ${java.time.LocalDate.now()}.nota")

    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null || bytes.size < 6 || String(bytes, 0, 4) != "NOTA") { message = "That isn't a Nota backup file."; return@launch }
            importBytes = bytes
            importPin = null
            if (!dev.ray.reminders.data.Backup.isLocked(bytes)) confirmImport = true
        }
    }

    Text(
        "Export saves everything in Nota to one file: reminders, alarms, lists, notes, pictures, sounds, themes and your passwords. " +
            "Passwords stay encrypted, locked with your passwords PIN. Import that file on another phone to bring it all over, PIN included.",
        color = Nota.Sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
    SoftButton(
        "Export to a file", color = Nota.Mint, height = 52.dp, modifier = Modifier.fillMaxWidth(),
        onClick = { if (busy == null) { if (dev.ray.reminders.data.Vault.isSetUp(ctx)) { pinError = null; askExportPin = true } else pickSaveSpot() } },
    )
    SoftButton(
        "Import from a file", height = 52.dp, modifier = Modifier.fillMaxWidth(),
        onClick = { if (busy == null) open.launch(arrayOf("*/*")) },
    )
    Text(
        "Importing replaces everything on this phone with what's in the file.",
        color = Nota.Outline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp),
    )
    busy?.let {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = Nota.Mint, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(it, color = Nota.Sub, modifier = Modifier.padding(start = 10.dp))
        }
    }
    message?.let { Text(it, color = Nota.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }

    if (askExportPin) BackupPinDialog(
        "Enter your passwords PIN", "The file's passwords are locked with it.", pinError,
        onDismiss = { askExportPin = false },
    ) { pin ->
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.ray.reminders.data.Vault.unlockWithPin(ctx, pin) }
            when (result) {
                is dev.ray.reminders.data.Vault.PinResult.Wrong -> pinError = "Wrong PIN."
                is dev.ray.reminders.data.Vault.PinResult.LockedOut -> pinError = "Too many tries. Wait a bit and try again."
                else -> {
                    exportVault = dev.ray.reminders.data.Vault.data.value
                    exportPin = pin
                    dev.ray.reminders.data.Vault.lock()
                    askExportPin = false
                    pickSaveSpot()
                }
            }
        }
    }
    if (importBytes != null && !confirmImport && importPin == null) BackupPinDialog(
        "Enter the backup's PIN", "The passwords PIN from the phone this file came from.", pinError,
        onDismiss = { importBytes = null; pinError = null },
    ) { pin -> pinError = null; importPin = pin; confirmImport = true }
    if (confirmImport && importBytes != null) AlertDialog(
        onDismissRequest = { confirmImport = false; importBytes = null; importPin = null },
        containerColor = Nota.Shelf,
        title = { Text("Replace everything?") },
        text = { Text("Everything on this phone will be replaced with the backup, then Nota restarts.", color = Nota.Sub) },
        confirmButton = {
            TextButton(onClick = {
                val bytes = importBytes ?: return@TextButton
                val pin = importPin
                confirmImport = false
                busy = "Importing…"
                scope.launch {
                    val error = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { dev.ray.reminders.data.Backup.import(ctx, bytes, pin) }.exceptionOrNull()
                    }
                    busy = null
                    when (error) {
                        null -> dev.ray.reminders.data.Backup.restartApp(ctx)
                        is dev.ray.reminders.data.Backup.WrongPin -> { pinError = "That PIN doesn't open this backup."; importPin = null }
                        else -> { importBytes = null; message = "Couldn't import that file." }
                    }
                }
            }) { Text("Replace", color = Nota.Error) }
        },
        dismissButton = { TextButton(onClick = { confirmImport = false; importBytes = null; importPin = null }) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
private fun BackupPinDialog(title: String, line: String, error: String?, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line, color = Nota.Sub, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.OutlinedTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Outline, cursorColor = Nota.Mint,
                        focusedTextColor = Nota.Text, unfocusedTextColor = Nota.Text,
                    ),
                )
                if (error != null) Text(error, color = Nota.Error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(pin) }, enabled = pin.length == 4) { Text("Continue", color = if (pin.length == 4) Nota.Mint else Nota.Outline) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}
