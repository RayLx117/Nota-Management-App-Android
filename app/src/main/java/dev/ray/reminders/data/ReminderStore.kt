package dev.ray.reminders.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Reminders are kept in a small JSON file and exposed as a StateFlow for the UI. */
class ReminderStore private constructor(private val file: File, private val onChange: () -> Unit = {}) {

    private val lock = Any()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    fun all(): List<Reminder> = _items.value
    fun get(id: Int): Reminder? = _items.value.firstOrNull { it.id == id }

    fun newId(): Int = (_items.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(reminder: Reminder) = mutate { list ->
        if (list.any { it.id == reminder.id }) list.map { if (it.id == reminder.id) reminder else it }
        else list + reminder
    }

    /** Applies [change] to one reminder atomically and returns the result. */
    fun modify(id: Int, change: (Reminder) -> Reminder): Reminder? {
        var result: Reminder? = null
        mutate { list ->
            list.map { if (it.id == id) change(it).also { r -> result = r } else it }
        }
        return result
    }

    fun delete(id: Int) = mutate { list -> list.filterNot { it.id == id } }

    private fun mutate(change: (List<Reminder>) -> List<Reminder>) = synchronized(lock) {
        val updated = change(_items.value)
        _items.value = updated
        save(updated)
        runCatching(onChange)
    }

    private fun load(): List<Reminder> = try {
        if (file.exists()) json.decodeFromString<List<Reminder>>(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Reminder>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(file)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        @Volatile
        private var instance: ReminderStore? = null

        fun get(context: Context): ReminderStore = instance ?: synchronized(this) {
            val app = context.applicationContext
            // Keep the "Up next" widget current whenever anything changes.
            instance ?: ReminderStore(File(app.filesDir, "reminders.json")) { dev.ray.reminders.widget.NextWidget.refresh(app) }
                .also { instance = it }
        }
    }
}
