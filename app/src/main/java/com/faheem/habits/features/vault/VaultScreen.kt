@file:Suppress("LongParameterList", "CyclomaticComplexMethod", "TooManyFunctions", "FunctionNaming", "ReturnCount")

package com.faheem.habits.features.vault
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).vaultDao()
    private val prefs = app.getSharedPreferences("momentum_vault", Context.MODE_PRIVATE)
    private val vmkWrappedPref = "vmk_wrapped_password_v1"
    private val biometricWrappedPref = "vmk_wrapped_biometric_v1"
    private val failedAttemptsPref = "failed_unlock_attempts"

    fun isConfigured(): Boolean = prefs.contains(VaultCrypto.SALT_PREF) && prefs.contains(VaultCrypto.CHECK_PREF)

    suspend fun setup(masterPassword: String): ByteArray = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.newSalt()
        val passwordChars = masterPassword.toCharArray()
        val key = VaultCrypto.deriveKey(passwordChars, salt)
        passwordChars.fill('\u0000')
        val vmk = VaultCrypto.newVmk()
        val wrapped = VaultCrypto.wrapVmk(vmk, key)
        val saved = prefs.edit()
            .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
            .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(key)))
            .putString(vmkWrappedPref, encodeWrapped(wrapped))
            .commit()
        if (!saved) {
            key.fill(0)
            vmk.fill(0)
            error("Could not save the vault setup. Please try again.")
        }
        key.fill(0)
        vmk
    }

    suspend fun unlock(masterPassword: String): ByteArray? = withContext(Dispatchers.IO) {
        val failedAttempts = prefs.getInt(failedAttemptsPref, 0)
        if (failedAttempts > 0) {
            Thread.sleep((500L * (1L shl failedAttempts.coerceAtMost(6))).coerceAtMost(30_000L))
        }
        val salt = prefs.getString(VaultCrypto.SALT_PREF, null) ?: return@withContext null
        val check = prefs.getString(VaultCrypto.CHECK_PREF, null) ?: return@withContext null
        val passwordChars = masterPassword.toCharArray()
        val key = try { VaultCrypto.deriveKey(passwordChars, VaultCrypto.decode(salt)) } catch (_: Exception) {
            passwordChars.fill('\u0000')
            return@withContext null
        }
        passwordChars.fill('\u0000')
        val matches = try { VaultCrypto.matches(key, VaultCrypto.decode(check)) } catch (_: Exception) { false }
        if (!matches) {
            prefs.edit().putInt(failedAttemptsPref, (failedAttempts + 1).coerceAtMost(10)).apply()
            key.fill(0)
            return@withContext null
        }
        prefs.edit().remove(failedAttemptsPref).apply()
        val wrapped = prefs.getString(vmkWrappedPref, null)
        if (wrapped != null) {
            return@withContext try {
                val parts = decodeWrapped(wrapped)
                val vmk = VaultCrypto.unwrapVmk(parts.first, parts.second, key)
                key.fill(0)
                vmk
            } catch (_: Exception) {
                key.fill(0)
                null
            }
        }
        migrateLegacy(key, salt)
    }

    suspend fun unlockWithBiometric(cipher: javax.crypto.Cipher): ByteArray? = withContext(Dispatchers.IO) {
        val wrapped = prefs.getString(biometricWrappedPref, null) ?: return@withContext null
        runCatching {
            val parts = decodeWrapped(wrapped)
            VaultCrypto.unwrapVmkWithBiometric(cipher, parts.second)
        }.getOrNull()
    }

    fun prepareBiometricCipher(): javax.crypto.Cipher = VaultCrypto.biometricCipher()

    fun prepareBiometricDecryptCipher(): javax.crypto.Cipher? {
        val wrapped = prefs.getString(biometricWrappedPref, null) ?: return null
        return runCatching { VaultCrypto.biometricDecryptCipher(decodeWrapped(wrapped).first) }.getOrNull()
    }

    fun hasBiometricUnlock(): Boolean = prefs.contains(biometricWrappedPref)

    suspend fun enableBiometric(vmk: ByteArray, cipher: javax.crypto.Cipher) = withContext(Dispatchers.IO) {
        val wrapped = VaultCrypto.wrapVmkWithBiometric(vmk, cipher)
        check(prefs.edit().putString(biometricWrappedPref, encodeWrapped(wrapped)).commit()) {
            "Could not save biometric vault unlock."
        }
    }

    suspend fun changeMasterPassword(vmk: ByteArray, newPassword: String) = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.newSalt()
        val chars = newPassword.toCharArray()
        val key = try {
            VaultCrypto.deriveKey(chars, salt)
        } finally {
            chars.fill('\u0000')
        }
        try {
            val wrapped = VaultCrypto.wrapVmk(vmk, key)
            check(
                prefs.edit()
                    .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
                    .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(key)))
                    .putString(vmkWrappedPref, encodeWrapped(wrapped))
                    .commit()
            ) { "Could not save the new master password." }
        } finally {
            key.fill(0)
        }
    }

    private suspend fun migrateLegacy(oldKey: ByteArray, salt: String): ByteArray {
        try {
            val records = dao.getAll()
            val backup = JSONArray()
            records.forEach { row ->
                backup.put(
                    JSONObject()
                        .put("id", row.id)
                        .put("nonce", VaultCrypto.encode(row.nonce))
                        .put("ciphertext", VaultCrypto.encode(row.ciphertext))
                        .put("updatedAt", row.updatedAt)
                )
            }
            val backupFile = java.io.File(getApplication<Application>().filesDir, "vault-legacy-migration-backup.json")
            backupFile.writeText(
                JSONObject().put("format", "momentum-vault-legacy-encrypted-backup")
                    .put("version", 1).put("salt", salt).put("entries", backup).toString(),
                Charsets.UTF_8
            )
            val decrypted = records.map { row -> row to VaultCrypto.decrypt(row.nonce, row.ciphertext, oldKey) }
            val vmk = VaultCrypto.newVmk()
            val migrated = decrypted.map { (row, secret) ->
                val encrypted = VaultCrypto.encrypt(secret, vmk)
                VaultRecord(row.id, encrypted.first, encrypted.second, row.updatedAt)
            }
            dao.upsertAll(migrated)
            val passwordWrapped = VaultCrypto.wrapVmk(vmk, oldKey)
            check(
                prefs.edit().putString(vmkWrappedPref, encodeWrapped(passwordWrapped)).commit()
            ) { "Could not finish the vault encryption migration." }
            return vmk
        } finally {
            oldKey.fill(0)
        }
    }

    private fun encodeWrapped(value: Pair<ByteArray, ByteArray>): String =
        "${VaultCrypto.encode(value.first)}:${VaultCrypto.encode(value.second)}"

    private fun decodeWrapped(value: String): Pair<ByteArray, ByteArray> {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid vault key wrapper." }
        return VaultCrypto.decode(parts[0]) to VaultCrypto.decode(parts[1])
    }

    suspend fun readAll(key: ByteArray): Pair<List<VaultListItem>, Int> {
        // Snapshot the key before switching threads: the UI may clear its session key while this runs.
        val operationKey = key.copyOf()
        return try {
            withContext(Dispatchers.IO) {
                var unreadable = 0
                val entries = dao.getAll().mapNotNull { record ->
                    try { VaultListItem(record.id, VaultCrypto.decrypt(record.nonce, record.ciphertext, operationKey), record.updatedAt) }
                    catch (_: Exception) { unreadable++; null }
                }
                entries to unreadable
            }
        } finally {
            operationKey.fill(0)
        }
    }

    suspend fun save(id: Int?, secret: VaultSecret, key: ByteArray) {
        // Use an operation-scoped copy so locking the vault cannot zero the key mid-encryption.
        val operationKey = key.copyOf()
        try {
            withContext(Dispatchers.IO) {
                val encrypted = VaultCrypto.encrypt(secret, operationKey)
                dao.upsert(VaultRecord(id = id ?: 0, nonce = encrypted.first, ciphertext = encrypted.second, updatedAt = System.currentTimeMillis()))
            }
        } finally {
            operationKey.fill(0)
        }
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) { dao.delete(id) }

    suspend fun exportEncrypted(destination: Uri) = withContext(Dispatchers.IO) {
        val salt = prefs.getString(VaultCrypto.SALT_PREF, null) ?: error("Vault is not configured.")
        val records = dao.getAll()
        val entries = JSONArray()
        records.forEach { row ->
            entries.put(JSONObject().put("id", row.id).put("nonce", VaultCrypto.encode(row.nonce))
                .put("ciphertext", VaultCrypto.encode(row.ciphertext)).put("updatedAt", row.updatedAt))
        }
        val bytes = JSONObject().put("format", "momentum-encrypted-vault")
            .put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put("salt", salt).put("entries", entries).toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 10 * 1024 * 1024) { "Encrypted vault export exceeds the 10 MB safety limit." }
        val output = getApplication<android.app.Application>().contentResolver.openOutputStream(destination, "w")
            ?: error("Could not open the encrypted export destination.")
        output.use { it.write(bytes) }
    }
}

@Composable
fun VaultScreen(vm: VaultViewModel = viewModel()) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(window) {
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val activity = context.findActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.setRecentsScreenshotEnabled(false)
        }
        onDispose {
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity?.setRecentsScreenshotEnabled(true)
            }
        }
    }

    var configured by remember { mutableStateOf(vm.isConfigured()) }
    var sessionKey by remember { mutableStateOf<ByteArray?>(null) }
    var sessionEpoch by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<VaultListItem>>(emptyList()) }
    var unreadable by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultListItem?>(null) }
    var selected by remember { mutableStateOf<VaultListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultListItem?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val vaultExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val key = sessionKey
        if (uri != null && key != null) scope.launch {
            busy = true
            exportMessage = try { vm.exportEncrypted(uri); "Encrypted vault backup exported. No decrypted vault values were written." }
            catch (e: Exception) { "Encrypted vault export failed: ${e.message ?: "unknown error"}" }
            finally { busy = false }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        sessionEpoch++
        sessionKey?.fill(0)
        sessionKey = null
        items = emptyList()
        selected = null
        editorOpen = false
        editing = null
        deleteTarget = null
    }
    LaunchedEffect(sessionKey, refresh, sessionEpoch) {
        val key = sessionKey
        val epoch = sessionEpoch
        if (key != null) {
            val result = vm.readAll(key)
            // Ignore results from an earlier session if the vault was locked during the read.
            if (sessionKey === key && sessionEpoch == epoch) {
                items = result.first
                unreadable = result.second
            }
        }
    }

    fun unlockOrCreate(master: String, setup: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                val key = if (setup) vm.setup(master) else vm.unlock(master)
                if (key == null) error = "That master password didn’t unlock the vault. Try again."
                else {
                    sessionEpoch++
                    sessionKey = key
                    configured = true
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not open the vault. Please try again."
            } finally { busy = false }
        }
    }

    fun launchBiometricUnlock() {
        val activity = context.findActivity() as? FragmentActivity ?: return
        val cipher = vm.prepareBiometricDecryptCipher() ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch {
                        val unlocked = result.cryptoObject?.cipher?.let { vm.unlockWithBiometric(it) }
                        if (unlocked != null) {
                            sessionEpoch++
                            sessionKey = unlocked
                        } else {
                            error = "Biometric unlock could not open the vault."
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Momentum vault")
                .setSubtitle("Use strong biometrics to unwrap the vault key")
                .setNegativeButtonText("Use master password")
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    fun enableBiometricUnlock() {
        val activity = context.findActivity() as? FragmentActivity ?: return
        val vmk = sessionKey ?: return
        val cipher = runCatching { vm.prepareBiometricCipher() }.getOrElse {
            error = "Biometric unlock is unavailable on this device."
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch {
                        runCatching { vm.enableBiometric(vmk, result.cryptoObject?.cipher ?: cipher) }
                            .onFailure { error = it.message ?: "Could not enable biometric unlock." }
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric vault unlock")
                .setSubtitle("Your master password remains available as a fallback")
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    val key = sessionKey
    if (key == null) {
        VaultGate(
            configured,
            busy,
            error,
            biometricAvailable = configured && vm.hasBiometricUnlock(),
            onSubmit = { unlockOrCreate(it, !configured) },
            onBiometric = ::launchBiometricUnlock
        )
    } else {
        VaultHome(
            items = items,
            unreadable = unreadable,
            onLock = {
                sessionEpoch++
                key.fill(0); sessionKey = null; items = emptyList(); selected = null
                editorOpen = false; editing = null; deleteTarget = null
            },
            onAdd = { editing = null; editorOpen = true },
            onSelect = { selected = it },
            onExport = { vaultExportLauncher.launch("momentum-vault-encrypted.json") },
            onEnableBiometric = ::enableBiometricUnlock
        )
        exportMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
    }

    if (editorOpen && key != null) {
        VaultEntryEditor(initial = editing, onDismiss = { editorOpen = false; editing = null }) { secret ->
            scope.launch {
                busy = true
                try {
                    vm.save(editing?.id, secret, key)
                    refresh++
                    editorOpen = false
                    editing = null
                    selected = null
                } catch (e: Exception) { error = e.message ?: "Could not save this item." }
                finally { busy = false }
            }
        }
    }
    selected?.let { item ->
        if (key != null) VaultDetailSheet(item, onDismiss = { selected = null }, onEdit = { editing = item; editorOpen = true; selected = null }, onDelete = { deleteTarget = item; selected = null })
    }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("Delete secure item?") },
            text = { Text("${item.secret.title} will be permanently removed from this vault.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.delete(item.id); refresh++; deleteTarget = null }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultGate(
    configured: Boolean,
    busy: Boolean,
    error: String?,
    biometricAvailable: Boolean,
    onSubmit: (String) -> Unit,
    onBiometric: () -> Unit
) {
    var master by remember(configured) { mutableStateOf("") }
    var confirm by remember(configured) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember(configured) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HeroCard {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(38.dp), tint = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(if (configured) "Vault locked" else "Create your private vault", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Your passwords are encrypted on this device and never synced by Momentum.", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                }
            }
            if (configured && biometricAvailable) item {
                OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with biometrics")
                }
            }
            if (!configured) {
                item { Text("Choose a strong master password with at least 12 characters. Momentum does not store it and cannot reset it; if you forget it, the vault cannot be opened.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                OutlinedTextField(master, { master = it.take(128); localError = null }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Master password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide master password") } })
            }
            if (!configured) item {
                OutlinedTextField(confirm, { confirm = it.take(128); localError = null }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm master password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation())
            }
            if (localError != null || error != null) item { Text(localError ?: error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            item {
                BigButton(if (busy) "Unlocking…" else if (configured) "Unlock vault" else "Create vault") {
                    when {
                        busy -> Unit
                        !configured && master.length < 12 -> localError = "Use at least 12 characters for your master password."
                        !configured && master != confirm -> localError = "The passwords don’t match."
                        else -> { onSubmit(master); master = ""; confirm = "" }
                    }
                }
            }
            if (configured) item {
                Text("The vault locks when Momentum goes into the background. Screenshots are blocked while Vault is open.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VaultHome(
    items: List<VaultListItem>,
    unreadable: Int,
    onLock: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (VaultListItem) -> Unit,
    onExport: () -> Unit,
    onEnableBiometric: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    val filtered = items.filter { item ->
        (type == "All" || (type == "Logins" && item.secret.type == "login") || (type == "Secure notes" && item.secret.type == "note")) &&
            (query.isBlank() || listOf(item.secret.title, item.secret.username, item.secret.website, item.secret.folder, item.secret.tags).any { it.contains(query, true) })
    }
    val health = vaultHealthReport(items)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Private password vault", fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                            Text("${items.size} secure item${if (items.size == 1) "" else "s"}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("Encrypted on this device", fontSize = 13.sp)
                        }
                        IconButton(onClick = onLock) { Icon(Icons.Rounded.Lock, "Lock vault now", tint = Color.White) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onEnableBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable biometric unlock")
                }
            }
            if (unreadable > 0) item {
                Text("$unreadable item${if (unreadable == 1) "" else "s"} could not be decrypted. Their encrypted data has not been changed.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            item {
                Text(
                    "Password health: ${health.weak} weak · ${health.reused} reused · ${health.duplicates} duplicate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (health.weak + health.reused > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Search titles, usernames, websites") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(16.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Logins", "Secure notes").forEach { label -> FilterChip(type == label, { type = label }, label = { Text(label) }) }
                }
            }
            item { SectionLabel("Your items · ${filtered.size}") }
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Rounded.Password, if (items.isEmpty()) "Your vault is empty" else "No matching items", if (items.isEmpty()) "Add a login or secure note to get started" else "Try another search or filter")
            }
            items(filtered, key = { it.id }) { entry -> VaultItemCard(entry) { onSelect(entry) } }
            item {
                Text("Credentials stay encrypted in the database. Copying a value clears the clipboard after 30 seconds when possible.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Export encrypted vault file") } }
        }
        ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add secure item") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
private fun VaultItemCard(item: VaultListItem, onClick: () -> Unit) {
    val secret = item.secret
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).padding(10.dp), contentAlignment = Alignment.Center) {
                Icon(if (secret.type == "note") Icons.AutoMirrored.Rounded.StickyNote2 else Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(secret.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (secret.type == "note") "Secure note" else listOf(secret.username, secret.website).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Login" }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VaultDetailSheet(item: VaultListItem, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val secret = item.secret
    var reveal by remember(item.id) { mutableStateOf(false) }
    FormSheet(secret.title, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(if (secret.type == "note") Icons.AutoMirrored.Rounded.StickyNote2 else Icons.Rounded.Key, MaterialTheme.colorScheme.primary, 46.dp)
            Spacer(Modifier.width(10.dp))
            Column { Text(if (secret.type == "note") "Secure note" else "Login", fontWeight = FontWeight.SemiBold); Text("Updated ${DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.updatedAt))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (secret.type == "login") {
            if (secret.website.isNotBlank()) VaultValue("Website", secret.website) { copySensitive(context, secret.website) }
            if (secret.username.isNotBlank()) VaultValue("Username", secret.username) { copySensitive(context, secret.username) }
            if (secret.password.isNotBlank()) {
                OutlinedTextField(value = secret.password, onValueChange = {}, readOnly = true, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide password") }
                            IconButton(onClick = { copySensitive(context, secret.password) }) { Icon(Icons.Rounded.ContentCopy, "Copy password") }
                        }
                    })
            }
            if (secret.totp.isNotBlank()) VaultValue("TOTP secret", secret.totp) { copySensitive(context, secret.totp) }
        }
        if (secret.folder.isNotBlank()) Text("Folder: ${secret.folder}", style = MaterialTheme.typography.bodySmall)
        if (secret.tags.isNotBlank()) Text("Tags: ${secret.tags}", style = MaterialTheme.typography.bodySmall)
        if (secret.notes.isNotBlank()) {
            Text(if (secret.type == "note") "Note" else "Notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text(secret.notes)
            TextButton(onClick = { copySensitive(context, secret.notes) }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy note") }
        }
        BigButton("Edit item", onEdit)
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete item", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VaultValue(label: String, value: String, onCopy: () -> Unit) {
    OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = { IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label") } })
}

@Composable
private fun VaultEntryEditor(initial: VaultListItem?, onDismiss: () -> Unit, onSave: (VaultSecret) -> Unit) {
    var type by remember(initial?.id) { mutableStateOf(initial?.secret?.type ?: "login") }
    var title by remember(initial?.id) { mutableStateOf(initial?.secret?.title ?: "") }
    var website by remember(initial?.id) { mutableStateOf(initial?.secret?.website ?: "") }
    var username by remember(initial?.id) { mutableStateOf(initial?.secret?.username ?: "") }
    var password by remember(initial?.id) { mutableStateOf(initial?.secret?.password ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.secret?.notes ?: "") }
    var folder by remember(initial?.id) { mutableStateOf(initial?.secret?.folder ?: "") }
    var tags by remember(initial?.id) { mutableStateOf(initial?.secret?.tags ?: "") }
    var favorite by remember(initial?.id) { mutableStateOf(initial?.secret?.favorite ?: false) }
    var totp by remember(initial?.id) { mutableStateOf(initial?.secret?.totp ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    FormSheet(if (initial == null) "Add secure item" else "Edit secure item", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == "login", { type = "login" }, label = { Text("Login") }, leadingIcon = { Icon(Icons.Rounded.Key, null) })
            FilterChip(type == "note", { type = "note" }, label = { Text("Secure note") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.StickyNote2, null) })
        }
        OutlinedTextField(title, { title = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, placeholder = { Text(if (type == "login") "e.g. Email, Bank" else "e.g. Recovery codes") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        if (type == "login") {
            OutlinedTextField(website, { website = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Website (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username or email") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp),
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide password") } })
            OutlinedButton(onClick = { password = generatePassword() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Generate strong password") }
            OutlinedTextField(totp, { totp = it }, modifier = Modifier.fillMaxWidth(), label = { Text("TOTP secret (optional)") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2, maxLines = 5, shape = RoundedCornerShape(14.dp))
        } else {
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Secure note") }, minLines = 5, maxLines = 12, shape = RoundedCornerShape(14.dp))
        }
        OutlinedTextField(folder, { folder = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Folder (optional)") }, singleLine = true)
        OutlinedTextField(tags, { tags = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tags (comma-separated)") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = favorite, onCheckedChange = { favorite = it })
            Text("Favorite")
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        BigButton(if (initial == null) "Save securely" else "Save changes") {
            if (title.isBlank()) error = "Add a title to save this item."
            else if (type == "login" && password.isBlank()) error = "Add a password or generate one."
            else onSave(
                if (type == "login") VaultSecret(title.trim(), type, username.trim(), password, website.trim(), notes.trim(), folder.trim(), tags.trim(), favorite, totp.trim())
                else VaultSecret(title.trim(), type, "", "", "", notes.trim(), folder.trim(), tags.trim(), favorite)
            )
        }
    }
}

private fun generatePassword(length: Int = 20): String {
    val random = SecureRandom()
    val groups = listOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%^&*()-_=+")
    val alphabet = groups.joinToString("")
    val characters = groups.map { it[random.nextInt(it.length)] }.toMutableList()
    repeat(length - characters.size) { characters += alphabet[random.nextInt(alphabet.length)] }
    for (index in characters.lastIndex downTo 1) {
        val other = random.nextInt(index + 1)
        val current = characters[index]
        characters[index] = characters[other]
        characters[other] = current
    }
    return characters.joinToString("")
}

private fun copySensitive(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Sensitive vault value", value)
    if (Build.VERSION.SDK_INT >= 33) {
        val extras = clip.description.extras ?: PersistableBundle().also { clip.description.extras = it }
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
    val hash = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    val work = OneTimeWorkRequestBuilder<SensitiveClipboardWorker>()
        .setInitialDelay(30, TimeUnit.SECONDS)
        .setInputData(workDataOf(SensitiveClipboardWorker.KEY_HASH to VaultCrypto.encode(hash)))
        .build()
    WorkManager.getInstance(context).enqueue(work)
    Toast.makeText(context, "Copied. Clipboard clears after 30 seconds if unchanged.", Toast.LENGTH_SHORT).show()
    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (current == value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 30_000)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
