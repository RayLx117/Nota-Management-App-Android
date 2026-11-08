package dev.ray.reminders.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Sounds alarms and timers can ring with.
 * Ids: "system" = the phone's alarm tone, "file:<name>" = audio uploaded into Nota,
 * "uri:<content uri>" = one of the phone's own sounds.
 */
@Serializable
data class Sound(val id: String, val name: String)

object Sounds {
    const val SYSTEM = "system"
    private val SYSTEM_SOUND = Sound(SYSTEM, "Phone alarm")

    private var flow: MutableStateFlow<List<Sound>>? = null
    private var defaultFlow: MutableStateFlow<String>? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("sounds", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "sounds").apply { mkdirs() }

    /** Every sound, the phone alarm first. */
    fun all(ctx: Context): StateFlow<List<Sound>> = flow ?: MutableStateFlow(load(ctx)).also { flow = it }

    private fun load(ctx: Context): List<Sound> {
        val saved = prefs(ctx).getString("list", null)?.let {
            runCatching { ReminderStore.json.decodeFromString<List<Sound>>(it) }.getOrNull()
        } ?: emptyList()
        return listOf(SYSTEM_SOUND) + saved.filter { it.id != SYSTEM }
    }

    private fun save(ctx: Context, list: List<Sound>) {
        prefs(ctx).edit().putString("list", ReminderStore.json.encodeToString(list.filter { it.id != SYSTEM })).apply()
        (all(ctx) as MutableStateFlow).value = listOf(SYSTEM_SOUND) + list.filter { it.id != SYSTEM }
    }

    /** The sound new alarms and timers start with (Settings > Alarm sounds). */
    fun default(ctx: Context): StateFlow<String> =
        defaultFlow ?: MutableStateFlow(prefs(ctx).getString("default", SYSTEM) ?: SYSTEM).also { defaultFlow = it }

    fun setDefault(ctx: Context, id: String) {
        prefs(ctx).edit().putString("default", id).apply()
        (default(ctx) as MutableStateFlow).value = id
    }

    /** A sound id that still exists: missing ones fall back to the default, then the phone alarm. */
    fun resolve(ctx: Context, id: String?): String {
        val ids = all(ctx).value.map { it.id }
        return when {
            id != null && id in ids -> id
            default(ctx).value in ids -> default(ctx).value
            else -> SYSTEM
        }
    }

    fun name(ctx: Context, id: String?): String {
        val r = resolve(ctx, id)
        return all(ctx).value.firstOrNull { it.id == r }?.name ?: SYSTEM_SOUND.name
    }

    /** Copies a picked audio file into Nota so it keeps working even if the original is moved. */
    fun upload(ctx: Context, uri: Uri): Sound? = runCatching {
        val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Sound"
        val base = display.substringBeforeLast('.').ifBlank { "Sound" }
        val ext = display.substringAfterLast('.', "").takeIf { it.length in 1..5 } ?: "audio"
        val file = File(dir(ctx), "${System.currentTimeMillis()}.$ext")
        ctx.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Sound("file:${file.name}", base).also { save(ctx, all(ctx).value + it) }
    }.getOrNull()

    /** Adds one of the phone's own sounds (from the system sound picker). */
    fun addPhoneSound(ctx: Context, uri: Uri): Sound {
        val id = "uri:$uri"
        all(ctx).value.firstOrNull { it.id == id }?.let { return it }
        val name = runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull() ?: "Phone sound"
        return Sound(id, name).also { save(ctx, all(ctx).value + it) }
    }

    fun rename(ctx: Context, id: String, name: String) =
        save(ctx, all(ctx).value.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })

    fun remove(ctx: Context, id: String) {
        if (id == SYSTEM) return
        if (id.startsWith("file:")) File(dir(ctx), id.removePrefix("file:")).delete()
        save(ctx, all(ctx).value.filter { it.id != id })
        if (default(ctx).value == id) setDefault(ctx, SYSTEM)
    }

    /** What MediaPlayer should open for a sound id. */
    fun uri(ctx: Context, id: String?): Uri {
        val r = resolve(ctx, id)
        return when {
            r.startsWith("file:") -> Uri.fromFile(File(dir(ctx), r.removePrefix("file:")))
            r.startsWith("uri:") -> Uri.parse(r.removePrefix("uri:"))
            else -> RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
    }
}
