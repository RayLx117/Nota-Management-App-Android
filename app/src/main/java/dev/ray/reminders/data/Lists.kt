package dev.ray.reminders.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
enum class ListType { CHECKLIST, STATUS }

/** A user-made section of a sectioned list (stored as STATUS for older data); shown in list order. */
@Serializable
data class Status(val id: Int, val name: String)

/** How a list item's picture shows: fading in on the right, a banner on top, or only when opened. */
@Serializable
enum class ListImageStyle { FADE, BANNER, EXPAND }

@Serializable
data class ListItem(
    val id: Int,
    val text: String,
    val description: String = "",
    /** Sectioned lists: show the notes to the right of the name instead of below it. */
    val notesRight: Boolean = false,
    val checked: Boolean = false,
    /** Legacy single section; read through [sections]. */
    val statusId: Int? = null,
    /** Every section this item is in (an item can be in several). */
    val statusIds: List<Int> = emptyList(),
    /** Checklist due date (epoch day) and optional time (minutes after midnight). */
    val dueDay: Long? = null,
    val dueMinute: Int? = null,
    /** Notify this many minutes before it's due; null = no reminder. */
    val remindBefore: Int? = null,
    /** Optional picture (a file in app storage). */
    val imagePath: String? = null,
    val imageStyle: ListImageStyle = ListImageStyle.FADE,
) {
    val sections: Set<Int> get() = (statusIds + listOfNotNull(statusId)).toSet()

    /** When it's due, for reminders: the set time, or 9 AM on the day if no time was set. */
    fun dueAt(): Long? = dueDay?.let { day ->
        java.time.LocalDate.ofEpochDay(day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() +
            (dueMinute ?: (9 * 60)) * 60_000L
    }

    fun remindAt(): Long? = if (remindBefore == null) null else dueAt()?.minus(remindBefore * 60_000L)
}

@Serializable
data class NotaList(
    val id: Int,
    val name: String,
    val type: ListType,
    val statuses: List<Status> = emptyList(),
    val items: List<ListItem> = emptyList(),
) {
    fun nextItemId() = (items.maxOfOrNull { it.id } ?: 0) + 1
    fun nextStatusId() = (statuses.maxOfOrNull { it.id } ?: 0) + 1
}

/** All lists in one JSON file, exposed as a StateFlow. */
class ListsStore private constructor(private val ctx: Context, private val file: File) {
    private val lock = Any()
    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<List<NotaList>> = _lists.asStateFlow()

    fun create(name: String, type: ListType): Int {
        val id = (_lists.value.maxOfOrNull { it.id } ?: 0) + 1
        mutate { it + NotaList(id, name, type) }
        return id
    }

    fun delete(id: Int) = mutate { l ->
        l.firstOrNull { it.id == id }?.items?.forEach { i -> i.imagePath?.let(Images::delete) }
        l.filterNot { it.id == id }
    }

    fun update(id: Int, change: (NotaList) -> NotaList) = mutate { l -> l.map { if (it.id == id) change(it) else it } }

    fun addItem(listId: Int, item: ListItem) = update(listId) {
        it.copy(items = it.items + item.copy(id = it.nextItemId()))
    }

    fun item(listId: Int, itemId: Int) = _lists.value.firstOrNull { it.id == listId }?.items?.firstOrNull { it.id == itemId }

    fun updateItem(listId: Int, item: ListItem) = update(listId) { l ->
        l.items.firstOrNull { it.id == item.id }?.imagePath?.let { old -> if (old != item.imagePath) Images.delete(old) }
        l.copy(items = l.items.map { if (it.id == item.id) item else it })
    }

    fun deleteItem(listId: Int, itemId: Int) = update(listId) { l ->
        l.items.firstOrNull { it.id == itemId }?.imagePath?.let(Images::delete)
        l.copy(items = l.items.filterNot { it.id == itemId })
    }

    fun clearChecked(listId: Int) = update(listId) { l ->
        l.items.filter { it.checked }.forEach { i -> i.imagePath?.let(Images::delete) }
        l.copy(items = l.items.filterNot { it.checked })
    }

    private fun mutate(change: (List<NotaList>) -> List<NotaList>) = synchronized(lock) {
        val before = _lists.value
        val updated = change(before)
        _lists.value = updated
        dev.ray.reminders.alarm.ListAlarms.sync(ctx, before, updated)
        dev.ray.reminders.widget.ListWidgets.refreshAll(ctx)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ReminderStore.json.encodeToString(updated))
        tmp.renameTo(file)
    }

    private fun load(): List<NotaList> = try {
        if (file.exists()) ReminderStore.json.decodeFromString<List<NotaList>>(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        @Volatile private var instance: ListsStore? = null
        fun get(context: Context): ListsStore = instance ?: synchronized(this) {
            instance ?: ListsStore(context.applicationContext, File(context.applicationContext.filesDir, "lists.json")).also { instance = it }
        }
    }
}
