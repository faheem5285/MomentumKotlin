package com.faheem.habits.features.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.ui.LocalGlassPalette
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.MomentumTokens
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FilePreviewSheet(file: FileItem, onDismiss: () -> Unit, onSaveCopy: (String) -> Unit) {
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
