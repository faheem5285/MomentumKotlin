package com.faheem.habits.features.files
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import com.faheem.habits.features.files.data.FileRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

@HiltViewModel
class FileViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {
    val files: StateFlow<List<FileItem>> = repository.files.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(name: String, uri: String, mime: String, folder: String = "") {
        viewModelScope.launch { repository.add(name, uri, mime, folder) }
    }
    fun remove(file: FileItem) { viewModelScope.launch { repository.delete(file) } }
}

private val badgeColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
internal enum class FileKind(val label: String) { All("All"), Image("Images"), Pdf("PDFs"), Text("Text"), Other("Other") }

internal fun fileKind(file: FileItem): FileKind {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic") -> FileKind.Image
        file.mime == "application/pdf" || ext == "pdf" -> FileKind.Pdf
        file.mime.startsWith("text/") || ext in setOf("txt", "md", "csv", "json", "xml", "html", "css", "js", "kt", "java", "log", "yaml", "yml", "properties") -> FileKind.Text
        else -> FileKind.Other
    }
}

@Composable
fun FilesScreen(vm: FileViewModel = hiltViewModel()) {
    val files by vm.files.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FileKind.All) }
    var preview by remember { mutableStateOf<FileItem?>(null) }
    var removeTarget by remember { mutableStateOf<FileItem?>(null) }
    var pendingText by remember { mutableStateOf("") }
    var folderPath by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            var name = "File"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) name = cursor.getString(column) ?: name
                }
            }
            vm.add(name, uri.toString(), context.contentResolver.getType(uri) ?: "*/*", folderPath)
        }
    }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                (context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("No output stream")).use { it.write(pendingText.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "Edited copy saved", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not save this copy", Toast.LENGTH_LONG).show()
            }
        }
    }

    preview?.let { file ->
        FilePreviewSheet(
            file = file,
            onDismiss = { preview = null },
            onSaveCopy = { text ->
                pendingText = text
                val base = file.name.substringBeforeLast('.', file.name)
                val ext = file.name.substringAfterLast('.', "txt").ifBlank { "txt" }
                saveCopy.launch("${base}_edited.$ext")
            }
        )
    }
    removeTarget?.let { file ->
        MomentumDialog(
            onDismissRequest = { removeTarget = null }, title = { Text("Remove from library?") },
            text = { Text("${file.name} will be removed from Zylo. The original file will stay on your device.") },
            confirmButton = { TextButton(onClick = { vm.remove(file); removeTarget = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    if (editingFolder) {
        AlertDialog(
            onDismissRequest = { editingFolder = false },
            title = { Text("Create folder or subfolder") },
            text = {
                MomentumTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Path (example: Work/Receipts)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { folderPath = folderPath.trim('/'); editingFolder = false }) { Text("Use folder") }
            },
            dismissButton = { TextButton(onClick = { editingFolder = false }) { Text("Cancel") } }
        )
    }
    val shown = files.filter {
        (kind == FileKind.All || fileKind(it) == kind) &&
            (folderPath.isBlank() || it.folder == folderPath) &&
            it.name.contains(query, ignoreCase = true)
    }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(26.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your file space", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                            Text(
                                "${files.size} file${if (files.size == 1) "" else "s"}",
                                fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Preview images, PDFs and text files here. Edit text and save a copy.",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
            item {
                MomentumTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search your files") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MomentumChip(
                        selected = folderPath.isBlank(),
                        onClick = { folderPath = "" },
                        label = "All folders"
                    )
                    MomentumChip(
                        selected = folderPath.isNotBlank(),
                        onClick = { editingFolder = true },
                        label = folderPath.ifBlank { "Create folder" },
                        icon = Icons.Rounded.CreateNewFolder
                    )
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileKind.entries.forEach { type ->
                        MomentumChip(selected = kind == type, onClick = { kind = type }, label = type.label)
                    }
                }
            }
            if (files.isNotEmpty()) {
                item {
                    val counts = files.groupingBy { fileKind(it) }.eachCount()
                        .filterKeys { it != FileKind.All }
                        .entries
                        .sortedByDescending { it.value }
                    val slices = counts.mapIndexed { index, entry ->
                        ChartSlice(entry.key.label, entry.value.toFloat(), chartColor(index))
                    }
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = slices,
                                modifier = Modifier.width(146.dp),
                                centerValue = "${files.size}",
                                centerLabel = "files",
                                thickness = 18.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Library mix", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                ChartLegend(
                                    slices = slices,
                                    maxRows = 4,
                                    valueFormatter = { "${it.toInt()}" }
                                )
                            }
                        }
                    }
                }
                item {
                    val today = LocalDate.now()
                    val additions = (13 downTo 0).map { offset ->
                        val day = today.minusDays(offset.toLong()).toEpochDay()
                        files.count { it.addedEpochDay == day }.toFloat()
                    }
                    if (additions.any { it > 0f }) {
                        PremiumCard(contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.Insights, MaterialTheme.colorScheme.primary, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Additions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${additions.sumOf { it.toInt() }} files added in 14 days",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            BarChart(
                                values = additions,
                                labels = (13 downTo 0).map { offset ->
                                    val day = today.minusDays(offset.toLong())
                                    if (offset % 2 == 0) "${day.dayOfMonth}" else ""
                                },
                                colors = listOf(MaterialTheme.colorScheme.secondary),
                                highlightIndex = additions.lastIndex,
                                height = 132.dp,
                                showValues = false
                            )
                        }
                    }
                }
            }
            item { SectionHeader("${shown.size} file${if (shown.size == 1) "" else "s"}", subtitle = "Stored on this device") }
            if (shown.isEmpty()) {
                item { EmptyState(Icons.Rounded.FolderOpen, if (files.isEmpty()) "Your library is empty" else "No matching files", if (files.isEmpty()) "Add a file to preview and manage it here" else "Try another search or file type") }
            }
            items(shown, key = { it.id }) { file ->
                val ext = file.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "FILE" }
                val badge = badgeColors[Math.floorMod(ext.hashCode(), badgeColors.size)]
                PremiumCard(
                    onClick = { preview = file },
                    contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(badge.copy(alpha = .13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (fileKind(file)) {
                                    FileKind.Image -> Icons.Rounded.Image
                                    FileKind.Pdf -> Icons.Rounded.PictureAsPdf
                                    FileKind.Text -> Icons.Rounded.Description
                                    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                },
                                null,
                                tint = badge
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${fileKind(file).label} · Added ${dayText(file.addedEpochDay)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { removeTarget = file }) { Icon(Icons.Rounded.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        MomentumFab(
            text = "Add file",
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
        )
    }
}

