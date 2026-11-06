package dev.ray.reminders.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class Note(
    val id: Int,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isBlank get() = title.isBlank() && body.isBlank()
}

/** Plain notes, kept in notes.json. (Passwords live separately, encrypted, in [Vault].) */
class NotesStore private constructor(private val file: File) {
    private val lock = Any()
    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun newId() = (_notes.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(note: Note) = mutate { l ->
        if (l.any { it.id == note.id }) l.map { if (it.id == note.id) note else it } else l + note
    }

    fun delete(ids: Set<Int>) = mutate { l -> l.filterNot { it.id in ids } }

    private fun mutate(change: (List<Note>) -> List<Note>) = synchronized(lock) {
        val updated = change(_notes.value)
        _notes.value = updated
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ReminderStore.json.encodeToString(updated))
        tmp.renameTo(file)
    }

    private fun load(): List<Note> = try {
        if (file.exists()) ReminderStore.json.decodeFromString<List<Note>>(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        @Volatile private var instance: NotesStore? = null
        fun get(context: Context): NotesStore = instance ?: synchronized(this) {
            instance ?: NotesStore(File(context.applicationContext.filesDir, "notes.json")).also { instance = it }
        }
    }
}
