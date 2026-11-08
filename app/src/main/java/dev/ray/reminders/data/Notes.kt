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
    val folderId: Int? = null,
    /** Locked by the user (needs the PIN/fingerprint to open). */
    val locked: Boolean = false,
    /** True when the title/body live encrypted in the [Vault] and this entry is only a placeholder. */
    val encrypted: Boolean = false,
    /** Position when arranged by hand (lower = higher up). New notes go on top. */
    val order: Int = 0,
) {
    val isBlank get() = title.isBlank() && body.isBlank()
}

@Serializable
data class NoteFolder(val id: Int, val name: String, val locked: Boolean = false)

/** Plain notes, kept in notes.json. (Passwords live separately, encrypted, in [Vault].) */
class NotesStore private constructor(private val file: File) {
    private val lock = Any()
    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val folderFile = File(file.parentFile, "note_folders.json")
    private val _folders = MutableStateFlow(
        runCatching { ReminderStore.json.decodeFromString<List<NoteFolder>>(folderFile.readText()) }.getOrDefault(emptyList()),
    )
    val folders: StateFlow<List<NoteFolder>> = _folders.asStateFlow()

    fun folder(id: Int?) = id?.let { f -> _folders.value.firstOrNull { it.id == f } }

    /** A note needs the PIN if it's locked itself or sits in a locked folder. */
    fun isProtected(n: Note) = n.locked || folder(n.folderId)?.locked == true

    fun addFolder(name: String): Int {
        val id = (_folders.value.maxOfOrNull { it.id } ?: 0) + 1
        setFolders(_folders.value + NoteFolder(id, name))
        return id
    }

    fun updateFolder(f: NoteFolder) = setFolders(_folders.value.map { if (it.id == f.id) f else it })

    /** Removes the folder; its notes stay, just without a folder. */
    fun deleteFolder(id: Int) {
        setFolders(_folders.value.filterNot { it.id == id })
        mutate { l -> l.map { if (it.folderId == id) it.copy(folderId = null) else it } }
    }

    private fun setFolders(list: List<NoteFolder>) = synchronized(lock) {
        _folders.value = list
        folderFile.writeText(ReminderStore.json.encodeToString(list))
    }

    /**
     * Moves note text in or out of the vault so it matches its lock state, and drops leftovers.
     * Only works while the vault is unlocked.
     */
    fun reconcile(ctx: Context) {
        if (!Vault.isUnlocked()) return
        for (n in _notes.value) {
            val prot = isProtected(n)
            if (prot && !n.encrypted) {
                Vault.putSecretNote(ctx, SecretNote(n.id, n.title, n.body))
                upsert(n.copy(title = "", body = "", encrypted = true))
            } else if (!prot && n.encrypted) {
                val s = Vault.secretNote(n.id)
                upsert(n.copy(title = s?.title.orEmpty(), body = s?.body.orEmpty(), encrypted = false))
                Vault.removeSecretNotes(ctx, setOf(n.id))
            }
        }
        val live = _notes.value.filter { it.encrypted }.map { it.id }.toSet()
        val orphans = Vault.data.value?.notes?.map { it.id }?.filterNot { it in live }?.toSet().orEmpty()
        if (orphans.isNotEmpty()) Vault.removeSecretNotes(ctx, orphans)
    }

    fun newId() = (_notes.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(note: Note) = mutate { l ->
        if (l.any { it.id == note.id }) l.map { if (it.id == note.id) note else it }
        else l + note.copy(order = (l.minOfOrNull { it.order } ?: 0) - 1)
    }

    /** Saves a hand-arranged order: [ids] top to bottom. */
    fun arrange(ids: List<Int>) = mutate { l ->
        val pos = ids.withIndex().associate { it.value to it.index }
        l.map { n -> pos[n.id]?.let { n.copy(order = it) } ?: n }
    }

    /** Moves notes into a folder (null = out of any folder). Call [reconcile] after, for locked folders. */
    fun move(ids: Set<Int>, folderId: Int?) = mutate { l ->
        val top = (l.filter { it.folderId == folderId }.minOfOrNull { it.order } ?: 0) - 1
        l.map { if (it.id in ids && it.folderId != folderId) it.copy(folderId = folderId, order = top) else it }
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
