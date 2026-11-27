@file:Suppress("FunctionNaming")

package com.faheem.habits.features.contacts

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.Contact
import com.faheem.habits.core.data.ImagePreviewPolicy
import com.faheem.habits.core.data.decodeBoundedImage
import com.faheem.habits.core.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContactImportPhase { IDLE, READING, SAVING, SUCCESS, FAILURE }

data class ContactImportState(
    val phase: ContactImportPhase = ContactImportPhase.IDLE,
    val message: String? = null
)

internal enum class ContactPhotoLinkState { NONE, LINKED, TEMPORARY, UNAVAILABLE }

private const val IMPORT_FAILURE_MESSAGE = "No contacts were added. Check the file and try again."
private const val PHOTO_LINK_TEMPORARY_MESSAGE = "This photo is linked temporarily. Re-select it to keep access after a restart."
private const val PHOTO_LINK_UNAVAILABLE_MESSAGE = "This photo link is no longer available. Re-select the photo."

internal fun contactPhotoLinkState(context: Context, uriValue: String): ContactPhotoLinkState {
    if (uriValue.isBlank()) return ContactPhotoLinkState.NONE
    val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return ContactPhotoLinkState.UNAVAILABLE
    val persisted = try {
        uri.scheme != "content" || context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }
    } catch (_: Exception) {
        false
    }
    if (!persisted) return ContactPhotoLinkState.TEMPORARY
    val readable = try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
    return if (readable) ContactPhotoLinkState.LINKED else ContactPhotoLinkState.UNAVAILABLE
}

internal fun contactImportSummary(counts: ContactImportCounts): String = buildString {
    append("Imported ${counts.imported} contact${if (counts.imported == 1) "" else "s"}.")
    if (counts.skippedContacts > 0) {
        append(" Skipped ${counts.skippedContacts} duplicate contact${if (counts.skippedContacts == 1) "" else "s"}.")
    }
    if (counts.duplicateNumbers > 0) {
        append(" ${counts.duplicateNumbers} duplicate number${if (counts.duplicateNumbers == 1) "" else "s"} detected.")
    }
}

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.contactDao()
    val contacts = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(contact: Contact) = viewModelScope.launch {
        val normalized = contact.copy(name = contact.name.trim(), updatedAt = System.currentTimeMillis(), dirty = true)
        if (normalized.id == 0) dao.insert(normalized) else dao.update(normalized)
    }

    fun delete(contact: Contact, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        dao.softDelete(contact.id)
        onDeleted()
    }

    fun restore(contact: Contact) = viewModelScope.launch {
        dao.update(contact.copy(deleted = false, dirty = true, updatedAt = System.currentTimeMillis()))
    }

    private val _importState = MutableStateFlow(ContactImportState())
    val importState: StateFlow<ContactImportState> = _importState.asStateFlow()

    fun clearImportState() {
        _importState.value = ContactImportState()
    }

    fun importContacts(uri: Uri) {
        _importState.value = ContactImportState(ContactImportPhase.READING)
        viewModelScope.launch {
            try {
                val counts = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val rows = app.contentResolver.openInputStream(uri)?.use { input ->
                        parseContacts(input, documentName(app, uri))
                    } ?: throw IllegalArgumentException("Could not open the selected file.")
                    // Tombstones must block duplicate re-imports, so use the unfiltered DAO query.
                    val existing = dao.getAllForBackup()
                    val plan = planContactImport(existing, rows)
                    _importState.value = ContactImportState(ContactImportPhase.SAVING)
                    // Keep the batch atomic: a failed insert must not leave a partly imported address book.
                    persistContactImportPlan(db, dao, plan)
                    plan.counts
                }
                _importState.value = ContactImportState(ContactImportPhase.SUCCESS, contactImportSummary(counts))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _importState.value = ContactImportState(ContactImportPhase.FAILURE, IMPORT_FAILURE_MESSAGE)
            }
        }
    }
}

@Composable
fun ContactsScreen(vm: ContactsViewModel = viewModel()) {
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importContacts(uri)
    }
    val filtered = remember(contacts, query) {
        contacts.filter { query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) || it.email.contains(query, true) }
    }

    if (creating || editing != null) {
        ContactEditor(editing, onDismiss = { creating = false; editing = null }, onSave = {
            vm.save(it)
            creating = false
            editing = null
        })
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 8.dp, MomentumTokens.screenPadding, 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("Your address book", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f))
                    Text("${contacts.size} contact${if (contacts.size == 1) "" else "s"}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Save people, phone numbers, email addresses and notes.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f))
                }
            }
            item {
                MomentumTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search contacts") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
                )
            }
            item {
                SecondaryButton("Import VCF, CSV or Excel", onClick = {
                    vm.clearImportState()
                    importPicker.launch(arrayOf(
                        "text/*",
                        "text/vcard",
                        "text/x-vcard",
                        "application/vcard",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ))
                })
                when (importState.phase) {
                    ContactImportPhase.READING -> Text(
                        "Reading the selected file…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ContactImportPhase.SAVING -> Text(
                        "Checking for duplicates and saving…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ContactImportPhase.SUCCESS, ContactImportPhase.FAILURE -> {
                        PremiumCard(contentPadding = PaddingValues(12.dp)) {
                            Text(
                                importState.message.orEmpty(),
                                fontSize = 12.5.sp,
                                color = if (importState.phase == ContactImportPhase.FAILURE) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            TextButton(onClick = { vm.clearImportState() }) { Text("Dismiss") }
                        }
                    }
                    ContactImportPhase.IDLE -> Text(
                        "Imports are limited to 5,000 contacts and 10 MB. Existing and deleted contacts are checked for duplicates.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (filtered.isEmpty()) {
                item { ContactsEmptyState(noContacts = contacts.isEmpty(), onAdd = { creating = true }) }
            }
            items(filtered, key = { it.id }) { contact ->
                PremiumCard(onClick = { editing = contact }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(contact, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                phoneNumbers(contact.phone).joinToString(" · ").ifBlank { contact.email.ifBlank { "No phone or email" } },
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (contact.phone.isNotBlank() && contact.email.isNotBlank()) {
                                Text(contact.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ContactPhotoLinkWarning(contact.profileImageUri)
                        }
                        IconButton(onClick = {
                            vm.delete(contact) {
                                snackbarScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${contact.name} deleted",
                                        actionLabel = "Undo",
                                        withDismissAction = true
                                    )
                                    if (result == SnackbarResult.ActionPerformed) vm.restore(contact)
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${contact.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (contacts.isNotEmpty()) {
            MomentumFab("Add contact", onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        )
    }
}

@Composable
private fun ContactsEmptyState(noContacts: Boolean, onAdd: () -> Unit) {
    EmptyState(
        Icons.Rounded.PersonAdd,
        if (noContacts) "No contacts yet" else "No matching contacts",
        if (noContacts) "Add a contact to start your address book." else "Try a different search."
    )
    if (noContacts) {
        PrimaryButton("Add contact", onClick = onAdd, icon = Icons.Rounded.PersonAdd)
    }
}

@Composable
private fun ContactPhotoLinkWarning(uriValue: String) {
    if (uriValue.isBlank()) return
    val context = LocalContext.current
    val state by produceState(ContactPhotoLinkState.NONE, uriValue) {
        value = withContext(Dispatchers.IO) { contactPhotoLinkState(context, uriValue) }
    }
    if (state == ContactPhotoLinkState.TEMPORARY || state == ContactPhotoLinkState.UNAVAILABLE) {
        Text(
            if (state == ContactPhotoLinkState.UNAVAILABLE) "Photo link needs re-grant" else "Photo link is temporary",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private fun documentName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) return cursor.getString(column)
        }
    }
    return uri.lastPathSegment.orEmpty()
}

@Composable
private fun ContactAvatar(contact: Contact, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, contact.profileImageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (contact.profileImageUri.isBlank()) null else decodeBoundedImage(
                    context,
                    Uri.parse(contact.profileImageUri),
                    ImagePreviewPolicy.AVATAR_SOURCE_BYTES,
                    ImagePreviewPolicy.AVATAR_DECODE_DIMENSION
                )?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) Image(image!!, contact.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(contact.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContactEditor(initial: Contact?, onDismiss: () -> Unit, onSave: (Contact) -> Unit) {
    val context = LocalContext.current
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember(initial) { mutableStateOf(initial?.email.orEmpty()) }
    var address by remember(initial) { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var imageUri by remember(initial) { mutableStateOf(initial?.profileImageUri.orEmpty()) }
    var preview by remember { mutableStateOf(false) }
    var photoLinkState by remember(initial?.profileImageUri) {
        mutableStateOf(ContactPhotoLinkState.NONE)
    }
    LaunchedEffect(initial?.profileImageUri) {
        photoLinkState = withContext(Dispatchers.IO) {
            contactPhotoLinkState(context, initial?.profileImageUri.orEmpty())
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            } catch (_: Exception) {
                false
            }
            imageUri = uri.toString()
            photoLinkState = if (persisted || uri.scheme != "content") {
                ContactPhotoLinkState.LINKED
            } else {
                ContactPhotoLinkState.TEMPORARY
            }
        }
    }
    FormSheet(if (initial == null) "Add contact" else "Edit contact", onDismiss) {
        ContactAvatar(initial?.copy(name = name.ifBlank { "?" }, profileImageUri = imageUri) ?: Contact(name = name.ifBlank { "?" }, profileImageUri = imageUri), 72.dp) {
            if (imageUri.isNotBlank()) preview = true
        }
        if (preview && imageUri.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { preview = false },
                title = { Text("${name.ifBlank { "Contact" }} photo") },
                text = {
                    val image by produceState<ImageBitmap?>(null, imageUri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching { decodeBoundedImage(context, Uri.parse(imageUri))?.asImageBitmap() }.getOrNull()
                        }
                    }
                    if (image != null) Image(image!!, null, Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit)
                    else Text("This image can’t be previewed.")
                },
                confirmButton = { TextButton(onClick = { preview = false }) { Text("Close") } }
            )
        }
        MomentumTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        MomentumTextField(
            phone,
            { phone = it },
            label = { Text("Phone numbers (one per line)") },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        MomentumTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
        MomentumTextField(address, { address = it }, label = { Text("Address") }, singleLine = true)
        MomentumTextField(notes, { notes = it }, label = { Text("Notes") }, singleLine = false)
        SecondaryButton(if (imageUri.isBlank()) "Add contact photo" else "Change contact photo", onClick = { picker.launch(arrayOf("image/*")) }, icon = Icons.Rounded.AddPhotoAlternate)
        if (photoLinkState == ContactPhotoLinkState.TEMPORARY || photoLinkState == ContactPhotoLinkState.UNAVAILABLE) {
            Text(
                if (photoLinkState == ContactPhotoLinkState.UNAVAILABLE) {
                    PHOTO_LINK_UNAVAILABLE_MESSAGE
                } else {
                    PHOTO_LINK_TEMPORARY_MESSAGE
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        PrimaryButton("Save contact", onClick = {
            if (name.isNotBlank()) onSave(
                (initial ?: Contact(name = name)).copy(
                    name = name.trim(), phone = phoneNumbers(phone).joinToString("\n"), email = email.trim(), address = address.trim(),
                    notes = notes.trim(), profileImageUri = imageUri
                )
            )
        })
    }
}
