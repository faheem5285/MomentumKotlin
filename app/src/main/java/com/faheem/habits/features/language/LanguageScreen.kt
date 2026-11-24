@file:Suppress("FunctionNaming")

package com.faheem.habits.features.language

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.GrammarNote
import com.faheem.habits.core.data.VocabularyWord
import com.faheem.habits.core.ui.*
import com.faheem.habits.core.data.ReviewState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal object LanguageStudyLogic {
    private val intervalsDays = intArrayOf(1, 2, 4, 8, 16)
    fun schedule(previous: ReviewState?, now: Long): ReviewState {
        val box = ((previous?.box ?: -1) + 1).coerceIn(0, intervalsDays.lastIndex)
        return ReviewState(
            wordId = previous?.wordId ?: 0L,
            box = box,
            nextReviewAt = now + intervalsDays[box] * 86_400_000L,
            lastReviewedAt = now,
            correctCount = (previous?.correctCount ?: 0) + 1,
            reviewCount = (previous?.reviewCount ?: 0) + 1
        )
    }
}

class LanguageViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val words = db.vocabularyDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = db.grammarNoteDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveWord(word: VocabularyWord) = viewModelScope.launch { db.vocabularyDao().upsert(word.copy(word = word.word.trim(), meaning = word.meaning.trim())) }
    fun removeWord(id: Long) = viewModelScope.launch { db.vocabularyDao().delete(id) }
    fun saveNote(note: GrammarNote) = viewModelScope.launch { db.grammarNoteDao().upsert(note.copy(title = note.title.trim())) }
    fun removeNote(id: Long) = viewModelScope.launch { db.grammarNoteDao().delete(id) }
    fun markReviewed(word: VocabularyWord) = viewModelScope.launch {
        val old = db.reviewStateDao().get(word.id)
        db.reviewStateDao().upsert(LanguageStudyLogic.schedule(old?.copy(wordId = word.id), System.currentTimeMillis()).copy(wordId = word.id))
    }
}

@Composable
fun LanguageScreen(vm: LanguageViewModel = viewModel()) {
    val words by vm.words.collectAsState()
    val notes by vm.notes.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var wordDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TabRow(selectedTabIndex = tab) {
            listOf("Words", "Grammar", "Practice", "Translate").forEachIndexed { index, title -> Tab(index == tab, { tab = index }, text = { Text(title) }) }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0, 1 -> {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (tab == 0) wordDialog = true else noteDialog = true }) { Icon(Icons.Rounded.Add, null); Text(if (tab == 0) "Add word" else "Add note") }
                }
                if (tab == 0) LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(words.filter { it.word.contains(query, true) || it.meaning.contains(query, true) }, key = { it.id }) { word ->
                        PremiumCard(contentPadding = PaddingValues(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(word.word, fontWeight = FontWeight.SemiBold); Text(word.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant); if (word.example.isNotBlank()) Text(word.example, style = MaterialTheme.typography.bodySmall) }
                                IconButton(onClick = { vm.removeWord(word.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete word") }
                            }
                        }
                    }
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes.filter { it.title.contains(query, true) || it.explanation.contains(query, true) }, key = { it.id }) { note ->
                        PremiumCard(contentPadding = PaddingValues(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(note.title, fontWeight = FontWeight.SemiBold); Text(note.category, color = MaterialTheme.colorScheme.primary); Text(note.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton(onClick = { vm.removeNote(note.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete note") }
                            }
                        }
                    }
                }
            }
            2 -> {
                val due = words.filter { it.id > 0 }
                Text("Review your vocabulary", style = MaterialTheme.typography.titleLarge)
                Text("${due.size} saved words · intervals: 1, 2, 4, 8, then 16 days", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(due, key = { it.id }) { word ->
                        PremiumCard(contentPadding = PaddingValues(14.dp)) {
                            Text(word.word, fontWeight = FontWeight.SemiBold)
                            var revealed by remember(word.id) { mutableStateOf(false) }
                            if (revealed) Text(word.meaning, Modifier.padding(vertical = 8.dp))
                            TextButton(onClick = { if (revealed) vm.markReviewed(word) else revealed = true }) { Text(if (revealed) "Got it · schedule review" else "Reveal meaning") }
                        }
                    }
                }
            }
            else -> Text("On-device translation is ready to connect. Downloaded language models will run offline.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (wordDialog) EntryDialog("Add vocabulary", "Word", "Meaning", onDismiss = { wordDialog = false }) { a, b -> vm.saveWord(VocabularyWord(word = a, meaning = b)); wordDialog = false }
    if (noteDialog) EntryDialog("Add grammar note", "Title", "Explanation", onDismiss = { noteDialog = false }) { a, b -> vm.saveNote(GrammarNote(title = a, explanation = b)); noteDialog = false }
}

@Composable
private fun EntryDialog(title: String, firstLabel: String, secondLabel: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }, singleLine = true)
            OutlinedTextField(second, { second = it }, label = { Text(secondLabel) })
        }
    }, confirmButton = { TextButton(enabled = first.isNotBlank() && second.isNotBlank(), onClick = { onSave(first, second) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
