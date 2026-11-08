package dev.ray.reminders.ui

import androidx.compose.foundation.layout.width
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
import androidx.compose.animation.core.animateFloat

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
    val data by Vault.data.collectAsState()
    val d = data
    if (d == null) VaultLock(active) else VaultContents(d)
    if (offerAfterSetup && d != null) FingerprintOffer { offerAfterSetup = false }
}

// ------------------------------------------------------------------------ locked

@Composable
internal fun VaultLock(active: Boolean) {
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

    // With fingerprint on, first choose how to unlock; otherwise straight to the PIN pad.
    val bio = Vault.biometricOn(ctx)
    var mode by remember { mutableStateOf(if (bio) UnlockMode.CHOOSE else UnlockMode.PIN) }
    fun fingerprint() {
        val cipher = Vault.biometricUnlockCipher(ctx)
        if (cipher == null) {
            if (Vault.isSetUp(ctx)) message = "Fingerprint changed on this phone. Use your PIN, then turn it back on in settings."
            mode = UnlockMode.PIN
            return
        }
        mode = UnlockMode.FINGER
        askFingerprint(
            ctx, "Unlock passwords", cipher,
            onSuccess = { Vault.finishBiometricUnlock(ctx, it) },
            onCancel = { if (mode == UnlockMode.FINGER) mode = UnlockMode.CHOOSE },
        )
    }
    if (mode != UnlockMode.PIN) BackHandler(enabled = mode == UnlockMode.FINGER) { mode = UnlockMode.CHOOSE }
    when (mode) {
        UnlockMode.CHOOSE -> {
            UnlockChooser(message, onPin = { mode = UnlockMode.PIN }, onFingerprint = ::fingerprint, onForgot = { confirmReset = true })
            if (confirmReset) ForgotPinDialog(onDismiss = { confirmReset = false }, onErase = { Vault.reset(ctx); confirmReset = false; setUp = false })
            return
        }
        UnlockMode.FINGER -> { ScanningScreen(onUsePin = { mode = UnlockMode.PIN }); return }
        UnlockMode.PIN -> {}
    }

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
    if (confirmReset) ForgotPinDialog(onDismiss = { confirmReset = false }, onErase = { Vault.reset(ctx); confirmReset = false; setUp = false })
}

private enum class UnlockMode { CHOOSE, PIN, FINGER }

@Composable
private fun ForgotPinDialog(onDismiss: () -> Unit, onErase: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Nota.Shelf,
    title = { Text("Erase saved passwords?") },
    text = { Text("Without the PIN they can't be decrypted. This deletes them all so you can set a new PIN.", color = Nota.Sub) },
    confirmButton = { TextButton(onClick = onErase) { Text("Erase", color = Nota.Error) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Nota.Sub) } },
)

/** "Unlock with": PIN in the middle; the fingerprint is a circle low down, roughly over the phone's sensor. */
@Composable
private fun UnlockChooser(message: String?, onPin: () -> Unit, onFingerprint: () -> Unit, onForgot: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(34.dp))
        Text(
            "Unlock with", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Nota.Text,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        UnlockOption("PIN", R.drawable.ic_dialpad, Modifier.width(170.dp), onPin)
        if (message != null) Text(
            message, style = MaterialTheme.typography.bodySmall, color = Nota.Error, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        TextButton(onClick = onForgot, modifier = Modifier.padding(top = 16.dp)) { Text("Forgot PIN?", color = Nota.Sub) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(78.dp).clip(CircleShape).background(Nota.Shelf).border(2.dp, Nota.Mint.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onFingerprint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = "Unlock with fingerprint", tint = Nota.Mint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UnlockOption(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(Nota.Shelf).clickable(onClick = onClick).padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Nota.Raised), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Nota.Mint, modifier = Modifier.size(30.dp))
        }
        Text(label, fontFamily = Nota.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Nota.Text, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Behind the phone's fingerprint sheet: a glowing print with rings rippling out, pointing toward the sensor. */
@Composable
private fun ScanningScreen(onUsePin: () -> Unit) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "scan")
    val wave by pulse.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600)), label = "wave",
    )
    val glow by pulse.animateFloat(
        0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "glow",
    )
    val accent = Nota.Mint
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("Scanning", fontFamily = Nota.Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Nota.Text)
        Text("Hold your finger on the sensor", style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.padding(top = 6.dp))
        // Up here, well away from the sensor, so a quick second tap doesn't hit it.
        TextButton(onClick = onUsePin, modifier = Modifier.padding(top = 10.dp)) { Text("Use PIN instead", color = Nota.Mint) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            // Two rings rippling outward, half a beat apart.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                listOf(wave, (wave + 0.5f) % 1f).forEach { t ->
                    drawCircle(
                        accent.copy(alpha = (1f - t) * 0.5f), radius = size.minDimension / 2 * (0.42f + 0.58f * t),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f * glow + 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = null, tint = accent.copy(alpha = glow), modifier = Modifier.size(62.dp))
            }
        }
        Spacer(Modifier.weight(1f))
    }
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
private fun VaultContents(data: dev.ray.reminders.data.VaultData) {
    val entries = data.entries
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
    var openFolder by rememberSaveable { mutableStateOf<Int?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    val folder = data.folders.firstOrNull { it.id == openFolder }
    if (openFolder != null && folder == null) openFolder = null
    if (folder != null) BackHandler { openFolder = null }

    editing?.let { e -> EntryEditor(e, data.folders.map { it.id to it.name }, onClose = { editing = null }); return }
    if (changingPin) {
        PinSetup("Choose a new PIN", null, onDone = { pin, finished -> Vault.changePin(ctx, pin); changingPin = false }, onCancel = { changingPin = false })
        return
    }

    val searching = query.isNotBlank()
    val shown = entries.filter {
        when {
            searching -> it.name.contains(query, true) || it.username.contains(query, true) || it.url.contains(query, true)
            folder != null -> it.folderId == folder.id
            // Top level: passwords that aren't in a folder (folders are listed above them).
            else -> it.folderId == null || data.folders.none { f -> f.id == it.folderId }
        }
    }.sortedWith(compareBy<VaultEntry> { it.order }.thenBy { it.name.lowercase() })

    // Hold to pick up a password: drag to reorder, or drop it on a folder.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val arrange = rememberArrangeState(listState)
    val wobble = rememberWobble()
    val displayed = arrange.order?.let { o -> o.mapNotNull { id -> shown.firstOrNull { it.id == id } } } ?: shown
    fun moveEntries(ids: Set<Int>, to: Int?) { Vault.move(ctx, ids, to); sel.clear() }
    fun dropped(d: ArrangeState.Drop) {
        when (val t = d.target) {
            "out" -> moveEntries(sel.selected + d.id, null)
            is String -> moveEntries(sel.selected + d.id, t.removePrefix("f").toIntOrNull())
            else -> if (d.moved) Vault.arrange(ctx, d.order)
        }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxSize()) {
            if (sel.active) SelectionBar(
                sel, "password", onDelete = { Vault.delete(ctx, it) },
                extra = { MoveButton(data.folders.map { it.id to it.name }, folder?.id) { to -> moveEntries(sel.selected, to) } },
            )
            else if (folder != null) FolderHeader(
                folder.name, locked = false, onBack = { openFolder = null },
                onRename = { renameFolder = true }, onDelete = { deleteFolder = true },
            )
            else Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, "Search passwords", Modifier.weight(1f))
                IconButton(onClick = { Vault.lock() }) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = "Lock", tint = Nota.Mint, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { newFolder = true }) {
                    Icon(painterResource(R.drawable.ic_folder_add), contentDescription = "New folder", tint = Nota.Cyan, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Nota.Sub) }
            }
            LazyColumn(
                Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (folder != null && arrange.dragging != null) item(key = "out") { DropOutRow(arrange.hover == "out") }
                if (folder == null && !searching) items(data.folders, key = { "f${it.id}" }) { f ->
                    FolderRow(
                        f.name, entries.count { it.folderId == f.id }, locked = false,
                        highlight = arrange.hover == "f${f.id}", modifier = Modifier.animateItem(),
                    ) { openFolder = f.id }
                }
                if (shown.isEmpty() && (folder != null || data.folders.isEmpty() || searching)) item {
                    Text(
                        when { searching -> "No matches."; folder != null -> "Nothing in this folder yet."; else -> "No passwords saved yet." },
                        style = MaterialTheme.typography.bodyMedium, color = Nota.Sub, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(displayed, key = { it.id }) { e ->
                    Row(
                        (if (arrange.dragging == e.id) Modifier else Modifier.animateItem()).arrangeable(
                            arrange, sel, e.id, enabled = !searching, wobble = wobble,
                            order = { displayed.map { it.id } }, canSwap = { _, _ -> true },
                            onOpen = { editing = e }, onDrop = ::dropped,
                        ).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nota.Shelf).padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
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
            onClick = { editing = VaultEntry(id = 0, folderId = folder?.id) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(vertical = 20.dp, horizontal = 4.dp),
        )
    }

    if (newFolder) FolderNameDialog("New folder", "", onDismiss = { newFolder = false }) { name ->
        newFolder = false; openFolder = Vault.addFolder(ctx, name)
    }
    if (renameFolder && folder != null) FolderNameDialog("Rename folder", folder.name, onDismiss = { renameFolder = false }) { name ->
        renameFolder = false; Vault.renameFolder(ctx, folder.id, name)
    }
    if (deleteFolder && folder != null) ConfirmFolderDelete(folder.name, "passwords", onDismiss = { deleteFolder = false }) {
        deleteFolder = false; Vault.deleteFolder(ctx, folder.id); openFolder = null
    }
    if (settings) VaultSettings(onDismiss = { settings = false }, onChangePin = { settings = false; changingPin = true })
}

@Composable
private fun VaultSettings(onDismiss: () -> Unit, onChangePin: () -> Unit) {
    val ctx = LocalContext.current
    var bio by remember { mutableStateOf(Vault.biometricOn(ctx)) }
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Nota.Mint) } },
    )
}

@Composable
private fun EntryEditor(entry: VaultEntry, folders: List<Pair<Int, String>>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val isNew = entry.id == 0
    var name by remember { mutableStateOf(entry.name) }
    var username by remember { mutableStateOf(entry.username) }
    var password by remember { mutableStateOf(entry.password) }
    var url by remember { mutableStateOf(entry.url) }
    var folderId by remember { mutableStateOf(entry.folderId) }
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
            if (folders.isNotEmpty()) FolderPicker(folders, folderId, onPick = { folderId = it }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                "Save",
                onClick = {
                    if (name.isNotBlank()) {
                        Vault.upsert(
                            ctx,
                            entry.copy(
                                id = if (isNew) Vault.newId() else entry.id, name = name.trim(), username = username.trim(),
                                password = password, url = url.trim(), notes = notes.trim(), updatedAt = System.currentTimeMillis(),
                                folderId = folderId,
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
