package dev.ray.reminders.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R
import dev.ray.reminders.data.Note
import dev.ray.reminders.data.NotesStore
import dev.ray.reminders.data.Vault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Notes tab: plain notes, and a PIN/fingerprint-locked Passwords page one swipe over. */
@Composable
fun NotesScreen(visible: Boolean) {
    val ctx = LocalContext.current
    val pages = rememberPagerState { 2 }
    // Leaving the tab: back to Notes, and everything locked locks again.
    LaunchedEffect(visible) { if (!visible) { pages.scrollToPage(0); Vault.lock() } }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var newIn by rememberSaveable { mutableStateOf<Int?>(null) }
    // Something locked was tapped: show the PIN pad, then carry on.
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vault by Vault.data.collectAsState()
    LaunchedEffect(vault != null) {
        if (vault != null) {
            NotesStore.get(ctx).reconcile(ctx)
            afterUnlock?.let { afterUnlock = null; it() }
        }
    }
    fun requireUnlock(then: () -> Unit) { if (Vault.isUnlocked()) then() else afterUnlock = then }

    if (afterUnlock != null) {
        BackHandler { afterUnlock = null }
        Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { afterUnlock = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Nota.Text) }
                Text("Unlock", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Nota.Text)
            }
            VaultLock(active = true)
        }
        return
    }

    AnimatedContent(
        editing,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) (fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 12 }) togetherWith fadeOut(tween(160))
            else fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        },
        label = "note editor",
    ) { id ->
        if (id != null) {
            NoteEditor(id, newIn, requireUnlock = ::requireUnlock, onClose = { editing = null })
        } else {
            Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding()) {
                TwoPageHeader("Notes", "Passwords", pages)
                HorizontalPager(pages, modifier = Modifier.weight(1f)) { page ->
                    if (page == 0) NotesPage(
                        onOpen = { note -> editing = note },
                        onNew = { id, folder -> newIn = folder; editing = id },
                        requireUnlock = ::requireUnlock,
                    )
                    else PasswordsPage(active = visible && pages.currentPage == 1)
                }
            }
        }
    }
}

/** "Left  • •  Right" header: the focused page is bright, the other greyed; tap either to switch. */
@Composable
fun TwoPageHeader(left: String, right: String, pages: PagerState) {
    val scope = rememberCoroutineScope()
    val progress = (pages.currentPage + pages.currentPageOffsetFraction).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
            color = lerp(Nota.Text, Nota.Outline, progress),
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { pages.animateScrollToPage(0) }
            },
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(2) { i ->
                val on = if (i == 0) 1f - progress else progress
                Box(Modifier.size(6.dp + 2.dp * on).clip(CircleShape).background(lerp(Nota.Outline, Nota.Mint, on)))
            }
        }
        Text(
            right, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
            color = lerp(Nota.Outline, Nota.Text, progress),
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { pages.animateScrollToPage(1) }
            },
        )
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder, color = Nota.Sub) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Nota.Sub) },
        shape = RoundedCornerShape(16.dp), modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Raised,
            focusedContainerColor = Nota.Shelf, unfocusedContainerColor = Nota.Shelf, cursorColor = Nota.Mint,
        ),
    )
}

private fun shortWhen(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(DateTimeFormatter.ofPattern("h:mm a"))
    else t.format(DateTimeFormatter.ofPattern("MMM d"))
}

@Composable
private fun NotesPage(onOpen: (Int) -> Unit, onNew: (Int, Int?) -> Unit, requireUnlock: (() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { NotesStore.get(ctx) }
    val notes by store.notes.collectAsState()
    val folders by store.folders.collectAsState()
    val vault by Vault.data.collectAsState()
    val unlocked = vault != null
    var query by rememberSaveable { mutableStateOf("") }
    var openFolder by rememberSaveable { mutableStateOf<Int?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    val folder = folders.firstOrNull { it.id == openFolder }
    if (openFolder != null && folder == null) openFolder = null
    if (folder != null) BackHandler { openFolder = null }
    val sel = rememberSelection(notes.map { it.id }.toSet())

    // What a card shows: locked notes reveal their text only while unlocked.
    fun visible(n: Note): Note = if (n.encrypted) Vault.secretNote(n.id)?.let { n.copy(title = it.title, body = it.body) } ?: n else n
    val searching = query.isNotBlank()
    val shown = notes.map(::visible).filter { n ->
        when {
            searching -> (!n.encrypted || unlocked) && (n.title.contains(query, true) || n.body.contains(query, true))
            folder != null -> n.folderId == folder.id
            else -> n.folderId == null || folders.none { it.id == n.folderId }
        }
    }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val folderLocked = folder?.locked == true && !unlocked

    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxSize()) {
            when {
                sel.active -> SelectionBar(sel, "note", onDelete = { ids -> store.delete(ids); if (unlocked) Vault.removeSecretNotes(ctx, ids) })
                folder != null -> FolderHeader(
                    folder.name, folder.locked, onBack = { openFolder = null },
                    onRename = { renameFolder = true },
                    onDelete = { requireUnlock { deleteFolder = true } },
                    onToggleLock = {
                        requireUnlock {
                            store.updateFolder(folder.copy(locked = !folder.locked))
                            store.reconcile(ctx)
                        }
                    },
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(query, { query = it }, "Search notes", Modifier.weight(1f))
                    IconButton(onClick = { newFolder = true }) {
                        Icon(painterResource(R.drawable.ic_folder_add), contentDescription = "New folder", tint = Nota.Cyan, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (folderLocked) {
                Column(
                    Modifier.fillMaxSize().padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(34.dp))
                    Text("This folder is locked", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Nota.Text, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(14.dp))
                    SoftButton("Unlock", onClick = { requireUnlock {} }, color = Nota.Mint, height = 46.dp)
                }
            } else LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (folder == null && !searching && !sel.active) items(folders, key = { "f${it.id}" }) { f ->
                    FolderRow(f.name, notes.count { it.folderId == f.id }, f.locked) {
                        if (f.locked) requireUnlock { openFolder = f.id } else openFolder = f.id
                    }
                }
                if (shown.isEmpty() && (folder != null || folders.isEmpty() || searching)) item {
                    Text(
                        when { searching -> "No matches."; folder != null -> "Nothing in this folder yet."; else -> "No notes yet." },
                        style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(shown, key = { it.id }) { note ->
                    NoteCard(note, hidden = note.encrypted && !unlocked, sel = sel, onOpen = {
                        if (store.isProtected(note)) requireUnlock { onOpen(note.id) } else onOpen(note.id)
                    })
                }
            }
        }
        if (!sel.active && !folderLocked) AddButton(
            onClick = { onNew(store.newId(), folder?.id) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(vertical = 20.dp, horizontal = 4.dp),
        )
    }

    if (newFolder) FolderNameDialog("New folder", "", onDismiss = { newFolder = false }) { name ->
        newFolder = false; openFolder = store.addFolder(name)
    }
    if (renameFolder && folder != null) FolderNameDialog("Rename folder", folder.name, onDismiss = { renameFolder = false }) { name ->
        renameFolder = false; store.updateFolder(folder.copy(name = name))
    }
    if (deleteFolder && folder != null) ConfirmFolderDelete(folder.name, "notes", onDismiss = { deleteFolder = false }) {
        deleteFolder = false; store.deleteFolder(folder.id); store.reconcile(ctx); openFolder = null
    }
}

@Composable
private fun NoteCard(note: Note, hidden: Boolean, sel: Selection, onOpen: () -> Unit) {
    val title = if (hidden) "Locked note" else note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
    val body = when {
        hidden -> ""
        note.title.isBlank() -> note.body.lineSequence().dropWhile { it.isBlank() }.drop(1).joinToString("\n")
        else -> note.body
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nota.Shelf)
            .selectable(sel, note.id, onOpen).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (sel.active) SelectCircle(sel.has(note.id), Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = if (hidden) Nota.Sub else Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotBlank()) Text(
                body.trim(), style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(shortWhen(note.updatedAt), style = MaterialTheme.typography.bodySmall, color = Nota.Outline, modifier = Modifier.padding(top = 8.dp))
        }
        if (note.locked) Icon(painterResource(R.drawable.ic_lock), contentDescription = "Locked", tint = Nota.Mint, modifier = Modifier.padding(start = 8.dp).size(16.dp))
        if (note.pinned) Icon(painterResource(R.drawable.ic_pin), contentDescription = "Pinned", tint = Nota.Mint, modifier = Modifier.padding(start = 8.dp).size(16.dp))
    }
}

/** Full-screen note editor; saves as you type, and drops the note if you leave it empty. */
@Composable
private fun NoteEditor(id: Int, newFolder: Int?, requireUnlock: (() -> Unit) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { NotesStore.get(ctx) }
    val folders by store.folders.collectAsState()
    val original = remember { store.notes.value.firstOrNull { it.id == id } }
    val secret = remember { original?.takeIf { it.encrypted }?.let { Vault.secretNote(it.id) } }
    var title by rememberSaveable { mutableStateOf(secret?.title ?: original?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(secret?.body ?: original?.body.orEmpty()) }
    var pinned by rememberSaveable { mutableStateOf(original?.pinned ?: false) }
    var locked by rememberSaveable { mutableStateOf(original?.locked ?: false) }
    var folderId by rememberSaveable { mutableStateOf(original?.folderId ?: newFolder) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(Triple(title, body, Triple(pinned, locked, folderId))) }
    val vault by Vault.data.collectAsState()

    fun isProtected() = locked || folders.firstOrNull { it.id == folderId }?.locked == true
    fun persist() {
        val state = Triple(title, body, Triple(pinned, locked, folderId))
        if (state == lastSaved && original != null) return
        val meta = (store.notes.value.firstOrNull { it.id == id } ?: original ?: Note(id)).copy(
            pinned = pinned, locked = locked, folderId = folderId, updatedAt = System.currentTimeMillis(),
        )
        if (title.isBlank() && body.isBlank()) {
            store.delete(setOf(id)); Vault.removeSecretNotes(ctx, setOf(id))
        } else if (isProtected()) {
            if (!Vault.isUnlocked()) return // can't write locked text without the key
            Vault.putSecretNote(ctx, dev.ray.reminders.data.SecretNote(id, title, body))
            store.upsert(meta.copy(title = "", body = "", encrypted = true))
        } else {
            store.upsert(meta.copy(title = title, body = body, encrypted = false))
            if (Vault.isUnlocked()) Vault.removeSecretNotes(ctx, setOf(id))
        }
        lastSaved = state
    }
    fun close() { persist(); onClose() }

    // Autosave shortly after you stop typing.
    LaunchedEffect(title, body, pinned, locked, folderId) { delay(500); persist() }
    // If Nota locks while a locked note is open (app went to the background), close it.
    LaunchedEffect(vault == null) { if (vault == null && isProtected()) onClose() }
    BackHandler { close() }

    Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nota.Text) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { pinned = !pinned }) {
                Icon(painterResource(R.drawable.ic_pin), contentDescription = if (pinned) "Unpin" else "Pin to top", tint = if (pinned) Nota.Mint else Nota.Sub, modifier = Modifier.size(20.dp))
            }
            // Lock this note: needs the PIN set up and entered first.
            IconButton(onClick = { requireUnlock { locked = !locked } }) {
                Icon(
                    painterResource(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open),
                    contentDescription = if (locked) "Unlock note" else "Lock note",
                    tint = if (locked) Nota.Mint else Nota.Sub, modifier = Modifier.size(20.dp),
                )
            }
            if (original != null) IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Nota.Error)
            }
        }
        if (folders.isNotEmpty()) FolderPicker(
            folders.map { it.id to it.name + if (it.locked) " (locked)" else "" }, folderId,
            onPick = { f ->
                // Moving into or out of a locked folder re-encrypts the note, so it needs the PIN.
                val target = folders.firstOrNull { it.id == f }
                if (target?.locked == true || isProtected()) requireUnlock { folderId = f } else folderId = f
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            BasicTextField(
                value = title, onValueChange = { title = it },
                textStyle = TextStyle(fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Nota.Text),
                cursorBrush = SolidColor(Nota.Mint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("Title", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Nota.Outline)
                    inner()
                },
            )
            Text(
                (original?.updatedAt ?: System.currentTimeMillis()).let { "Edited " + shortWhen(it) } + if (isProtected()) " · locked" else "",
                style = MaterialTheme.typography.bodySmall, color = Nota.Outline, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            BasicTextField(
                value = body, onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Nota.Text, lineHeight = 24.sp),
                cursorBrush = SolidColor(Nota.Mint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                decorationBox = { inner ->
                    if (body.isEmpty()) Text("Note", style = MaterialTheme.typography.bodyLarge, color = Nota.Outline)
                    inner()
                },
            )
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Nota.Shelf,
        title = { Text("Delete this note?") },
        confirmButton = {
            TextButton(onClick = {
                store.delete(setOf(id)); Vault.removeSecretNotes(ctx, setOf(id)); confirmDelete = false; onClose()
            }) { Text("Delete", color = Nota.Error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Nota.Sub) } },
    )
}
