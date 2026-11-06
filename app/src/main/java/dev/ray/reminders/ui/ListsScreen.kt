package dev.ray.reminders.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.data.ListItem
import dev.ray.reminders.data.ListType
import dev.ray.reminders.data.ListsStore
import dev.ray.reminders.data.NotaList
import dev.ray.reminders.data.Status
import kotlinx.coroutines.launch

/** The Lists tab: every list is a rounded card; swipe between them, the last card makes a new one. */
@Composable
fun ListsScreen() {
    val ctx = LocalContext.current
    val store = remember { ListsStore.get(ctx) }
    val lists by store.lists.collectAsState()
    val pager = rememberPagerState { lists.size + 1 }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }

    // After creating a list, slide over to it once it exists.
    LaunchedEffect(jumpTo, lists.size) {
        val id = jumpTo ?: return@LaunchedEffect
        val index = lists.indexOfFirst { it.id == id }
        if (index >= 0) { pager.animateScrollToPage(index); jumpTo = null }
    }

    Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding()) {
        PageHeader("Lists", Modifier.padding(horizontal = 16.dp))
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 26.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f).padding(top = 4.dp),
        ) { page ->
            val list = lists.getOrNull(page)
            if (list == null) NewListCard { creating = true } else ListCard(list, store)
        }
        // Where you are among your lists.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            repeat(lists.size + 1) { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier.size(if (on) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (on) Nota.Mint else Nota.Outline)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }
    }

    if (creating) NewListDialog(
        onDismiss = { creating = false },
        onCreate = { name, type -> creating = false; jumpTo = store.create(name, type) },
    )
}

@Composable
private fun CardFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Nota.Shelf)) { content() }
}

@Composable
private fun NewListCard(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).border(2.dp, Nota.Outline, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AddButton(onClick = onClick)
            Text(
                "New list", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                color = Nota.Text, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------ one list

@Composable
private fun ListCard(list: NotaList, store: ListsStore) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingStatuses by remember { mutableStateOf(false) }
    // Status list item dialog: null = closed; id 0 = new item.
    var editingItem by remember { mutableStateOf<ListItem?>(null) }

    CardFrame {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 6.dp, top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (list.type == ListType.CHECKLIST) "CHECKLIST" else "STATUS LIST",
                        style = Nota.Label, color = Nota.Sub,
                    )
                    Text(
                        list.name, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                        color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (list.type == ListType.CHECKLIST) {
                    val any = list.items.any { it.checked }
                    IconButton(onClick = { store.clearChecked(list.id) }, enabled = any) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = "Clear checked items",
                            tint = if (any) Nota.Error else Nota.Outline,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "List options", tint = Nota.Sub)
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Nota.Raised) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                        if (list.type == ListType.STATUS) {
                            DropdownMenuItem(text = { Text("Edit statuses") }, onClick = { menu = false; editingStatuses = true })
                        }
                        DropdownMenuItem(
                            text = { Text("Delete list", color = Nota.Error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).padding(end = 12.dp)) {
                when (list.type) {
                    ListType.CHECKLIST -> ChecklistBody(list, store)
                    ListType.STATUS -> StatusBody(
                        list,
                        onEditStatuses = { editingStatuses = true },
                        onAdd = { status -> editingItem = ListItem(0, "", statusId = status) },
                        onOpen = { editingItem = it },
                    )
                }
            }
        }
    }

    if (renaming) NameDialog("Rename list", list.name, "Save", onDismiss = { renaming = false }) { name ->
        store.update(list.id) { it.copy(name = name) }; renaming = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Nota.Shelf,
        title = { Text("Delete ${list.name}?") },
        text = { Text("Everything in this list will be gone.", color = Nota.Sub) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; store.delete(list.id) }) { Text("Delete", color = Nota.Error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Nota.Sub) } },
    )
    if (editingStatuses) StatusesDialog(list, store, onDismiss = { editingStatuses = false })
    editingItem?.let { item ->
        ItemDialog(list, item, onDismiss = { editingItem = null }, onSave = { saved ->
            if (saved.id == 0) store.addItem(list.id, saved.text, saved.description, saved.statusId)
            else store.updateItem(list.id, saved)
            editingItem = null
        }, onDelete = { store.deleteItem(list.id, item.id); editingItem = null })
    }
}

// ------------------------------------------------------------------------ checklist

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistBody(list: NotaList, store: ListsStore) {
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ListItem?>(null) }
    fun add() {
        if (draft.isNotBlank()) store.addItem(list.id, draft.trim())
        draft = ""
    }
    Column {
        Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                placeholder = { Text("Add item", color = Nota.Sub) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.padding(start = 10.dp).size(48.dp).clip(CircleShape)
                    .background(if (draft.isBlank()) Nota.Raised else Nota.Mint).clickable { add() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = if (draft.isBlank()) Nota.Sub else Nota.Ink)
            }
        }
        val ordered = list.items.filterNot { it.checked } + list.items.filter { it.checked }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(ordered, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { store.updateItem(list.id, item.copy(checked = !item.checked)) },
                            onLongClick = { editing = item },
                        )
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectCircle(item.checked)
                    Text(
                        item.text, style = MaterialTheme.typography.bodyLarge,
                        color = if (item.checked) Nota.Sub else Nota.Text,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
    editing?.let { item ->
        ItemDialog(list, item, onDismiss = { editing = null }, onSave = { store.updateItem(list.id, it); editing = null },
            onDelete = { store.deleteItem(list.id, item.id); editing = null })
    }
}

// ------------------------------------------------------------------------ status list

private val SECTION_COLORS = listOf(Nota.Mint, Nota.Cyan, Nota.Violet, Nota.Amber, Nota.Pink)

@Composable
private fun StatusBody(
    list: NotaList,
    onEditStatuses: () -> Unit,
    onAdd: (Int?) -> Unit,
    onOpen: (ListItem) -> Unit,
) {
    if (list.statuses.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Add your statuses first.", style = MaterialTheme.typography.bodyLarge, color = Nota.Sub)
            Spacer(Modifier.height(14.dp))
            SoftButton("Add statuses", onClick = onEditStatuses, color = Nota.Mint, height = 46.dp)
        }
        return
    }
    val known = list.statuses.map { it.id }.toSet()
    val loose = list.items.filter { it.statusId == null || it.statusId !in known }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)) {
            list.statuses.forEachIndexed { i, status ->
                val color = SECTION_COLORS[i % SECTION_COLORS.size]
                val inSection = list.items.filter { it.statusId == status.id }
                item(key = "s${status.id}") { SectionHeader(status.name, inSection.size, color) { onAdd(status.id) } }
                items(inSection, key = { "i${it.id}" }) { StatusItemRow(it, color) { onOpen(it) } }
            }
            if (loose.isNotEmpty()) {
                item(key = "none") { SectionHeader("No status", loose.size, Nota.Outline, onAdd = null) }
                items(loose, key = { "i${it.id}" }) { StatusItemRow(it, Nota.Outline) { onOpen(it) } }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp).size(52.dp).clip(CircleShape)
                .background(Nota.Mint).clickable { onAdd(list.statuses.first().id) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Add item", tint = Nota.Ink) }
    }
}

@Composable
private fun SectionHeader(name: String, count: Int, color: Color, onAdd: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(size = 9.dp, color = color)
        Text(
            name.uppercase(), style = Nota.Label, color = Nota.Text,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("  $count", style = Nota.Label, color = Nota.Sub)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) Box(
            Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Add to $name", tint = color, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun StatusItemRow(item: ListItem, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Nota.Raised)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(if (item.description.isBlank()) 20.dp else 36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Column(Modifier.padding(start = 12.dp)) {
            Text(item.text, style = MaterialTheme.typography.titleMedium, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) Text(
                item.description, style = MaterialTheme.typography.bodySmall, color = Nota.Sub,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------------ dialogs

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Outline,
    focusedContainerColor = Nota.Raised, unfocusedContainerColor = Nota.Raised,
    focusedLabelColor = Nota.Mint, unfocusedLabelColor = Nota.Sub, cursorColor = Nota.Mint,
)

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, ListType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ListType.CHECKLIST) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("New list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    placeholder = { Text("List name", color = Nota.Sub) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                TypeOption("Checklist", "Check things off, then clear them", type == ListType.CHECKLIST) { type = ListType.CHECKLIST }
                TypeOption("Status list", "Sort items into your own statuses", type == ListType.STATUS) { type = ListType.STATUS }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), type) }, enabled = name.isNotBlank()) {
                Text("Create", color = if (name.isNotBlank()) Nota.Mint else Nota.Outline)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
private fun TypeOption(title: String, line: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nota.Raised)
            .border(2.dp, if (selected) Nota.Mint else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Nota.Text)
            Text(line, style = MaterialTheme.typography.bodySmall, color = Nota.Sub)
        }
        if (selected) Dot(size = 10.dp)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm, color = Nota.Mint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

/** Add or edit an item. Status lists also get a description and a status; checklists just the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(list: NotaList, item: ListItem, onDismiss: () -> Unit, onSave: (ListItem) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(item.text) }
    var description by remember { mutableStateOf(item.description) }
    var status by remember { mutableStateOf(item.statusId) }
    val isNew = item.id == 0
    val isStatus = list.type == ListType.STATUS
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text(if (isNew) "New item" else "Edit item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                if (isStatus) {
                    OutlinedTextField(
                        value = description, onValueChange = { description = it }, label = { Text("Description") },
                        minLines = 2, maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("STATUS", style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.statuses.forEach { s -> DotChip(s.name, status == s.id) { status = s.id } }
                    }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete item", color = Nota.Error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(item.copy(text = text.trim(), description = description.trim(), statusId = status)) },
                enabled = text.isNotBlank(),
            ) { Text(if (isNew) "Add" else "Save", color = if (text.isNotBlank()) Nota.Mint else Nota.Outline) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
}

/** Add, rename, reorder and remove a status list's statuses. Changes apply as you go. */
@Composable
private fun StatusesDialog(list: NotaList, store: ListsStore, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    fun setStatuses(change: (List<Status>) -> List<Status>) = store.update(list.id) { it.copy(statuses = change(it.statuses)) }
    fun add() {
        if (draft.isNotBlank()) store.update(list.id) { it.copy(statuses = it.statuses + Status(it.nextStatusId(), draft.trim())) }
        draft = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Statuses") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.statuses.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(size = 9.dp, color = SECTION_COLORS[i % SECTION_COLORS.size])
                        OutlinedTextField(
                            value = s.name, singleLine = true,
                            onValueChange = { v -> setStatuses { l -> l.map { if (it.id == s.id) it.copy(name = v) else it } } },
                            shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Column {
                            Icon(
                                Icons.Filled.KeyboardArrowUp, contentDescription = "Move up",
                                tint = if (i > 0) Nota.Text else Nota.Outline,
                                modifier = Modifier.size(26.dp).clickable(enabled = i > 0) {
                                    setStatuses { l -> l.toMutableList().apply { add(i - 1, removeAt(i)) } }
                                },
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown, contentDescription = "Move down",
                                tint = if (i < list.statuses.lastIndex) Nota.Text else Nota.Outline,
                                modifier = Modifier.size(26.dp).clickable(enabled = i < list.statuses.lastIndex) {
                                    setStatuses { l -> l.toMutableList().apply { add(i + 1, removeAt(i)) } }
                                },
                            )
                        }
                        Icon(
                            Icons.Filled.Close, contentDescription = "Remove", tint = Nota.Error,
                            modifier = Modifier.padding(start = 2.dp).size(24.dp).clickable { setStatuses { l -> l.filterNot { it.id == s.id } } },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, singleLine = true,
                        placeholder = { Text("New status", color = Nota.Sub) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape)
                            .background(if (draft.isBlank()) Nota.Raised else Nota.Mint).clickable { add() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add status", tint = if (draft.isBlank()) Nota.Sub else Nota.Ink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Nota.Mint) } },
    )
}
