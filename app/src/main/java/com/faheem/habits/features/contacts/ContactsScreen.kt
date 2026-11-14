@file:Suppress("FunctionNaming")

package com.faheem.habits.features.contacts

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.faheem.habits.features.contacts.data.ContactRepository
import com.faheem.habits.core.data.Contact
import com.faheem.habits.core.ui.EmptyState
import com.faheem.habits.core.ui.FormSheet
import com.faheem.habits.core.ui.HeroCard
import com.faheem.habits.core.ui.MomentumDialog
import com.faheem.habits.core.ui.MomentumFab
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {
    val contacts = repository.contacts

    fun save(contact: Contact) = viewModelScope.launch {
        repository.save(contact)
    }

    fun delete(contact: Contact) = viewModelScope.launch {
        repository.delete(contact)
    }

    internal fun importContacts(rows: List<ImportedContact>, onComplete: (Int, Int, Int) -> Unit) =
        viewModelScope.launch {
            val result = repository.importContacts(rows)
            onComplete(result.imported, result.skippedContacts, result.duplicateNumbers)
        }
}

@Composable
fun ContactsScreen(vm: ContactsViewModel = hiltViewModel()) {
    val contacts by vm.contacts.collectAsStateWithLifecycle(
        initialValue = emptyList(),
        lifecycle = LocalLifecycleOwner.current.lifecycle
    )
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importScope.launch {
                runCatching {
                    val fileName = documentName(context, uri)
                    val imported = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { parseContacts(it, fileName) }
                            ?: error("Could not open the selected file.")
                    }
                    vm.importContacts(imported) { added, skippedContacts, duplicateNumbers ->
                        val details = buildList {
                            if (skippedContacts > 0) add("skipped $skippedContacts duplicate contact${if (skippedContacts == 1) "" else "s"}")
                            if (duplicateNumbers > 0) add("$duplicateNumbers duplicate number${if (duplicateNumbers == 1) "" else "s"} detected")
                        }.joinToString(" · ")
                        Toast.makeText(
                            context,
                            "Imported $added contact${if (added == 1) "" else "s"}${if (details.isNotBlank()) " · $details" else ""}.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "Could not import contacts.", Toast.LENGTH_LONG).show()
                }
            }
        }
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
    deleting?.let { contact ->
        MomentumDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${contact.name}?") },
            text = { Text("This contact will be removed from this device and synced as deleted.") },
            confirmButton = { TextButton(onClick = { vm.delete(contact); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
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
                    importPicker.launch(arrayOf(
                        "text/*",
                        "text/vcard",
                        "text/x-vcard",
                        "application/vcard",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel"
                    ))
                })
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.PersonAdd,
                        if (contacts.isEmpty()) "Your address book is empty" else "No matching contacts",
                        if (contacts.isEmpty()) {
                            "Add a contact to keep important names, phone numbers and email addresses together."
                        } else {
                            "Try another name, phone number or email address."
                        }
                    )
                    PrimaryButton("Add contact", onClick = { creating = true }, icon = Icons.Rounded.PersonAdd)
                }
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
                        }
                        IconButton(onClick = { deleting = contact }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${contact.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (contacts.isNotEmpty()) {
            MomentumFab("Add contact", onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding))
        }
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
                if (contact.profileImageUri.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(contact.profileImageUri))?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
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
                            runCatching { context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
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

private fun phoneNumbers(value: String): List<String> =
    value.split(Regex("[\\r\\n]+"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
