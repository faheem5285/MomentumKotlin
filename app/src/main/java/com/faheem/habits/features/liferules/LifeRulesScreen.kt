@file:Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")

package com.faheem.habits.features.liferules

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.faheem.habits.core.data.LifeRule
import com.faheem.habits.core.data.LifeRuleFile
import com.faheem.habits.core.ui.EmptyState
import com.faheem.habits.core.ui.FormSheet
import com.faheem.habits.core.ui.HeroCard
import com.faheem.habits.core.ui.IconBubble
import com.faheem.habits.core.ui.MomentumChip
import com.faheem.habits.core.ui.MomentumDialog
import com.faheem.habits.core.ui.MomentumFab
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SecondaryButton
import com.faheem.habits.core.ui.SectionHeader
import com.faheem.habits.core.ui.StatusPill
import com.faheem.habits.features.liferules.data.LifeRuleRepository

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val KIND_RULE = "Rule"
private const val KIND_HABIT = "Habit"
private const val KIND_QUOTE = "Quote"

private val lifeRuleKinds = listOf(KIND_RULE, KIND_HABIT, KIND_QUOTE)
private val lifeRuleFilters = listOf("All", "Rules", "Habits", "Quotes", "Pinned")

private fun kindColor(kind: String): Color = when (kind) {
    KIND_HABIT -> Color(0xFF10B981)
    KIND_QUOTE -> Color(0xFF8B5CF6)
    else -> Color(0xFFF59E0B)
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    KIND_HABIT -> Icons.Rounded.Lightbulb
    KIND_QUOTE -> Icons.Rounded.FormatQuote
    else -> Icons.AutoMirrored.Rounded.MenuBook
}

@HiltViewModel
class LifeRulesViewModel @Inject constructor(
    private val repository: LifeRuleRepository
) : ViewModel() {

    val rules = repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        rule: LifeRule,
        files: List<LifeRuleFile> = emptyList(),
        removedFiles: List<LifeRuleFile> = emptyList()
    ) = viewModelScope.launch {
        val savedId = repository.save(
            rule.copy(
                title = rule.title.trim(),
                body = rule.body.trim(),
                createdEpochDay = if (rule.id == 0) LocalDate.now().toEpochDay() else rule.createdEpochDay,
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        )
        files.filter { it.id == 0 }.forEach { repository.addFile(it.copy(ruleId = savedId)) }
        removedFiles.forEach { repository.deleteFile(it) }
    }

    fun files(ruleId: Int) = repository.files(ruleId)
    fun togglePin(rule: LifeRule) = viewModelScope.launch {
        repository.save(
            rule.copy(
                pinned = !rule.pinned,
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        )
    }

    fun delete(rule: LifeRule) = viewModelScope.launch { repository.delete(rule) }
}

@Composable
fun LifeRulesScreen(vm: LifeRulesViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LifeRule?>(null) }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LifeRule?>(null) }

    val pinnedCount = rules.count { it.pinned }
    val filtered = rules.filter { rule ->
        val matchesFilter = when (filter) {
            "Rules" -> rule.kind == KIND_RULE
            "Habits" -> rule.kind == KIND_HABIT
            "Quotes" -> rule.kind == KIND_QUOTE
            "Pinned" -> rule.pinned
            else -> true
        }
        matchesFilter && (rule.title.contains(query, true) || rule.body.contains(query, true))
    }

    deleting?.let { rule ->
        MomentumDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("\"${rule.title}\" will be permanently removed from your life rules.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(rule); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { LifeRulesHero(rules, pinnedCount) }
            item {
                MomentumTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Search life entries",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lifeRuleFilters.forEach { label ->
                        MomentumChip(selected = filter == label, onClick = { filter = label }, label = label)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        if (rules.isEmpty()) "Write your own philosophy" else "Nothing matches",
                        if (rules.isEmpty()) {
                            "Save the rules you live by, the habits that work for you and the quotes that keep you going."
                        } else {
                            "Try another filter or search term."
                        }
                    )
                }
            }
            items(filtered, key = { it.id }) { rule ->
                LifeRuleCard(
                    rule = rule,
                    onClick = { editing = rule },
                    onTogglePin = { vm.togglePin(rule) },
                    onDelete = { deleting = rule }
                )
            }
            if (rules.isNotEmpty()) {
                item {
                    Text(
                        "Your life rules are stored on this device.",
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        MomentumFab(
            text = "Add entry",
            onClick = { creating = true },
            icon = Icons.Rounded.Add,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
        )
    }

    if (creating) {
        LifeRuleEditorSheet(
            initial = null,
            onDismiss = { creating = false },
            onSave = { rule, files, removedFiles ->
                vm.save(rule, files, removedFiles)
                creating = false
            }
        )
    }
    editing?.let { rule ->
        val files by vm.files(rule.id).collectAsStateWithLifecycle(initialValue = emptyList())
        LifeRuleEditorSheet(
            initial = rule,
            initialFiles = files,
            onDismiss = { editing = null },
            onSave = { updatedRule, addedFiles, removedFiles ->
                vm.save(updatedRule, addedFiles, removedFiles)
                editing = null
            }
        )
    }
}

@Composable
private fun LifeRulesHero(rules: List<LifeRule>, pinnedCount: Int) {
    HeroCard {
        Text("The philosophy you choose", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
        Text(
            if (rules.isEmpty()) "Start with one line" else "${rules.size} entr${if (rules.size == 1) "y" else "ies"} kept",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifePill(Icons.AutoMirrored.Rounded.MenuBook, "${rules.count { it.kind == KIND_RULE }} rules")
            LifePill(Icons.Rounded.Lightbulb, "${rules.count { it.kind == KIND_HABIT }} habits")
            LifePill(Icons.Rounded.FormatQuote, "${rules.count { it.kind == KIND_QUOTE }} quotes")
        }
        if (pinnedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PushPin, null, Modifier.size(13.dp), tint = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.width(6.dp))
                Text(
                    "$pinnedCount pinned to the top",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun LifePill(icon: ImageVector, text: String) {
    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White.copy(alpha = 0.16f)) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
            Spacer(Modifier.width(5.dp))
            Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@Composable
private fun LifeRuleCard(
    rule: LifeRule,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = kindColor(rule.kind)
    PremiumCard(
        onClick = onClick,
        accent = if (rule.pinned) accent else null,
        contentPadding = PaddingValues(start = 13.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(kindIcon(rule.kind), accent, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (rule.kind == KIND_QUOTE) {
                    Text(
                        "“${rule.title}”",
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.body.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "— ${rule.body}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        rule.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.body.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            rule.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(rule.kind, accent)
                    if (rule.pinned) {
                        Icon(Icons.Rounded.PushPin, null, Modifier.size(13.dp), tint = accent)
                    }
                }
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (rule.pinned) "Unpin entry" else "Pin entry",
                    tint = if (rule.pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LifeRuleEditorSheet(
    initial: LifeRule?,
    initialFiles: List<LifeRuleFile> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (LifeRule, List<LifeRuleFile>, List<LifeRuleFile>) -> Unit
) {
    val context = LocalContext.current
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: KIND_RULE) }
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    var pinned by remember(initial?.id) { mutableStateOf(initial?.pinned ?: false) }
    var folderPath by remember(initial?.id) { mutableStateOf("") }
    var attachments by remember(initial?.id, initialFiles) { mutableStateOf(initialFiles) }
    var removedAttachments by remember(initial?.id) { mutableStateOf(emptyList<LifeRuleFile>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val selectedFiles = buildList {
            uris.forEach { uri ->
                val name = documentName(context, uri)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    add(
                        LifeRuleFile(
                            ruleId = initial?.id ?: 0,
                            name = name,
                            uri = uri.toString(),
                            mime = context.contentResolver.getType(uri).orEmpty(),
                            folderPath = folderPath.trim('/')
                        )
                    )
                } catch (error: SecurityException) {
                    Toast.makeText(
                        context,
                        "Could not keep access to $name: ${error.message ?: "permission denied"}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (error: UnsupportedOperationException) {
                    Toast.makeText(
                        context,
                        "This file provider does not support persistent access: $name",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        attachments = (attachments + selectedFiles).distinctBy { it.uri }
    }

    val titleLabel = when (kind) {
        KIND_HABIT -> "Best habit"
        KIND_QUOTE -> "Quote"
        else -> "Life rule"
    }
    val bodyLabel = when (kind) {
        KIND_HABIT -> "How you practise it"
        KIND_QUOTE -> "Author or source"
        else -> "Why it matters"
    }

    FormSheet(if (initial == null) "New life entry" else "Edit life entry", onDismiss) {
        SectionHeader("Kind")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lifeRuleKinds.forEach { value ->
                MomentumChip(
                    selected = kind == value,
                    onClick = { kind = value },
                    label = value,
                    icon = kindIcon(value)
                )
            }
        }
        MomentumTextField(
            value = folderPath,
            onValueChange = { folderPath = it },
            label = { Text("Folder / subfolder (optional)") },
            placeholder = { Text("Personal/Values") },
            singleLine = true
        )
        SecondaryButton(
            text = "Choose files",
            onClick = { picker.launch(arrayOf("*/*")) },
            icon = Icons.Rounded.AttachFile
        )
        attachments.forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(file.uri), file.mime.ifBlank { "*/*" })
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        } catch (error: ActivityNotFoundException) {
                            Toast.makeText(context, "No app can open ${file.name}.", Toast.LENGTH_LONG).show()
                        } catch (error: SecurityException) {
                            Toast.makeText(context, "Access to ${file.name} is no longer available.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    attachments = attachments - file
                    if (file.id != 0) removedAttachments = removedAttachments + file
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${file.name}")
                }
            }
        }
        MomentumTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(titleLabel) },
            minLines = if (kind == KIND_QUOTE) 2 else 1,
            maxLines = 5
        )
        MomentumTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(bodyLabel) },
            minLines = 2,
            maxLines = 6
        )
        SectionHeader("Priority")
        MomentumChip(
            selected = pinned,
            onClick = { pinned = !pinned },
            label = if (pinned) "Pinned to the top" else "Pin to the top",
            icon = Icons.Rounded.PushPin
        )
        Text(
            when (kind) {
                KIND_QUOTE -> "Keep a line that reminds you how to spend your life."
                KIND_HABIT -> "Describe the small habit that keeps your days working."
                else -> "Write a rule you want to live by, in your own words."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(
            text = if (initial == null) "Save entry" else "Save changes",
            enabled = title.isNotBlank(),
            onClick = {
                if (title.isNotBlank()) {
                    onSave(
                        (initial ?: LifeRule(title = "")).copy(
                            title = title,
                            body = body,
                            kind = kind,
                            pinned = pinned
                        ),
                        attachments,
                        removedAttachments
                    )
                }
            }
        )
    }
}

private fun documentName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameColumn)?.takeIf(String::isNotBlank)?.let { return it }
        }
    }
    return Uri.decode(uri.lastPathSegment?.substringAfterLast(':').orEmpty()).ifBlank { "Attached file" }
}
