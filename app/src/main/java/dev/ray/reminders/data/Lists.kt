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

/** A user-made status; a status list shows one section per status, in list order. */
@Serializable
data class Status(val id: Int, val name: String)

@Serializable
data class ListItem(
    val id: Int,
    val text: String,
    val description: String = "",
    val checked: Boolean = false,
    val statusId: Int? = null,
)

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
class ListsStore private constructor(private val file: File) {
    private val lock = Any()
    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<List<NotaList>> = _lists.asStateFlow()

    fun create(name: String, type: ListType): Int {
        val id = (_lists.value.maxOfOrNull { it.id } ?: 0) + 1
        mutate { it + NotaList(id, name, type) }
        return id
    }

    fun delete(id: Int) = mutate { l -> l.filterNot { it.id == id } }

    fun update(id: Int, change: (NotaList) -> NotaList) = mutate { l -> l.map { if (it.id == id) change(it) else it } }

    fun addItem(listId: Int, text: String, description: String = "", statusId: Int? = null) = update(listId) {
        it.copy(items = it.items + ListItem(it.nextItemId(), text, description, statusId = statusId))
    }

    fun updateItem(listId: Int, item: ListItem) = update(listId) { l ->
        l.copy(items = l.items.map { if (it.id == item.id) item else it })
    }

    fun deleteItem(listId: Int, itemId: Int) = update(listId) { l -> l.copy(items = l.items.filterNot { it.id == itemId }) }

    fun clearChecked(listId: Int) = update(listId) { l -> l.copy(items = l.items.filterNot { it.checked }) }

    private fun mutate(change: (List<NotaList>) -> List<NotaList>) = synchronized(lock) {
        val updated = change(_lists.value)
        _lists.value = updated
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
            instance ?: ListsStore(File(context.applicationContext.filesDir, "lists.json")).also { instance = it }
        }
    }
}
