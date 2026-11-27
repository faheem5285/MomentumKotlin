package com.faheem.habits.features.files
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Intent
import android.graphics.Bitmap
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
import com.faheem.habits.features.files.data.FileAddResult
import com.faheem.habits.features.files.data.FileLinkDraft
import com.faheem.habits.features.files.data.FileRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _addResult = MutableStateFlow<FileAddResult?>(null)
    val addResult: StateFlow<FileAddResult?> = _addResult.asStateFlow()

    fun clearAddResult() {
        _addResult.value = null
    }

    fun reportAccessDenied() {
        _addResult.value = FileAddResult.ACCESS_DENIED
    }

    fun add(draft: FileLinkDraft) {
        viewModelScope.launch { _addResult.value = repository.add(draft) }
    }

    fun reconnect(file: FileItem, draft: FileLinkDraft) {
        viewModelScope.launch { _addResult.value = repository.reconnect(file, draft) }
    }

    fun markAccessed(file: FileItem) {
        viewModelScope.launch { repository.markAccessed(file) }
    }

    fun markMissing(file: FileItem) {
        viewModelScope.launch { repository.markMissing(file) }
    }

    fun markAvailable(file: FileItem) {
        viewModelScope.launch { repository.markAvailable(file) }
    }

    fun remove(file: FileItem) { viewModelScope.launch { repository.delete(file) } }
}

internal fun fileAddMessage(result: FileAddResult): String = when (result) {
    FileAddResult.LINKED -> "File linked from its provider."
    FileAddResult.TEMPORARY_LINK -> "File linked, but access is temporary. Re-select it after a restart if it stops working."
    FileAddResult.TOO_LARGE -> "This file is over 10 MB and was not linked."
    FileAddResult.ACCESS_DENIED -> "Access was denied. The file was not linked; select it again to grant access."
    FileAddResult.INVALID_FILE -> "That file could not be identified. Nothing was linked."
    FileAddResult.FAILED -> "The file could not be linked. Nothing was changed."
}

internal data class FileMetadata(val name: String, val sizeBytes: Long?)

internal fun fileMetadata(context: android.content.Context, uri: Uri): FileMetadata {
    var name = "File"
    var sizeBytes: Long? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0) {
                    val size = cursor.getLong(sizeColumn)
                    if (size >= 0) sizeBytes = size
                }
            }
        }
    } catch (_: Exception) {
        return FileMetadata("File", null)
    }
    return FileMetadata(name, sizeBytes)
}

internal fun hasPersistedReadPermission(context: android.content.Context, uri: Uri): Boolean = try {
    context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
} catch (_: Exception) {
    false
}

@Composable
internal fun FileAddStatusCard(result: FileAddResult, onDismiss: () -> Unit) {
    PremiumCard(contentPadding = PaddingValues(12.dp)) {
        Text(
            fileAddMessage(result),
            fontSize = 12.5.sp,
            color = if (
                result == FileAddResult.TOO_LARGE ||
                result == FileAddResult.ACCESS_DENIED ||
                result == FileAddResult.INVALID_FILE ||
                result == FileAddResult.FAILED
            ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

private val badgeColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
private enum class FileKind(val label: String) { All("All"), Image("Images"), Pdf("PDFs"), Text("Text"), Other("Other") }

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")
private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "html", "css", "js", "kt", "java", "log", "yaml", "yml", "properties")

private fun fileKind(file: FileItem): FileKind {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mime.startsWith("image/") || ext in IMAGE_EXTENSIONS -> FileKind.Image
        file.mime == "application/pdf" || ext == "pdf" -> FileKind.Pdf
        file.mime.startsWith("text/") || ext in TEXT_EXTENSIONS -> FileKind.Text
        else -> FileKind.Other
    }
}

@Composable
fun FilesScreen(vm: FileViewModel = hiltViewModel()) {
    val files by vm.files.collectAsStateWithLifecycle()
    val addResult by vm.addResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FileKind.All) }
    var preview by remember { mutableStateOf<FileItem?>(null) }
    var removeTarget by remember { mutableStateOf<FileItem?>(null) }
    var reconnectTarget by remember { mutableStateOf<FileItem?>(null) }
    var pendingText by remember { mutableStateOf("") }
    var folderPath by remember { mutableStateOf("") }
    var editingFolder by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.clearAddResult()
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                hasPersistedReadPermission(context, uri)
            } catch (_: Exception) {
                false
            }
            val metadata = fileMetadata(context, uri)
            vm.add(
                FileLinkDraft(
                    name = metadata.name,
                    uri = uri.toString(),
                    mime = context.contentResolver.getType(uri) ?: "*/*",
                    folder = folderPath,
                    sizeBytes = metadata.sizeBytes,
                    persisted = persisted || uri.scheme != "content"
                )
            )
        }
    }
    val reconnectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = reconnectTarget
        reconnectTarget = null
        if (uri != null && target != null) {
            vm.clearAddResult()
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                hasPersistedReadPermission(context, uri)
            } catch (_: Exception) {
                false
            }
            val metadata = fileMetadata(context, uri)
            vm.reconnect(
                target,
                FileLinkDraft(
                    name = metadata.name,
                    uri = uri.toString(),
                    mime = context.contentResolver.getType(uri) ?: target.mime,
                    folder = target.folder,
                    sizeBytes = metadata.sizeBytes,
                    persisted = persisted || uri.scheme != "content"
                )
            )
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

    LaunchedEffect(preview?.id, preview?.uri) {
        val target = preview ?: return@LaunchedEffect
        val readable = withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(target.uri) }.getOrNull()
            if (uri == null) false else try {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
        if (!readable && !target.missing) vm.markMissing(target)
        if (readable && target.missing) vm.markAvailable(target)
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
            },
            onReconnect = {
                reconnectTarget = file
                reconnectPicker.launch(arrayOf("*/*"))
            }
        )
    }
    removeTarget?.let { file ->
        MomentumDialog(
            onDismissRequest = { removeTarget = null }, title = { Text("Remove from library?") },
            text = { Text("Zylo will remove the link from your library. The original file stays with its provider and is not deleted.") },
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
    val shown = remember(files, kind, folderPath, query) {
        files.filter {
            (kind == FileKind.All || fileKind(it) == kind) &&
                (folderPath.isBlank() || it.folder == folderPath) &&
                it.name.contains(query, ignoreCase = true)
        }
    }
    val fileMixSlices = remember(files) {
        files.groupingBy { fileKind(it) }.eachCount()
            .filterKeys { it != FileKind.All }
            .entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry -> ChartSlice(entry.key.label, entry.value.toFloat(), chartColor(index)) }
    }
    val additions14d = remember(files) {
        val today = LocalDate.now()
        (13 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong()).toEpochDay()
            files.count { it.addedEpochDay == day }.toFloat()
        }
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
                            HeroCaption("Your file space")
                            Text(
                                "${files.size} file${if (files.size == 1) "" else "s"}",
                                fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Files stay with their provider. Zylo stores a link so you can preview and organise them here.",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
            addResult?.let { result ->
                item {
                    FileAddStatusCard(result = result, onDismiss = { vm.clearAddResult() })
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
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = fileMixSlices,
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
                                    slices = fileMixSlices,
                                    maxRows = 4,
                                    valueFormatter = { "${it.toInt()}" }
                                )
                            }
                        }
                    }
                }
                item {
                    if (additions14d.any { it > 0f }) {
                        val today = LocalDate.now()
                        PremiumCard(contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.Insights, MaterialTheme.colorScheme.primary, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Additions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${additions14d.sumOf { it.toInt() }} files added in 14 days",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            BarChart(
                                values = additions14d,
                                labels = (13 downTo 0).map { offset ->
                                    val day = today.minusDays(offset.toLong())
                                    if (offset % 2 == 0) "${day.dayOfMonth}" else ""
                                },
                                colors = listOf(MaterialTheme.colorScheme.secondary),
                                highlightIndex = additions14d.lastIndex,
                                height = 132.dp,
                                showValues = false
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    "${shown.size} file${if (shown.size == 1) "" else "s"}",
                    subtitle = "Linked from your device · Zylo does not store a copy"
                )
            }
            if (shown.isEmpty()) {
                item { EmptyState(Icons.Rounded.FolderOpen, if (files.isEmpty()) "Your library is empty" else "No matching files", if (files.isEmpty()) "Add a file to preview and manage it here" else "Try another search or file type") }
            }
            items(shown, key = { it.id }) { file ->
                val ext = file.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "FILE" }
                val badge = badgeColors[Math.floorMod(ext.hashCode(), badgeColors.size)]
                PremiumCard(
                    onClick = {
                        preview = file
                        vm.markAccessed(file)
                    },
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
                            val details = buildList {
                                add("${fileKind(file).label} · Added ${dayText(file.addedEpochDay)}")
                                file.folder.takeIf(String::isNotBlank)?.let { add("Folder: $it") }
                                file.tags.takeIf(String::isNotBlank)?.let { add("Tags: $it") }
                                if (file.lastAccessedEpochDay > 0) add("Opened ${dayText(file.lastAccessedEpochDay)}")
                                if (file.missing) add("Link needs re-grant")
                            }
                            Text(
                                details.joinToString(" · "),
                                fontSize = 12.sp,
                                maxLines = 3,
                                color = if (file.missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePreviewSheet(
    file: FileItem,
    onDismiss: () -> Unit,
    onSaveCopy: (String) -> Unit,
    onReconnect: () -> Unit
) {
    val glass = LocalGlassPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glass.panel,
        scrimColor = MaterialTheme.colorScheme.scrim,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(file.mime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close preview") }
            }
            Spacer(Modifier.height(8.dp))
            if (file.missing) {
                PremiumCard(contentPadding = PaddingValues(12.dp)) {
                    Text(
                        "This link is no longer available. Re-select the file to grant access again.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onReconnect) { Text("Re-select file") }
                }
                Spacer(Modifier.height(8.dp))
            }
            val metadata = buildList {
                file.folder.takeIf(String::isNotBlank)?.let { add("Folder: $it") }
                file.tags.takeIf(String::isNotBlank)?.let { add("Tags: $it") }
                if (file.lastAccessedEpochDay > 0) add("Opened ${dayText(file.lastAccessedEpochDay)}")
            }.joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(metadata, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            when (fileKind(file)) {
                FileKind.Image -> ImageFilePreview(file)
                FileKind.Pdf -> PdfFilePreview(file)
                FileKind.Text -> TextFilePreview(file, onSaveCopy)
                FileKind.Other -> UnsupportedFilePreview(file)
                FileKind.All -> Unit
            }
        }
    }
}

@Composable
private fun ImageFilePreview(file: FileItem) {
    val context = LocalContext.current
    val state by produceState<Pair<Boolean, Bitmap?>>(false to null, file.uri) {
        value = true to withContext(Dispatchers.IO) { decodeImage(context, Uri.parse(file.uri)) }
    }
    if (state.second == null) PreviewMessage(if (state.first) "Could not preview this image" else "Opening image…", "The file may be unavailable or too large to decode.")
    else Image(state.second!!.asImageBitmap(), contentDescription = file.name, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 620.dp).padding(16.dp), contentScale = ContentScale.Fit)
}

private fun decodeImage(context: android.content.Context, uri: Uri): Bitmap? =
    decodeBoundedImage(context, uri)

@Composable
private fun PdfFilePreview(file: FileItem) {
    val context = LocalContext.current
    var page by remember(file.uri) { mutableIntStateOf(0) }
    val rendered by produceState<PdfPage?>(null, file.uri, page) {
        value = withContext(Dispatchers.IO) { renderPdfPage(context, Uri.parse(file.uri), page) ?: PdfPage(-1, null) }
    }
    if (rendered == null) {
        PreviewMessage("Opening PDF…", "PDF pages are rendered on your device.")
    } else if (rendered!!.pageCount <= 0 || rendered!!.bitmap == null) {
        PreviewMessage("Could not preview this PDF", "The file may be unavailable or protected.")
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, "Previous page") }
                Text("Page ${page + 1} of ${rendered!!.pageCount}", fontWeight = FontWeight.Medium)
                IconButton(onClick = { page = (page + 1).coerceAtMost(rendered!!.pageCount - 1) }, enabled = page + 1 < rendered!!.pageCount) { Icon(Icons.Rounded.ChevronRight, "Next page") }
            }
            Image(rendered!!.bitmap!!.asImageBitmap(), contentDescription = "PDF page ${page + 1}", modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), contentScale = ContentScale.Fit)
        }
    }
}

private data class PdfPage(val pageCount: Int, val bitmap: Bitmap?)

private fun renderPdfPage(context: android.content.Context, uri: Uri, index: Int): PdfPage? {
    return try {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return PdfPage(0, null)
                val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = ImagePreviewPolicy.pdfScale(page.width, page.height)
                    val (width, height) = ImagePreviewPolicy.pdfDimensions(page.width, page.height, scale)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    PdfPage(renderer.pageCount, bitmap)
                }
            }
        }
    } catch (_: FileNotFoundException) { null } catch (_: Exception) { null }
}

@Composable
private fun TextFilePreview(file: FileItem, onSaveCopy: (String) -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlassPalette.current
    var editing by remember(file.uri) { mutableStateOf(false) }
    var text by remember(file.uri) { mutableStateOf<String?>(null) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(file.uri) {
        val result = withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(Uri.parse(file.uri))?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buffer = CharArray(8192)
                    val output = StringBuilder()
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (output.length + count > 1024 * 1024) throw IllegalStateException("This editor supports files up to 1 MB.")
                        output.append(buffer, 0, count)
                    }
                    output.toString()
                } ?: throw FileNotFoundException()
                content to null
            } catch (_: Exception) {
                null to "This file could not be read. It may be unavailable or too large to preview."
            }
        }
        text = result.first
        error = result.second
    }
    if (text == null) {
        PreviewMessage(if (error == null) "Loading text…" else "Unable to read this file", error ?: "Text files up to 1 MB can be previewed and edited.")
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Edit a copy" else "Text preview", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { if (editing) onSaveCopy(text ?: "") else editing = true }) { Text(if (editing) "Save copy" else "Edit") }
            }
            if (editing) {
                MomentumTextField(
                    value = text ?: "",
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    minLines = 6
                )
                Text("The original stays unchanged. Save creates an edited copy.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(MomentumTokens.radiusSmall)).background(glass.chip).padding(14.dp)) {
                    Text(text ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("Text files up to 1 MB can be edited. Save creates a separate copy.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UnsupportedFilePreview(file: FileItem) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Preview not available yet", fontWeight = FontWeight.SemiBold)
        Text("${file.name} is linked in your library. In-app preview supports images, PDFs and common text files.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewMessage(title: String, message: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 620.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (title.startsWith("Opening") || title.startsWith("Loading")) CircularProgressIndicator(Modifier.size(30.dp))
        else Icon(Icons.Rounded.WarningAmber, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
