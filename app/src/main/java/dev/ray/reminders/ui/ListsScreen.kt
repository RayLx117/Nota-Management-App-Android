package dev.ray.reminders.ui

import androidx.compose.material.icons.filled.Menu
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.ray.reminders.alarm.ListAlarms
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState

/** A list to jump to when the Lists tab opens (from a widget). */
object ListsNav { val open = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null) }

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
    var arrangingLists by remember { mutableStateOf(false) }

    // Opened from a widget: go straight to that list.
    val requested by ListsNav.open.collectAsState()
    LaunchedEffect(requested, lists.size) {
        val id = requested ?: return@LaunchedEffect
        val index = lists.indexOfFirst { it.id == id }
        if (index >= 0) { pager.scrollToPage(index); ListsNav.open.value = null }
    }

    // After creating a list, slide over to it once it exists.
    LaunchedEffect(jumpTo, lists.size) {
        val id = jumpTo ?: return@LaunchedEffect
        val index = lists.indexOfFirst { it.id == id }
        if (index >= 0) { pager.animateScrollToPage(index); jumpTo = null }
    }

    Column(Modifier.fillMaxSize().background(Nota.Bg).statusBarsPadding()) {
        PageHeader("Lists", Modifier.padding(horizontal = 16.dp))
        if (arrangingLists) {
            ArrangeLists(lists, store, Modifier.weight(1f)) { arrangingLists = false }
            return@Column
        }
        HorizontalPager(
            pager,
            // The open list fills almost the whole width; only a sliver of its neighbours shows.
            contentPadding = PaddingValues(horizontal = 10.dp),
            pageSpacing = 5.dp,
            modifier = Modifier.weight(1f).padding(top = 4.dp),
        ) { page ->
            val list = lists.getOrNull(page)
            if (list == null) NewListCard { creating = true } else ListCard(list, store, onArrange = { arrangingLists = true })
        }
        // Where you are among your lists.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(lists.size + 1) { i ->
                val on = pager.currentPage == i
                if (i == lists.size) {
                    // The last spot is the "New list" card, so it's a + instead of a dot.
                    Icon(
                        Icons.Filled.Add, contentDescription = "New list",
                        tint = if (on) Nota.Mint else Nota.Sub,
                        modifier = Modifier.size(14.dp).clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                } else Box(
                    Modifier.align(Alignment.CenterVertically).size(if (on) 8.dp else 6.dp).clip(CircleShape)
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
private fun CardFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Nota.Shelf)) { content() }
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

/** Every list as a wobbling tile: drag them into a new order. */
@Composable
private fun ArrangeLists(lists: List<NotaList>, store: ListsStore, modifier: Modifier, onDone: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val arrange = rememberArrangeState(listState)
    val wobble = rememberWobble()
    val sel = remember { alwaysArranging() }
    val displayed = arrange.order?.mapNotNull { id -> lists.firstOrNull { it.id == id } } ?: lists
    BackHandler(onBack = onDone)
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Drag lists to reorder", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", color = Nota.Mint) }
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        ) {
            items(displayed, key = { it.id }) { l ->
                val dragging = arrange.dragging == l.id
                Row(
                    (if (dragging) Modifier else Modifier.animateItem())
                        .arrangeable(
                            arrange, sel, l.id, true, wobble,
                            order = { displayed.map { it.id } }, canSwap = { _, _ -> true }, onOpen = {},
                            onDrop = { d -> if (d.moved) store.arrange(d.order) },
                        )
                        .fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nota.Shelf)
                        .border(1.dp, if (dragging) Nota.Mint else Nota.Outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (l.type == ListType.CHECKLIST) "CHECKLIST" else "CATEGORIZED LIST",
                            style = Nota.Label, color = Nota.Sub,
                        )
                        Text(
                            l.name, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                            color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${l.items.size} item" + if (l.items.size == 1) "" else "s",
                            style = MaterialTheme.typography.bodySmall, color = Nota.Sub,
                        )
                    }
                    Icon(Icons.Filled.Menu, contentDescription = null, tint = Nota.Sub)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ one list

@Composable
private fun ListCard(list: NotaList, store: ListsStore, onArrange: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingStatuses by remember { mutableStateOf(false) }
    // Status list item dialog: null = closed; id 0 = new item.
    var editingItem by remember { mutableStateOf<ListItem?>(null) }

    // Holding an empty spot or the edge of the card (not an item) picks the lists up to reorder them.
    CardFrame(Modifier.holdOnEmpty(onArrange)) {
        Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
            Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (list.type == ListType.CHECKLIST) "CHECKLIST" else "CATEGORIZED LIST",
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
                            DropdownMenuItem(text = { Text("Edit categories") }, onClick = { menu = false; editingStatuses = true })
                        }
                        DropdownMenuItem(
                            text = { Text("Delete list", color = Nota.Error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            // Categorized lists run their category outlines close to the card's edge.
            Box(Modifier.weight(1f).padding(horizontal = if (list.type == ListType.STATUS) 6.dp else 12.dp)) {
                when (list.type) {
                    ListType.CHECKLIST -> ChecklistBody(list, store)
                    ListType.STATUS -> StatusBody(
                        list, store,
                        onEditStatuses = { editingStatuses = true },
                        onAdd = { section -> editingItem = ListItem(0, "", statusIds = listOf(section)) },
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
            if (saved.id == 0) store.addItem(list.id, saved)
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
    // Items opened up to show their full notes and picture.
    var open by remember { mutableStateOf(setOf<Int>()) }
    fun add() {
        if (draft.isNotBlank()) store.addItem(list.id, ListItem(0, draft.trim()))
        draft = ""
    }
    // Hold an item to wobble: drag to reorder, tap to select, trash to delete.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val arrange = rememberArrangeState(listState)
    val wobble = rememberWobble()
    val sel = rememberSelection(list.items.map { it.id }.toSet())
    val ordered = list.items.filterNot { it.checked } + list.items.filter { it.checked }
    val displayed = arrange.order?.let { o -> o.mapNotNull { id -> ordered.firstOrNull { it.id == id } } } ?: ordered
    Column {
        if (sel.active) SelectionBar(sel, "item", onDelete = { ids -> ids.forEach { store.deleteItem(list.id, it) } })
        else Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            items(displayed, key = { it.id }) { item ->
                val ctx = LocalContext.current
                val toggle = { open = if (item.id in open) open - item.id else open + item.id }
                ListItemCard(
                    item, expanded = item.id in open,
                    onToggle = toggle,
                    onEdit = { editing = item }, background = Nota.Shelf, tapHandled = true,
                    modifier = (if (arrange.dragging == item.id) Modifier else Modifier.animateItem()).arrangeable(
                        arrange, sel, item.id, enabled = true, wobble = wobble,
                        order = { displayed.map { it.id } },
                        // Ticked items stay below unticked ones.
                        canSwap = { a, b -> list.items.firstOrNull { it.id == a }?.checked == list.items.firstOrNull { it.id == b }?.checked },
                        onOpen = { item.tap(toggle) { editing = item } },
                        onDrop = { d -> if (d.moved) store.arrangeItems(list.id, d.order) },
                    ).padding(vertical = 2.dp),
                    leading = {
                        // The circle checks it off; the rest of the row opens it.
                        if (sel.active) SelectCircle(sel.has(item.id), Modifier.padding(end = 2.dp))
                        else SelectCircle(
                            item.checked,
                            Modifier.clip(CircleShape).clickable { store.updateItem(list.id, item.copy(checked = !item.checked)) },
                        )
                    },
                    title = {
                        Text(
                            item.text, style = MaterialTheme.typography.bodyLarge,
                            color = if (item.checked) Nota.Sub else Nota.Text,
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        )
                    },
                    extra = {
                        if (item.dueDay != null && !item.checked) {
                            val late = (item.dueAt() ?: Long.MAX_VALUE) < System.currentTimeMillis()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Due " + ListAlarms.describeDue(ctx, item), style = MaterialTheme.typography.bodySmall,
                                    color = if (late) Nota.Error else Nota.Sub,
                                )
                                if (item.remindBefore != null) Dot(size = 6.dp, color = Nota.Cyan, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    },
                )
            }
        }
    }
    editing?.let { item ->
        ItemDialog(list, item, onDismiss = { editing = null }, onSave = { store.updateItem(list.id, it); editing = null },
            onDelete = { store.deleteItem(list.id, item.id); editing = null })
    }
}

// ------------------------------------------------------------------------ status list

private val SECTION_COLORS get() = listOf(Nota.Mint, Nota.Cyan, Nota.Violet, Nota.Amber, Nota.Pink)

/** Dragging an item between categories: where it came from, where the finger is, and where it would land. */
private class CategoryDrag {
    var arranging by mutableStateOf(false)
    /** (category it was picked up from, or null for "No category"; item id) */
    var dragging by mutableStateOf<Pair<Int?, Int>?>(null)
    var pointer by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var grab = androidx.compose.ui.geometry.Offset.Zero
    var ghostWidth = 0
    /** (category, insert before this item or null = at the end) */
    var target by mutableStateOf<Pair<Int, Int?>?>(null)
    val sections = mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
    val items = mutableMapOf<Pair<Int?, Int>, androidx.compose.ui.geometry.Rect>()
    var body = androidx.compose.ui.geometry.Rect.Zero

    fun start(key: Pair<Int?, Int>, local: androidx.compose.ui.geometry.Offset) {
        val r = items[key] ?: return
        grab = local; pointer = r.topLeft + local; ghostWidth = r.width.toInt(); dragging = key
        aim()
    }

    fun aim() {
        val p = pointer
        val sec = sections.entries.firstOrNull { it.value.contains(p) }?.key
        target = sec?.let { s ->
            s to items.entries.filter { it.key.first == s && it.key != dragging }.sortedBy { it.value.top }
                .firstOrNull { it.value.center.y > p.y }?.key?.second
        }
    }
}

@Composable
private fun StatusBody(
    list: NotaList,
    store: ListsStore,
    onEditStatuses: () -> Unit,
    onAdd: (Int) -> Unit,
    onOpen: (ListItem) -> Unit,
) {
    if (list.statuses.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Add your categories first.", style = MaterialTheme.typography.bodyLarge, color = Nota.Sub)
            Spacer(Modifier.height(14.dp))
            SoftButton("Add categories", onClick = onEditStatuses, color = Nota.Mint, height = 46.dp)
        }
        return
    }
    val known = list.statuses.map { it.id }.toSet()
    val loose = list.items.filter { it.sections.none { id -> id in known } }
    var open by remember { mutableStateOf(setOf<String>()) }
    val drag = remember { CategoryDrag() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val wobble = rememberWobble()
    if (drag.arranging) BackHandler { drag.arranging = false }
    fun drop() {
        val (from, id) = drag.dragging ?: return
        drag.target?.let { (to, before) -> store.moveItem(list.id, id, from, to, before) }
        drag.dragging = null; drag.target = null
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { drag.body = it.boundsInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            if (drag.arranging) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Drag items between categories", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                TextButton(onClick = { drag.arranging = false }) { Text("Done", color = Nota.Mint) }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
                list.statuses.forEachIndexed { i, section ->
                    val color = SECTION_COLORS[i % SECTION_COLORS.size]
                    item(key = "s${section.id}") {
                        SectionBox(
                            section.name, color, list.items.filter { section.id in it.sections }, section.id, drag, wobble, listState,
                            open, { open = it }, onOpen, ::drop,
                        ) { onAdd(section.id) }
                    }
                }
                if (loose.isNotEmpty()) item(key = "none") {
                    SectionBox("No category", Nota.Outline, loose, null, drag, wobble, listState, open, { open = it }, onOpen, ::drop, onAdd = null)
                }
            }
        }
        // The picked-up item follows the finger.
        drag.dragging?.let { (_, id) ->
            val item = list.items.firstOrNull { it.id == id } ?: return@let
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pos = drag.pointer - drag.grab - drag.body.topLeft
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
                    .width(with(density) { drag.ghostWidth.toDp() })
                    .graphicsLayer { scaleX = 1.04f; scaleY = 1.04f; shadowElevation = 24f; rotationZ = -1.5f },
            ) {
                ListItemCard(
                    item, expanded = false, onToggle = {}, onEdit = {}, background = Nota.Raised, tapHandled = true,
                    leading = { Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(Nota.Mint)) },
                    title = { Text(item.text, style = MaterialTheme.typography.titleMedium, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** A thin rounded outline with the section's name sitting in a gap at the top centre, and + at the bottom. */
@Composable
private fun SectionBox(
    name: String, color: Color, items: List<ListItem>, sectionId: Int?, drag: CategoryDrag, wobble: androidx.compose.runtime.State<Float>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    open: Set<String>, setOpen: (Set<String>) -> Unit, onOpen: (ListItem) -> Unit, onDrop: () -> Unit, onAdd: (() -> Unit)?,
) {
    val key = sectionId?.let { "s$it" } ?: "none"
    val targeted = sectionId != null && drag.target?.first == sectionId
    if (sectionId != null) androidx.compose.runtime.DisposableEffect(sectionId) { onDispose { drag.sections.remove(sectionId) } }
    Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (sectionId != null) Modifier.onGloballyPositioned { drag.sections[sectionId] = it.boundsInRoot() } else Modifier)
                .border(if (targeted) 2.5.dp else 1.5.dp, color.copy(alpha = if (targeted) 1f else 0.75f), RoundedCornerShape(22.dp))
                .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item ->
                if (targeted && drag.target?.second == item.id) DropLine(color)
                // The same item can sit in several categories, so each spot opens on its own.
                val k = "$key/${item.id}"
                StatusItemRow(
                    item, color, expanded = k in open, sectionId = sectionId, drag = drag, wobble = wobble, listState = listState,
                    onToggle = { setOpen(if (k in open) open - k else open + k) }, onDrop = onDrop,
                ) { onOpen(item) }
            }
            if (targeted && drag.target?.second == null) DropLine(color)
            if (onAdd != null) Box(
                Modifier.padding(top = if (items.isEmpty()) 0.dp else 4.dp).size(38.dp).clip(CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = "Add to $name", tint = color, modifier = Modifier.size(24.dp)) }
        }
        // The name breaks the line: same colour as the card behind it.
        Text(
            name, color = color, fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-11).dp).background(Nota.Shelf).padding(horizontal = 10.dp),
        )
    }
}

/** Where the dragged item will land. */
@Composable
private fun DropLine(color: Color) {
    Box(Modifier.padding(vertical = 3.dp).fillMaxWidth(0.9f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
}

@Composable
private fun StatusItemRow(
    item: ListItem, color: Color, expanded: Boolean, sectionId: Int?, drag: CategoryDrag, wobble: androidx.compose.runtime.State<Float>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggle: () -> Unit, onDrop: () -> Unit, onEdit: () -> Unit,
) {
    val dragKey = sectionId to item.id
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tap by androidx.compose.runtime.rememberUpdatedState { item.tap(onToggle, onEdit) }
    val drop by androidx.compose.runtime.rememberUpdatedState(onDrop)
    androidx.compose.runtime.DisposableEffect(dragKey) { onDispose { drag.items.remove(dragKey) } }
    val picked = drag.dragging == dragKey
    ListItemCard(
        item, expanded = expanded, onToggle = onToggle, onEdit = onEdit, background = Nota.Raised, tapHandled = true,
        modifier = Modifier.padding(vertical = 4.dp)
            .onGloballyPositioned { drag.items[dragKey] = it.boundsInRoot() }
            .graphicsLayer {
                alpha = if (picked) 0.3f else 1f
                if (drag.arranging && !picked) rotationZ = wobble.value * (if (item.id % 2 == 0) 0.9f else -0.9f)
            }
            .pointerInput(dragKey) {
                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.follow(id: androidx.compose.ui.input.pointer.PointerId) {
                    androidx.compose.foundation.gestures.drag(id) { change ->
                        drag.pointer += change.positionChange()
                        change.consume()
                        drag.aim()
                        // Near the top or bottom: scroll along.
                        val y = drag.pointer.y
                        val step = when { y < drag.body.top + 120f -> -16f; y > drag.body.bottom - 120f -> 16f; else -> 0f }
                        if (step != 0f) listState.dispatchRawDelta(step)
                    }
                    drop()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    HoldGuard.claim(down.uptimeMillis)
                    if (drag.arranging) {
                        val first = androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                        if (first == null) {
                            val last = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (last != null && !last.pressed && !last.isConsumed) { last.consume(); tap() }
                            return@awaitEachGesture
                        }
                        drag.start(dragKey, first.position)
                        follow(first.id)
                        return@awaitEachGesture
                    }
                    var cancelled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        androidx.compose.foundation.gestures.waitForUpOrCancellation().also { if (it == null) cancelled = true }
                    }
                    if (cancelled) return@awaitEachGesture
                    if (up != null) { up.consume(); tap(); return@awaitEachGesture }
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    drag.arranging = true
                    drag.start(dragKey, down.position)
                    follow(down.id)
                }
            },
        notesRight = item.notesRight,
        leading = { Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(color)) },
        title = { Text(item.text, style = MaterialTheme.typography.titleMedium, color = Nota.Text, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis) },
    )
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
                TypeOption("Checklist", "Check things off, with optional due dates", type == ListType.CHECKLIST) { type = ListType.CHECKLIST }
                TypeOption("Categorized list", "Group items into your own categories", type == ListType.STATUS) { type = ListType.STATUS }
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

private val REMIND_OPTIONS: List<Int?> = listOf(null, 0, 15, 60, 24 * 60)

private fun remindLabel(minutes: Int?, hasTime: Boolean) = when (minutes) {
    null -> "No reminder"
    0 -> if (hasTime) "Remind at due time" else "Remind on the day (9 AM)"
    15 -> "Remind 15 min before"
    60 -> "Remind 1 hour before"
    else -> "Remind 1 day before"
}

/** Add or edit an item. Sectioned lists get a description and sections; checklists a due date and reminder. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ItemDialog(list: NotaList, item: ListItem, onDismiss: () -> Unit, onSave: (ListItem) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(item.text) }
    var description by remember { mutableStateOf(item.description) }
    var notesRight by remember { mutableStateOf(item.notesRight) }
    var imagePath by remember { mutableStateOf(item.imagePath) }
    var imageStyle by remember { mutableStateOf(item.imageStyle) }
    var sections by remember { mutableStateOf(item.sections) }
    var dueDay by remember { mutableStateOf(item.dueDay) }
    var dueMinute by remember { mutableStateOf(item.dueMinute) }
    var remind by remember { mutableStateOf(item.remindBefore) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var remindMenu by remember { mutableStateOf(false) }
    val isNew = item.id == 0
    val sectioned = list.type == ListType.STATUS
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
                if (sectioned) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("NOTES POSITION", style = Nota.Label, color = Nota.Sub, modifier = Modifier.weight(1f))
                        LayoutIcon(right = false, selected = !notesRight) { notesRight = false }
                        Spacer(Modifier.width(8.dp))
                        LayoutIcon(right = true, selected = notesRight) { notesRight = true }
                    }
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Notes") },
                    minLines = 2, maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                ItemPictureSection(imagePath, imageStyle, query = text, onPath = { imagePath = it }, onStyle = { imageStyle = it })
                if (sectioned) {
                    Text("CATEGORIES", style = Nota.Label, color = Nota.Sub, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.statuses.forEach { s ->
                            DotChip(s.name, s.id in sections) { sections = if (s.id in sections) sections - s.id else sections + s.id }
                        }
                    }
                } else {
                    // Due date, optional time, and a reminder once there's a date.
                    val probe = ListItem(0, "", dueDay = dueDay, dueMinute = dueMinute)
                    DialogRow(
                        if (dueDay == null) "Add due date" else "Due " + ListAlarms.describeDue(ctx, probe.copy(dueMinute = null)),
                        active = dueDay != null, onClick = { pickDate = true },
                        onClear = if (dueDay != null) ({ dueDay = null; dueMinute = null; remind = null }) else null,
                    )
                    if (dueDay != null) {
                        DialogRow(
                            dueMinute?.let { "At " + timeText(ctx, it) } ?: "Add time",
                            active = dueMinute != null, onClick = { pickTime = true },
                            onClear = if (dueMinute != null) ({ dueMinute = null }) else null,
                        )
                        Box {
                            DialogRow(remindLabel(remind, dueMinute != null), active = remind != null, onClick = { remindMenu = true }, onClear = null)
                            DropdownMenu(remindMenu, onDismissRequest = { remindMenu = false }, containerColor = Nota.Raised) {
                                REMIND_OPTIONS.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(remindLabel(m, dueMinute != null)) },
                                        onClick = { remind = m; remindMenu = false },
                                    )
                                }
                            }
                        }
                    }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete item", color = Nota.Error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            text = text.trim(), description = description.trim(), notesRight = notesRight, statusId = null,
                            statusIds = list.statuses.map { it.id }.filter { it in sections },
                            dueDay = dueDay, dueMinute = dueMinute, remindBefore = if (dueDay == null) null else remind,
                            imagePath = imagePath, imageStyle = imageStyle,
                        ),
                    )
                },
                enabled = text.isNotBlank(),
            ) { Text(if (isNew) "Add" else "Save", color = if (text.isNotBlank()) Nota.Mint else Nota.Outline) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dueDay ?: java.time.LocalDate.now().toEpochDay()) * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDay = it / 86_400_000L }
                    pickDate = false
                }) { Text("OK", color = Nota.Mint) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = Nota.Sub) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val m = dueMinute ?: (9 * 60)
        val state = rememberTimePickerState(m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Nota.Shelf,
            confirmButton = {
                TextButton(onClick = { dueMinute = state.hour * 60 + state.minute; pickTime = false }) { Text("OK", color = Nota.Mint) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel", color = Nota.Sub) } },
            text = { TimePicker(state) },
        )
    }
}

private fun timeText(ctx: android.content.Context, minutes: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minutes / 60); set(java.util.Calendar.MINUTE, minutes % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(ctx).format(cal.time)
}

/** Notes position choice: a box with its bottom (below the name) or right side (beside it) filled in. */
@Composable
private fun LayoutIcon(right: Boolean, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Nota.Mint else Nota.Sub
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Nota.Raised else Color.Transparent)
            .border(1.5.dp, if (selected) Nota.Mint else Nota.Outline, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            val fill = if (right) androidx.compose.ui.geometry.Rect(size.width * 0.55f, 0f, size.width, size.height)
            else androidx.compose.ui.geometry.Rect(0f, size.height * 0.55f, size.width, size.height)
            clipRect(fill.left, fill.top, fill.right, fill.bottom) { drawRoundRect(tint, cornerRadius = r) }
            drawRoundRect(
                tint, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), cornerRadius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
        }
    }
}

/** A tappable grey row in the item dialog, with an optional ✕ to clear it. */
@Composable
private fun DialogRow(text: String, active: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nota.Raised).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (active) Nota.Text else Nota.Mint, modifier = Modifier.weight(1f))
        if (onClear != null) Icon(
            Icons.Filled.Close, contentDescription = "Clear", tint = Nota.Sub,
            modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClear),
        )
    }
}

/** Add, rename, reorder and remove a sectioned list's sections. Changes apply as you go. */
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
        title = { Text("Categories") },
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
                        placeholder = { Text("New category", color = Nota.Sub) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        shape = RoundedCornerShape(14.dp), colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape)
                            .background(if (draft.isBlank()) Nota.Raised else Nota.Mint).clickable { add() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add category", tint = if (draft.isBlank()) Nota.Sub else Nota.Ink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Nota.Mint) } },
    )
}
