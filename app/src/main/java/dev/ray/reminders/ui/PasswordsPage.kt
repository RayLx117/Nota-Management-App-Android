package dev.ray.reminders.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ray.reminders.R
import dev.ray.reminders.data.Vault
import dev.ray.reminders.data.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.math.roundToInt

// ------------------------------------------------------------------------ helpers

private fun biometricAvailable(ctx: Context) =
    ctx.getSystemService(BiometricManager::class.java)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** The system fingerprint prompt, tied to [cipher] so the vault key only opens after a real match. */
private fun askFingerprint(ctx: Context, title: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onCancel: () -> Unit = {}) {
    val prompt = BiometricPrompt.Builder(ctx)
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButton("Use PIN", ctx.mainExecutor) { _, _ -> onCancel() }
        .build()
    prompt.authenticate(
        BiometricPrompt.CryptoObject(cipher), CancellationSignal(), ctx.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancel()
        },
    )
}

/** Copy, marked sensitive so Android hides it in the clipboard preview. */
private fun copySecret(ctx: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable
private fun vaultFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Nota.Mint, unfocusedBorderColor = Nota.Raised,
    focusedContainerColor = Nota.Shelf, unfocusedContainerColor = Nota.Shelf,
    focusedLabelColor = Nota.Mint, unfocusedLabelColor = Nota.Sub, cursorColor = Nota.Mint,
)

// ------------------------------------------------------------------------ page

/** Set right after the first PIN is chosen, so the fingerprint offer shows over the (now unlocked) page. */
private var offerAfterSetup by mutableStateOf(false)

@Composable
fun PasswordsPage(active: Boolean) {
    val entries by Vault.entries.collectAsState()
    // Swiping away from Passwords locks it again.
    LaunchedEffect(active) { if (!active) Vault.lock() }
    val list = entries
    if (list == null) VaultLock(active) else VaultContents(list)
    if (offerAfterSetup && list != null) FingerprintOffer { offerAfterSetup = false }
}

// ------------------------------------------------------------------------ locked

@Composable
private fun VaultLock(active: Boolean) {
    val ctx = LocalContext.current
    var setUp by remember { mutableStateOf(Vault.isSetUp(ctx)) }
    if (!setUp) {
        PinSetup(
            title = "Set a 4-digit PIN",
            note = "Your passwords are encrypted on this phone. If you forget the PIN they can't be recovered.",
            onDone = { pin, finished ->
                Vault.setUp(ctx, pin)
                finished()
                setUp = true
            },
        )
        return
    }

    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(Vault.lockedUntil(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmReset by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(lockedUntil) { while (lockedUntil > now) { delay(500); now = System.currentTimeMillis() } }
    val lockedOut = lockedUntil > now

    fun fingerprint() {
        val cipher = Vault.biometricUnlockCipher(ctx)
        if (cipher == null) {
            if (Vault.isSetUp(ctx)) message = "Fingerprint changed on this phone. Use your PIN, then turn it back on in settings."
            return
        }
        askFingerprint(ctx, "Unlock passwords", cipher, onSuccess = { Vault.finishBiometricUnlock(ctx, it) })
    }
    // Ask for a fingerprint as soon as the page is in view.
    LaunchedEffect(active) { if (active && Vault.biometricOn(ctx) && !lockedOut) fingerprint() }

    fun submit(p: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { Vault.unlockWithPin(ctx, p) }
            busy = false
            pin = ""
            when (result) {
                Vault.PinResult.Ok -> message = null
                is Vault.PinResult.Wrong -> {
                    message = if (result.attemptsLeft > 0) "Wrong PIN · ${result.attemptsLeft} tries before a timeout" else "Wrong PIN"
                    shake.animateTo(0f, keyframes { durationMillis = 400; 24f at 60; -24f at 140; 16f at 220; -10f at 300 })
                }
                is Vault.PinResult.LockedOut -> { lockedUntil = result.untilMillis; now = System.currentTimeMillis(); message = null }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(34.dp))
        Text(
            "Enter PIN", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Nota.Text,
            modifier = Modifier.padding(top = 10.dp),
        )
        PinDots(pin.length, Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }.padding(vertical = 18.dp))
        Text(
            when {
                lockedOut -> "Too many tries. Try again in ${((lockedUntil - now) / 1000 + 1)} s"
                busy -> "Checking…"
                else -> message ?: " "
            },
            style = MaterialTheme.typography.bodySmall, color = if (lockedOut || message != null) Nota.Error else Nota.Sub,
            textAlign = TextAlign.Center, modifier = Modifier.height(36.dp),
        )
        PinPad(
            enabled = !lockedOut && !busy,
            onDigit = { d -> if (pin.length < 4) { pin += d; if (pin.length == 4) submit(pin) } },
            onBackspace = { pin = pin.dropLast(1) },
            leftKey = if (Vault.biometricOn(ctx)) ({ fingerprint() }) else null,
        )
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 12.dp)) {
            Text("Forgot PIN?", color = Nota.Sub)
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = Nota.Shelf,
        title = { Text("Erase saved passwords?") },
        text = { Text("Without the PIN they can't be decrypted. This deletes them all so you can set a new PIN.", color = Nota.Sub) },
        confirmButton = { TextButton(onClick = { Vault.reset(ctx); confirmReset = false; setUp = false }) { Text("Erase", color = Nota.Error) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Nota.Sub) } },
    )
}

/** Enter a new PIN twice. [onDone] gets the PIN and a callback to run once the vault is ready (offers fingerprint). */
@Composable
private fun PinSetup(title: String, note: String?, onDone: (String, () -> Unit) -> Unit, onCancel: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(mismatch) {
        if (mismatch) shake.animateTo(0f, keyframes { durationMillis = 400; 24f at 60; -24f at 140; 16f at 220; -10f at 300 })
    }
    if (onCancel != null) BackHandler { onCancel() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(34.dp))
        Text(
            if (first == null) title else "Enter it again", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp, color = Nota.Text, modifier = Modifier.padding(top = 10.dp),
        )
        PinDots(pin.length, Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }.padding(vertical = 18.dp))
        Text(
            if (mismatch) "Those didn't match. Start again." else note ?: " ",
            style = MaterialTheme.typography.bodySmall, color = if (mismatch) Nota.Error else Nota.Sub,
            textAlign = TextAlign.Center, modifier = Modifier.height(36.dp),
        )
        PinPad(
            enabled = true,
            onDigit = { d ->
                if (pin.length < 4) pin += d
                if (pin.length == 4) {
                    val f = first
                    when {
                        f == null -> { first = pin; pin = ""; mismatch = false }
                        f == pin -> onDone(pin) { if (biometricAvailable(ctx) && !Vault.biometricOn(ctx)) offerAfterSetup = true }
                        else -> { first = null; pin = ""; mismatch = true }
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            leftKey = null,
        )
        if (onCancel != null) TextButton(onClick = onCancel, modifier = Modifier.padding(top = 12.dp)) { Text("Cancel", color = Nota.Sub) }
    }
}

@Composable
private fun FingerprintOffer(onDone: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Nota.Shelf,
        icon = { Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = null, tint = Nota.Mint) },
        title = { Text("Unlock with fingerprint?") },
        text = { Text("Your PIN still works as a backup.", color = Nota.Sub) },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                runCatching { Vault.biometricSetupCipher() }.getOrNull()?.let { cipher ->
                    askFingerprint(ctx, "Turn on fingerprint unlock", cipher, onSuccess = { Vault.finishBiometricSetup(ctx, it) })
                }
            }) { Text("Turn on", color = Nota.Mint) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Not now", color = Nota.Sub) } },
    )
}

@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .then(if (i < filled) Modifier.background(Nota.Mint) else Modifier.border(2.dp, Nota.Outline, CircleShape)),
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit, leftKey: (() -> Unit)?) {
    Column(Modifier.alpha(if (enabled) 1f else 0.35f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { row.forEach { d -> PadKey(enabled, { onDigit(d) }) { PadDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (leftKey != null) PadKey(enabled, leftKey, plain = true) {
                Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = "Use fingerprint", tint = Nota.Mint, modifier = Modifier.size(30.dp))
            } else Spacer(Modifier.size(72.dp))
            PadKey(enabled, { onDigit('0') }) { PadDigit('0') }
            PadKey(enabled, onBackspace, plain = true) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = "Delete", tint = Nota.Sub, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PadDigit(d: Char) = Text(d.toString(), fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = Nota.Text)

@Composable
private fun PadKey(enabled: Boolean, onClick: () -> Unit, plain: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).then(if (plain) Modifier else Modifier.background(Nota.Shelf))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ------------------------------------------------------------------------ unlocked

@Composable
private fun VaultContents(entries: List<VaultEntry>) {
    val ctx = LocalContext.current
    // No screenshots or recents previews while passwords are on screen.
    DisposableEffect(Unit) {
        val window = (ctx as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var settings by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val sel = rememberSelection(entries.map { it.id }.toSet())

    editing?.let { e -> EntryEditor(e, onClose = { editing = null }); return }
    if (changingPin) {
        PinSetup("Choose a new PIN", null, onDone = { pin, finished -> Vault.changePin(ctx, pin); changingPin = false }, onCancel = { changingPin = false })
        return
    }

    val shown = entries.filter {
        query.isBlank() || it.name.contains(query, true) || it.username.contains(query, true) || it.url.contains(query, true)
    }.sortedBy { it.name.lowercase() }

    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxSize()) {
            if (sel.active) SelectionBar(sel, "password", onDelete = { Vault.delete(ctx, it) })
            else Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, "Search passwords", Modifier.weight(1f))
                IconButton(onClick = { Vault.lock() }) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = "Lock", tint = Nota.Mint, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Nota.Sub) }
            }
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) item {
                    Text(
                        if (entries.isEmpty()) "No passwords saved yet." else "No matches.",
                        style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(shown, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nota.Shelf)
                            .selectable(sel, e.id) { editing = e }.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sel.active) SelectCircle(sel.has(e.id), Modifier.padding(end = 12.dp))
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Nota.Raised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e.name.take(1).uppercase(), fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Nota.Mint)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(e.name, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Nota.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.username.isNotBlank()) Text(e.username, style = MaterialTheme.typography.bodySmall, color = Nota.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!sel.active && e.password.isNotEmpty()) IconButton(onClick = { copySecret(ctx, "Password", e.password) }) {
                            Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy password", tint = Nota.Mint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (!sel.active) AddButton(
            onClick = { editing = VaultEntry(id = 0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(vertical = 20.dp, horizontal = 4.dp),
        )
    }

    if (settings) VaultSettings(onDismiss = { settings = false }, onChangePin = { settings = false; changingPin = true })
}

@Composable
private fun VaultSettings(onDismiss: () -> Unit, onChangePin: () -> Unit) {
    val ctx = LocalContext.current
    var bio by remember { mutableStateOf(Vault.biometricOn(ctx)) }
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nota.Shelf,
        title = { Text("Password settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (biometricAvailable(ctx)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unlock with fingerprint", style = MaterialTheme.typography.bodyLarge, color = Nota.Text, modifier = Modifier.weight(1f))
                    Switch(checked = bio, colors = notaSwitchColors(), onCheckedChange = { on ->
                        if (on) runCatching { Vault.biometricSetupCipher() }.getOrNull()?.let { cipher ->
                            askFingerprint(ctx, "Turn on fingerprint unlock", cipher, onSuccess = {
                                Vault.finishBiometricSetup(ctx, it); bio = true
                            })
                        } else { Vault.turnOffBiometric(ctx); bio = false }
                    })
                }
                SoftButton("Change PIN", onClick = onChangePin, modifier = Modifier.fillMaxWidth(), height = 46.dp, color = Nota.Mint)
                SoftButton("Erase all passwords", onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(), height = 46.dp, color = Nota.Error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Nota.Mint) } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = Nota.Shelf,
        title = { Text("Erase all passwords?") },
        text = { Text("This deletes every saved password and your PIN.", color = Nota.Sub) },
        confirmButton = { TextButton(onClick = { Vault.reset(ctx); confirmReset = false; onDismiss() }) { Text("Erase", color = Nota.Error) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Nota.Sub) } },
    )
}

@Composable
private fun EntryEditor(entry: VaultEntry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = entry.id == 0
    var name by remember { mutableStateOf(entry.name) }
    var username by remember { mutableStateOf(entry.username) }
    var password by remember { mutableStateOf(entry.password) }
    var url by remember { mutableStateOf(entry.url) }
    var notes by remember { mutableStateOf(entry.notes) }
    var show by remember { mutableStateOf(isNew) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nota.Text) }
            Text(if (isNew) "New password" else "Edit password", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Nota.Text)
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                username, { username = it }, label = { Text("Username or email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                trailingIcon = {
                    if (username.isNotEmpty()) IconButton(onClick = { copySecret(ctx, "Username", username) }) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy username", tint = Nota.Sub, modifier = Modifier.size(20.dp))
                    }
                },
                shape = RoundedCornerShape(16.dp), colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Nota.Mono),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { show = !show }) {
                            Icon(painterResource(if (show) R.drawable.ic_eye_off else R.drawable.ic_eye), contentDescription = if (show) "Hide" else "Show", tint = Nota.Sub, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { password = Vault.generatePassword(); show = true }) {
                            Icon(painterResource(R.drawable.ic_dice), contentDescription = "Generate a strong password", tint = Nota.Mint, modifier = Modifier.size(20.dp))
                        }
                        if (password.isNotEmpty()) IconButton(onClick = { copySecret(ctx, "Password", password) }) {
                            Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy password", tint = Nota.Sub, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp), colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                url, { url = it }, label = { Text("Website") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp), colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, shape = RoundedCornerShape(16.dp), colors = vaultFieldColors(), modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                "Save",
                onClick = {
                    if (name.isNotBlank()) {
                        Vault.upsert(
                            ctx,
                            entry.copy(
                                id = if (isNew) Vault.newId() else entry.id, name = name.trim(), username = username.trim(),
                                password = password, url = url.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth().alpha(if (name.isNotBlank()) 1f else 0.4f),
            )
            if (!isNew) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Delete", color = Nota.Error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Nota.Shelf,
        title = { Text("Delete ${entry.name}?") },
        confirmButton = { TextButton(onClick = { Vault.delete(ctx, setOf(entry.id)); confirmDelete = false; onClose() }) { Text("Delete", color = Nota.Error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Nota.Sub) } },
    )
}
