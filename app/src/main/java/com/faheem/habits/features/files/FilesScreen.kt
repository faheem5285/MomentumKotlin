package com.faheem.habits.features.files
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class FileViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).fileDao()
    val files: StateFlow<List<FileItem>> = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(name: String, uri: String, mime: String) {
        viewModelScope.launch { dao.insert(FileItem(name = name, uri = uri, mime = mime, addedEpochDay = today())) }
    }
    fun remove(file: FileItem) { viewModelScope.launch { dao.delete(file) } }
}

private val badgeColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899))
private enum class FileKind(val label: String) { All("All"), Image("Images"), Pdf("PDFs"), Text("Text"), Other("Other") }

private fun fileKind(file: FileItem): FileKind {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic") -> FileKind.Image
        file.mime == "application/pdf" || ext == "pdf" -> FileKind.Pdf
        file.mime.startsWith("text/") || ext in setOf("txt", "md", "csv", "json", "xml", "html", "css", "js", "kt", "java", "log", "yaml", "yml", "properties") -> FileKind.Text
        else -> FileKind.Other
    }
}

@Composable
fun FilesScreen(vm: FileViewModel = viewModel()) {
    val files by vm.files.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FileKind.All) }
    var preview by remember { mutableStateOf<FileItem?>(null) }
    var removeTarget by remember { mutableStateOf<FileItem?>(null) }
    var pendingText by remember { mutableStateOf("") }

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
            vm.add(name, uri.toString(), context.contentResolver.getType(uri) ?: "*/*")
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
        AlertDialog(
            onDismissRequest = { removeTarget = null }, title = { Text("Remove from library?") },
            text = { Text("${file.name} will be removed from Momentum. The original file will stay on your device.") },
            confirmButton = { TextButton(onClick = { vm.remove(file); removeTarget = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    val shown = files.filter { (kind == FileKind.All || fileKind(it) == kind) && it.name.contains(query, ignoreCase = true) }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("Your file space", fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                    Text("${files.size} file${if (files.size == 1) "" else "s"}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Preview images, PDFs and text files here. Edit text and save a copy.", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search your files") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileKind.entries.forEach { type -> FilterChip(selected = kind == type, onClick = { kind = type }, label = { Text(type.label) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FileKind.Image to "Images",
                        FileKind.Pdf to "PDFs",
                        FileKind.Text to "Text",
                        FileKind.Other to "Other"
                    ).forEach { (type, label) ->
                        val count = files.count { fileKind(it) == type }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("${shown.size} matching file${if (shown.size == 1) "" else "s"}") }
            if (shown.isEmpty()) {
                item { EmptyState(Icons.Rounded.FolderOpen, if (files.isEmpty()) "Your library is empty" else "No matching files", if (files.isEmpty()) "Add a file to preview and manage it here" else "Try another search or file type") }
            }
            items(shown, key = { it.id }) { file ->
                val ext = file.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "FILE" }
                val badge = badgeColors[Math.floorMod(ext.hashCode(), badgeColors.size)]
                Card(Modifier.fillMaxWidth().clickable { preview = file }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(badge.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                            Icon(when (fileKind(file)) { FileKind.Image -> Icons.Rounded.Image; FileKind.Pdf -> Icons.Rounded.PictureAsPdf; FileKind.Text -> Icons.Rounded.Description; else -> Icons.AutoMirrored.Rounded.InsertDriveFile }, null, tint = badge)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fileKind(file).label} · Added ${dayText(file.addedEpochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { preview = file }) { Text("Preview") }
                        IconButton(onClick = { removeTarget = file }) { Icon(Icons.Rounded.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add file") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePreviewSheet(file: FileItem, onDismiss: () -> Unit, onSaveCopy: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(file.mime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close preview") }
            }
            Spacer(Modifier.height(8.dp))
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

private fun decodeImage(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) { null }
}

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
                    val scale = minOf(1400f / page.height, 1100f / page.width, 2f)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
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
            } catch (e: Exception) { null to (e.message ?: "Could not read this file.") }
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
                OutlinedTextField(value = text ?: "", onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.bodySmall)
                Text("The original stays unchanged. Save creates an edited copy.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
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
        Text("${file.name} is saved in your library. In-app preview supports images, PDFs and common text files.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
