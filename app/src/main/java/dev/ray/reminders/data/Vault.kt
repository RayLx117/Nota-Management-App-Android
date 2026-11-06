package dev.ray.reminders.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class VaultEntry(
    val id: Int,
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Encrypted password storage.
 *
 * Entries are encrypted (AES-GCM) with a random vault key. That key is stored two ways:
 *  - wrapped with a key derived from the 4-digit PIN (PBKDF2), and that blob is wrapped again with a
 *    hardware-backed Keystore key, so the file is useless off this phone;
 *  - optionally wrapped with a Keystore key that only unlocks after a strong biometric (fingerprint).
 * Wrong PINs are rate-limited. The vault key only lives in memory while unlocked.
 */
object Vault {
    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val untilMillis: Long) : PinResult
    }

    private const val DEVICE_KEY = "nota_vault_device"
    private const val BIO_KEY = "nota_vault_bio"
    private const val FREE_TRIES = 5

    private var key: SecretKey? = null
    private val _entries = MutableStateFlow<List<VaultEntry>?>(null)
    /** Decrypted entries while unlocked; null while locked. */
    val entries: StateFlow<List<VaultEntry>?> = _entries.asStateFlow()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "passwords.vault")

    fun isSetUp(ctx: Context) = prefs(ctx).contains("pin_wrapped")
    fun isUnlocked() = key != null
    fun biometricOn(ctx: Context) = prefs(ctx).contains("bio_wrapped")

    fun lock() {
        key = null
        _entries.value = null
    }

    // ------------------------------------------------------------------ PIN

    /** First-time setup: makes a fresh vault key protected by [pin], and unlocks. */
    fun setUp(ctx: Context, pin: String) {
        val k: SecretKey = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
        storePin(ctx, k, pin)
        key = k
        save(ctx, emptyList())
    }

    fun changePin(ctx: Context, pin: String) {
        storePin(ctx, key ?: return, pin)
    }

    private fun storePin(ctx: Context, k: SecretKey, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val inner = seal(pinKey(pin, salt), k.encoded)
        val outer = seal(deviceKey(), inner)
        prefs(ctx).edit().putString("pin_salt", b64(salt)).putString("pin_wrapped", b64(outer))
            .putInt("fails", 0).putLong("locked_until", 0).apply()
    }

    fun lockedUntil(ctx: Context): Long = prefs(ctx).getLong("locked_until", 0)

    /** Slow on purpose (key stretching): call off the main thread. */
    fun unlockWithPin(ctx: Context, pin: String): PinResult {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (lockedUntil(ctx) > now) return PinResult.LockedOut(lockedUntil(ctx))
        val salt = unb64(p.getString("pin_salt", null) ?: return PinResult.Wrong(0))
        val outer = unb64(p.getString("pin_wrapped", null) ?: return PinResult.Wrong(0))
        val raw = runCatching { open(pinKey(pin, salt), open(deviceKey(), outer)) }.getOrNull()
        if (raw == null) {
            val fails = p.getInt("fails", 0) + 1
            p.edit().putInt("fails", fails).apply()
            if (fails >= FREE_TRIES) {
                // 30 s, then doubling each further miss, up to an hour.
                val wait = (30_000L shl (fails - FREE_TRIES).coerceAtMost(7)).coerceAtMost(3_600_000L)
                p.edit().putLong("locked_until", now + wait).apply()
                return PinResult.LockedOut(now + wait)
            }
            return PinResult.Wrong(FREE_TRIES - fails)
        }
        p.edit().putInt("fails", 0).putLong("locked_until", 0).apply()
        openWith(ctx, SecretKeySpec(raw, "AES"))
        return PinResult.Ok
    }

    // ------------------------------------------------------------------ biometrics

    /** A cipher ready to wrap the vault key; hand it to the fingerprint prompt, then call [finishBiometricSetup]. */
    fun biometricSetupCipher(): Cipher {
        val ks = keyStore()
        ks.deleteEntry(BIO_KEY)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(BIO_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, gen.generateKey()) }
    }

    fun finishBiometricSetup(ctx: Context, cipher: Cipher) {
        val k = key ?: return
        val blob = cipher.iv + cipher.doFinal(k.encoded)
        prefs(ctx).edit().putString("bio_wrapped", b64(blob)).apply()
    }

    fun turnOffBiometric(ctx: Context) {
        prefs(ctx).edit().remove("bio_wrapped").apply()
        runCatching { keyStore().deleteEntry(BIO_KEY) }
    }

    /**
     * A cipher ready to unwrap the vault key after a fingerprint, or null if fingerprint unlock is off
     * or was invalidated (e.g. a new fingerprint was added; the PIN still works and can re-enable it).
     */
    fun biometricUnlockCipher(ctx: Context): Cipher? {
        val blob = unb64(prefs(ctx).getString("bio_wrapped", null) ?: return null)
        return try {
            val k = keyStore().getKey(BIO_KEY, null) as? SecretKey ?: run { turnOffBiometric(ctx); return null }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob, 0, 12)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            turnOffBiometric(ctx)
            null
        }
    }

    fun finishBiometricUnlock(ctx: Context, cipher: Cipher): Boolean {
        val blob = unb64(prefs(ctx).getString("bio_wrapped", null) ?: return false)
        val raw = runCatching { cipher.doFinal(blob, 12, blob.size - 12) }.getOrNull() ?: return false
        prefs(ctx).edit().putInt("fails", 0).putLong("locked_until", 0).apply()
        openWith(ctx, SecretKeySpec(raw, "AES"))
        return true
    }

    // ------------------------------------------------------------------ entries

    fun upsert(ctx: Context, entry: VaultEntry) {
        val list = _entries.value ?: return
        save(ctx, if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry)
    }

    fun delete(ctx: Context, ids: Set<Int>) {
        save(ctx, (_entries.value ?: return).filterNot { it.id in ids })
    }

    fun newId() = ((_entries.value ?: emptyList()).maxOfOrNull { it.id } ?: 0) + 1

    /** Forgot the PIN: wipe everything and start over. */
    fun reset(ctx: Context) {
        lock()
        file(ctx).delete()
        prefs(ctx).edit().clear().apply()
        runCatching { keyStore().deleteEntry(BIO_KEY) }
    }

    private fun openWith(ctx: Context, k: SecretKey) {
        val f = file(ctx)
        val list = if (f.exists()) {
            ReminderStore.json.decodeFromString<List<VaultEntry>>(String(open(k, f.readBytes()), Charsets.UTF_8))
        } else emptyList()
        key = k
        _entries.value = list
    }

    private fun save(ctx: Context, list: List<VaultEntry>) {
        val k = key ?: return
        val f = file(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(seal(k, ReminderStore.json.encodeToString(list).toByteArray(Charsets.UTF_8)))
        tmp.renameTo(f)
        _entries.value = list
    }

    // ------------------------------------------------------------------ crypto helpers

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Hardware-backed key that never leaves this phone (no fingerprint needed). */
    private fun deviceKey(): SecretKey {
        (keyStore().getKey(DEVICE_KEY, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(DEVICE_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun pinKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** iv (12 bytes) + AES-GCM ciphertext. */
    private fun seal(k: SecretKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        if (k is SecretKeySpec) {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        } else {
            c.init(Cipher.ENCRYPT_MODE, k) // Keystore keys pick their own IV
        }
        return c.iv + c.doFinal(data)
    }

    private fun open(k: SecretKey, blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    /** Random password: letters, digits and symbols. */
    fun generatePassword(length: Int = 16): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*-_=+?"
        val r = SecureRandom()
        return (1..length).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
}
