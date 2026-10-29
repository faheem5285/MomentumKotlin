package com.faheem.habits

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FileViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).fileDao()

    val files: StateFlow<List<FileItem>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, uri: String, mime: String) {
        viewModelScope.launch { dao.insert(FileItem(name = name, uri = uri, mime = mime, addedEpochDay = today())) }
    }

    fun remove(f: FileItem) {
        viewModelScope.launch { dao.delete(f) }
    }
}

private val badgeColors = listOf(
    Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981),
    Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
)

@Composable
fun FilesScreen(vm: FileViewModel = viewModel()) {
    val files by vm.files.collectAsState()
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            var name = "File"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) name = c.getString(i) ?: name
                }
            }
            vm.add(name, uri.toString(), ctx.contentResolver.getType(uri) ?: "*/*")
        }
    }

    val shown = files.filter { it.name.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("File library", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                    Text("${files.size} file${if (files.size == 1) "" else "s"}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Links to your files. Moving or deleting the original breaks the link.",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search files") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Folder,
                        if (files.isEmpty()) "No files yet" else "No match",
                        if (files.isEmpty()) "Tap “Add file” to pick one" else "Try a different name"
                    )
                }
            }
            items(shown, key = { it.id }) { f ->
                val ext = f.name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
                val color = badgeColors[Math.floorMod(ext.hashCode(), badgeColors.size)]
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ext, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Added ${dayText(f.addedEpochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(f.uri), f.mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                ctx.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(ctx, "Access to this file was lost. Add it again.", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Open") }
                        IconButton(onClick = { vm.remove(f) }) {
                            Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add file") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}
