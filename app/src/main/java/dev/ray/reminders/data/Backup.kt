package dev.ray.reminders.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything in Nota as one file, for moving to another phone: reminders, alarms, lists, notes, pictures,
 * sounds, themes and (decrypted, then re-locked with the PIN) the password vault.
 *
 * File: "NOTA" + version byte + locked flag, then either a plain zip or salt (16) + iv (12) + AES-GCM(zip),
 * keyed from the vault PIN. When there's a vault, the file is always locked with its PIN.
 */
object Backup {
    private val MAGIC = "NOTA".toByteArray()
    private const val VERSION: Byte = 1
    private const val VAULT_ENTRY = "vault.json"
    private const val PREFS_ENTRY = "prefs.json"
    private const val FILES = "files/"
    /** Settings worth carrying over (not phone-specific ones like widget placement or the vault's keys). */
    private val PREFS = listOf("theme", "sounds", "timer")
    private val SKIP_FILES = setOf("passwords.vault", "last_crash.txt")
    private const val PENDING = "restore_pending"

    @Serializable
    private data class Pref(val type: String, val value: String, val set: List<String>? = null)

    class WrongPin : Exception()
    class NotABackup : Exception()

    fun isLocked(bytes: ByteArray) = bytes.size > 6 && bytes.copyOfRange(0, 4).contentEquals(MAGIC) && bytes[5] == 1.toByte()

    /** Builds the file. [vault] is the unlocked vault (null if there's none) and [pin] its PIN. Run off the main thread. */
    fun export(ctx: Context, vault: VaultData?, pin: String?): ByteArray {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            val root = ctx.filesDir
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(root).path
                if (rel in SKIP_FILES || rel.endsWith(".tmp")) return@forEach
                z.putNextEntry(ZipEntry(FILES + rel)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
            val prefs = PREFS.associateWith { name ->
                ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
                    k to when (v) {
                        is String -> Pref("s", v)
                        is Int -> Pref("i", v.toString())
                        is Long -> Pref("l", v.toString())
                        is Boolean -> Pref("b", v.toString())
                        is Float -> Pref("f", v.toString())
                        is Set<*> -> Pref("set", "", v.map { it.toString() })
                        else -> return@mapNotNull null
                    }
                }.toMap()
            }
            z.putNextEntry(ZipEntry(PREFS_ENTRY)); z.write(ReminderStore.json.encodeToString(prefs).toByteArray()); z.closeEntry()
            if (vault != null) {
                z.putNextEntry(ZipEntry(VAULT_ENTRY)); z.write(ReminderStore.json.encodeToString(vault).toByteArray()); z.closeEntry()
            }
        }
        val body = zip.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(MAGIC); out.write(byteArrayOf(VERSION, if (pin != null) 1 else 0))
        if (pin == null) out.write(body) else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(pin, salt), GCMParameterSpec(128, iv)) }
            out.write(salt); out.write(iv); out.write(c.doFinal(body))
        }
        return out.toByteArray()
    }

    /**
     * Replaces everything in Nota with the file's contents. Throws [WrongPin] / [NotABackup].
     * Afterwards the app must restart (see [restartApp]) so every screen reloads. Run off the main thread.
     */
    fun import(ctx: Context, bytes: ByteArray, pin: String?) {
        if (bytes.size < 6 || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) throw NotABackup()
        val locked = bytes[5] == 1.toByte()
        val body = if (!locked) bytes.copyOfRange(6, bytes.size) else {
            if (pin == null || bytes.size < 6 + 28) throw WrongPin()
            val salt = bytes.copyOfRange(6, 22)
            val iv = bytes.copyOfRange(22, 34)
            runCatching {
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(pin, salt), GCMParameterSpec(128, iv)) }
                    .doFinal(bytes, 34, bytes.size - 34)
            }.getOrElse { throw WrongPin() }
        }

        // Unpack fully before touching anything, so a broken file changes nothing.
        val files = mutableMapOf<String, ByteArray>()
        var prefsJson: String? = null
        var vaultJson: String? = null
        ZipInputStream(ByteArrayInputStream(body)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val data = z.readBytes()
                when {
                    e.name == PREFS_ENTRY -> prefsJson = String(data)
                    e.name == VAULT_ENTRY -> vaultJson = String(data)
                    e.name.startsWith(FILES) && !e.name.contains("..") -> files[e.name.removePrefix(FILES)] = data
                }
            }
        }
        val vault = vaultJson?.let { ReminderStore.json.decodeFromString<VaultData>(it) }

        // Stop everything the old data had scheduled.
        dev.ray.reminders.alarm.ReminderEngine.unscheduleAll(ctx)

        val root = ctx.filesDir
        root.walkBottomUp().filter { it != root }.forEach { f ->
            val rel = f.relativeTo(root).path
            if (rel !in SKIP_FILES) f.delete()
        }
        files.forEach { (rel, data) -> File(root, rel).apply { parentFile?.mkdirs() }.writeBytes(data) }

        prefsJson?.let { ReminderStore.json.decodeFromString<Map<String, Map<String, Pref>>>(it) }?.forEach { (name, values) ->
            if (name !in PREFS) return@forEach
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (k, p) ->
                when (p.type) {
                    "s" -> ed.putString(k, p.value)
                    "i" -> ed.putInt(k, p.value.toInt())
                    "l" -> ed.putLong(k, p.value.toLong())
                    "b" -> ed.putBoolean(k, p.value.toBoolean())
                    "f" -> ed.putFloat(k, p.value.toFloat())
                    "set" -> ed.putStringSet(k, p.set.orEmpty().toSet())
                }
            }
            ed.commit()
        }

        // Passwords: a fresh vault on this phone, locked with the same PIN.
        Vault.reset(ctx)
        if (vault != null && pin != null) {
            Vault.setUp(ctx, pin)
            Vault.restore(ctx, vault)
            Vault.lock()
        }
        ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().putBoolean(PENDING, true).commit()
    }

    /** After an import: re-arm alarms and reminders from the new data (called when Nota starts). */
    fun finishRestore(ctx: Context) {
        val p = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
        if (!p.getBoolean(PENDING, false)) return
        p.edit().putBoolean(PENDING, false).commit()
        dev.ray.reminders.alarm.ReminderEngine.restoreAll(ctx)
        runCatching { dev.ray.reminders.alarm.ListAlarms.restore(ctx) }
    }

    /** Restarts Nota so every store reloads from the imported files. */
    fun restartApp(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        ctx.startActivity(android.content.Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    private fun key(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
}
