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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
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

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).vaultDao()
    private val prefs = app.getSharedPreferences("momentum_vault", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.contains(VaultCrypto.SALT_PREF) && prefs.contains(VaultCrypto.CHECK_PREF)

    suspend fun setup(masterPassword: String): ByteArray = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(masterPassword, salt)
        val saved = prefs.edit()
            .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
            .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(key)))
            .commit()
        if (!saved) {
            key.fill(0)
            error("Could not save the vault setup. Please try again.")
        }
        key
    }

    suspend fun unlock(masterPassword: String): ByteArray? = withContext(Dispatchers.IO) {
        val salt = prefs.getString(VaultCrypto.SALT_PREF, null) ?: return@withContext null
        val check = prefs.getString(VaultCrypto.CHECK_PREF, null) ?: return@withContext null
        val key = try { VaultCrypto.deriveKey(masterPassword, VaultCrypto.decode(salt)) } catch (_: Exception) { return@withContext null }
        val matches = try { VaultCrypto.matches(key, VaultCrypto.decode(check)) } catch (_: Exception) { false }
        if (matches) key else { key.fill(0); null }
    }

    suspend fun readAll(key: ByteArray): Pair<List<VaultListItem>, Int> = withContext(Dispatchers.IO) {
        var unreadable = 0
        val entries = dao.getAll().mapNotNull { record ->
            try { VaultListItem(record.id, VaultCrypto.decrypt(record.nonce, record.ciphertext, key), record.updatedAt) }
            catch (_: Exception) { unreadable++; null }
        }
        entries to unreadable
    }

    suspend fun save(id: Int?, secret: VaultSecret, key: ByteArray) = withContext(Dispatchers.IO) {
        val encrypted = VaultCrypto.encrypt(secret, key)
        dao.upsert(VaultRecord(id = id ?: 0, nonce = encrypted.first, ciphertext = encrypted.second, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) { dao.delete(id) }
}

@Composable
fun VaultScreen(vm: VaultViewModel = viewModel()) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(window) {
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    var configured by remember { mutableStateOf(vm.isConfigured()) }
    var sessionKey by remember { mutableStateOf<ByteArray?>(null) }
    var items by remember { mutableStateOf<List<VaultListItem>>(emptyList()) }
    var unreadable by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultListItem?>(null) }
    var selected by remember { mutableStateOf<VaultListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultListItem?>(null) }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        sessionKey?.fill(0)
        sessionKey = null
        items = emptyList()
        selected = null
        editorOpen = false
        editing = null
        deleteTarget = null
    }
    LaunchedEffect(sessionKey, refresh) {
        val key = sessionKey
        if (key != null) {
            val result = vm.readAll(key)
            items = result.first
            unreadable = result.second
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
                    sessionKey = key
                    configured = true
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not open the vault. Please try again."
            } finally { busy = false }
        }
    }

    val key = sessionKey
    if (key == null) {
        VaultGate(configured, busy, error, onSubmit = { unlockOrCreate(it, !configured) })
    } else {
        VaultHome(
            items = items,
            unreadable = unreadable,
            onLock = {
                key.fill(0); sessionKey = null; items = emptyList(); selected = null
                editorOpen = false; editing = null; deleteTarget = null
            },
            onAdd = { editing = null; editorOpen = true },
            onSelect = { selected = it }
        )
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
private fun VaultGate(configured: Boolean, busy: Boolean, error: String?, onSubmit: (String) -> Unit) {
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
private fun VaultHome(items: List<VaultListItem>, unreadable: Int, onLock: () -> Unit, onAdd: () -> Unit, onSelect: (VaultListItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    val filtered = items.filter { item ->
        (type == "All" || (type == "Logins" && item.secret.type == "login") || (type == "Secure notes" && item.secret.type == "note")) &&
            (query.isBlank() || listOf(item.secret.title, item.secret.username, item.secret.website).any { it.contains(query, true) })
    }
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
            if (unreadable > 0) item {
                Text("$unreadable item${if (unreadable == 1) "" else "s"} could not be decrypted. Their encrypted data has not been changed.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
        }
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
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2, maxLines = 5, shape = RoundedCornerShape(14.dp))
        } else {
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Secure note") }, minLines = 5, maxLines = 12, shape = RoundedCornerShape(14.dp))
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        BigButton(if (initial == null) "Save securely" else "Save changes") {
            if (title.isBlank()) error = "Add a title to save this item."
            else if (type == "login" && password.isBlank()) error = "Add a password or generate one."
            else onSave(
                if (type == "login") VaultSecret(title.trim(), type, username.trim(), password, website.trim(), notes.trim())
                else VaultSecret(title.trim(), type, "", "", "", notes.trim())
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
