@file:Suppress("FunctionNaming")

package com.faheem.habits.features.language

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.GrammarNote
import com.faheem.habits.core.data.ReviewState
import com.faheem.habits.core.data.TranslationHistory
import com.faheem.habits.core.data.VocabularyWord
import com.faheem.habits.core.ui.*
import com.google.mlkit.nl.translate.TranslateLanguage
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object LanguageStudyLogic {
    private val intervalsDays = intArrayOf(1, 2, 4, 8, 16)
    const val RETRY_DELAY_MILLIS = 10 * 60 * 1_000L

    fun schedule(previous: ReviewState?, now: Long, correct: Boolean = true): ReviewState {
        if (!correct) {
            val old = previous
            return ReviewState(
                wordId = old?.wordId ?: 0L,
                box = ((old?.box ?: -1) - 1).coerceAtLeast(-1),
                nextReviewAt = now + RETRY_DELAY_MILLIS,
                lastReviewedAt = now,
                correctCount = old?.correctCount ?: 0,
                reviewCount = (old?.reviewCount ?: 0) + 1
            )
        }
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
    val reviewStates = db.reviewStateDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = db.translationHistoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableTranslationState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    internal val translationState: StateFlow<TranslationUiState> = mutableTranslationState.asStateFlow()

    fun saveWord(word: VocabularyWord) = viewModelScope.launch {
        db.vocabularyDao().upsert(
            word.copy(word = word.word.trim(), meaning = word.meaning.trim(), example = word.example.trim(), tags = word.tags.trim())
        )
    }

    fun removeWord(id: Long) = viewModelScope.launch {
        db.withTransaction {
            db.vocabularyDao().delete(id)
            db.reviewStateDao().delete(id)
        }
    }

    fun setMastered(word: VocabularyWord, mastered: Boolean) = viewModelScope.launch {
        db.vocabularyDao().setMastered(word.id, mastered)
    }

    fun saveNote(note: GrammarNote) = viewModelScope.launch {
        db.grammarNoteDao().upsert(note.copy(title = note.title.trim(), explanation = note.explanation.trim()))
    }
    fun removeNote(id: Long) = viewModelScope.launch { db.grammarNoteDao().delete(id) }
    fun togglePinned(note: GrammarNote) = viewModelScope.launch {
        db.grammarNoteDao().upsert(note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis()))
    }

    fun review(word: VocabularyWord, correct: Boolean) = viewModelScope.launch {
        val old = db.reviewStateDao().get(word.id)
        db.reviewStateDao().upsert(
            LanguageStudyLogic.schedule(old?.copy(wordId = word.id), System.currentTimeMillis(), correct)
                .copy(wordId = word.id)
        )
    }

    fun translate(text: String, source: String, target: String) = viewModelScope.launch {
        if (text.isBlank()) {
            mutableTranslationState.value = TranslationUiState.Error("Enter text to translate.")
            return@launch
        }
        mutableTranslationState.value = TranslationUiState.Loading
        try {
            val result = LanguageTranslator.translate(text.trim(), source, target)
            val historyItem = TranslationHistory(
                sourceLanguage = result.sourceLanguage,
                targetLanguage = target,
                sourceText = text.trim(),
                translatedText = result.text
            )
            db.translationHistoryDao().insert(historyItem)
            mutableTranslationState.value = TranslationUiState.Result(historyItem)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("LanguageTranslation", "On-device translation failed", error)
            mutableTranslationState.value = TranslationUiState.Error(
                translationFailureMessage(error)
            )
        }
    }

    fun clearTranslationState() {
        mutableTranslationState.value = TranslationUiState.Idle
    }

    fun setFavorite(item: TranslationHistory) = viewModelScope.launch {
        db.translationHistoryDao().setFavorite(item.id, !item.favorite)
    }

    fun removeTranslation(item: TranslationHistory) = viewModelScope.launch {
        db.translationHistoryDao().delete(item.id)
    }
}

private fun translationFailureMessage(error: Exception): String {
    val detail = error.message.orEmpty()
    return when {
        detail.contains("different languages", ignoreCase = true) -> detail
        detail.contains("could not be identified", ignoreCase = true) ||
            detail.contains("identify", ignoreCase = true) ||
            detail.contains("und", ignoreCase = true) ->
            "Could not detect the source language. Choose it manually and try again."
        detail.contains("network", ignoreCase = true) ||
            detail.contains("download", ignoreCase = true) ||
            detail.contains("play services", ignoreCase = true) ||
            error.javaClass.simpleName.contains("MlKit", ignoreCase = true) ->
            "Could not download the language model. Connect to the internet, update Google Play services, and retry. The model works offline after downloading."
        else -> detail.ifBlank { "Translation failed. Check the language pair and try again." }
    }
}

internal sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Loading : TranslationUiState
    data class Result(val item: TranslationHistory) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

@Composable
fun LanguageScreen(vm: LanguageViewModel = viewModel()) {
    val words by vm.words.collectAsState()
    val notes by vm.notes.collectAsState()
    val reviewStates by vm.reviewStates.collectAsState()
    val history by vm.history.collectAsState()
    val translationState by vm.translationState.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var wordDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<VocabularyWord?>(null) }
    var editingNote by remember { mutableStateOf<GrammarNote?>(null) }
    var sourceLanguage by remember { mutableStateOf(LanguageTranslator.AUTO_DETECT) }
    var targetLanguage by remember { mutableStateOf("es") }
    var translationText by remember { mutableStateOf("") }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val reviewByWord = remember(reviewStates) { reviewStates.associateBy { it.wordId } }
    val dueWords = remember(words, reviewByWord, now) {
        words.filter { word ->
            !word.mastered && (reviewByWord[word.id]?.nextReviewAt ?: 0L) <= now
        }.sortedBy { reviewByWord[it.id]?.nextReviewAt ?: 0L }
    }
    val selectedPracticeWord = dueWords.firstOrNull()
    val filteredWords = remember(words, query) {
        words.filter {
            it.word.contains(query, true) || it.meaning.contains(query, true) ||
                it.tags.contains(query, true) || it.example.contains(query, true)
        }
    }
    val filteredNotes = remember(notes, query) {
        notes.filter {
            it.title.contains(query, true) || it.explanation.contains(query, true) ||
                it.category.contains(query, true) || it.tags.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LanguageOverview(words.size, dueWords.size, notes.size)
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            divider = {}
        ) {
            listOf("Vocabulary", "Grammar", "Practice", "Translate", "History")
                .forEachIndexed { index, title ->
                    Tab(
                        selected = index == tab,
                        onClick = {
                            tab = index
                            query = ""
                        },
                        text = { Text(title, maxLines = 1) }
                    )
                }
        }
        when (tab) {
            0 -> VocabularyTab(
                words = filteredWords,
                query = query,
                onQuery = { query = it },
                onAdd = { editingWord = null; wordDialog = true },
                onEdit = { editingWord = it; wordDialog = true },
                onDelete = vm::removeWord,
                onMastered = vm::setMastered
            )
            1 -> GrammarTab(
                notes = filteredNotes,
                query = query,
                onQuery = { query = it },
                onAdd = { editingNote = null; noteDialog = true },
                onEdit = { editingNote = it; noteDialog = true },
                onDelete = vm::removeNote,
                onPin = vm::togglePinned
            )
            2 -> PracticeTab(
                word = selectedPracticeWord,
                dueCount = dueWords.size,
                review = { word, correct ->
                    vm.review(word, correct)
                }
            )
            3 -> TranslateTab(
                source = sourceLanguage,
                target = targetLanguage,
                text = translationText,
                state = translationState,
                onSourceChange = { sourceLanguage = it; vm.clearTranslationState() },
                onTargetChange = { targetLanguage = it; vm.clearTranslationState() },
                onTextChange = { translationText = it; vm.clearTranslationState() },
                onSwap = {
                    if (sourceLanguage != LanguageTranslator.AUTO_DETECT) {
                        sourceLanguage = targetLanguage.also { targetLanguage = sourceLanguage }
                    }
                    translationText = (translationState as? TranslationUiState.Result)?.item?.translatedText.orEmpty()
                    vm.clearTranslationState()
                },
                onTranslate = { vm.translate(translationText, sourceLanguage, targetLanguage) }
            )
            else -> HistoryTab(
                history = history,
                onFavorite = vm::setFavorite,
                onDelete = vm::removeTranslation,
                onReuse = { item ->
                    sourceLanguage = item.sourceLanguage
                    targetLanguage = item.targetLanguage
                    translationText = item.sourceText
                    tab = 3
                    vm.clearTranslationState()
                }
            )
        }
    }
    if (wordDialog) WordEditorDialog(editingWord, onDismiss = { wordDialog = false }) { vm.saveWord(it); wordDialog = false }
    if (noteDialog) GrammarEditorDialog(editingNote, onDismiss = { noteDialog = false }) { vm.saveNote(it); noteDialog = false }
}

@Composable
private fun LanguageOverview(wordCount: Int, dueCount: Int, noteCount: Int) {
    PremiumCard(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        accent = AppModule.Language.accent,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR LEARNING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(3.dp))
                Text("Language studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Build vocabulary, review, and translate offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LearningStat("Words", wordCount.toString(), Modifier.weight(1f))
            LearningStat("Due now", dueCount.toString(), Modifier.weight(1f))
            LearningStat("Notes", noteCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun LearningStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VocabularyTab(
    words: List<VocabularyWord>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (VocabularyWord) -> Unit,
    onDelete: (Long) -> Unit,
    onMastered: (VocabularyWord, Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchActionRow(
            query = query,
            onQuery = onQuery,
            placeholder = "Search words, meanings, tags",
            addLabel = "Add vocabulary",
            onAdd = onAdd
        )
        Spacer(Modifier.height(10.dp))
        if (words.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.Book,
                    if (query.isBlank()) "Start your word bank" else "No matching words",
                    if (query.isBlank()) "Save useful words and examples to practice them later."
                    else "Try another search or clear the search field."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(words, key = { it.id }) { word ->
                    PremiumCard(
                        contentPadding = PaddingValues(start = 15.dp, top = 13.dp, end = 8.dp, bottom = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (word.partOfSpeech.isNotBlank()) {
                                        Text(
                                            "  ${word.partOfSpeech}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        "  ${languageName(word.language)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (word.mastered) {
                                        Text("  MASTERED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(word.meaning, style = MaterialTheme.typography.bodyMedium)
                                if (word.example.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        word.example,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (word.tags.isNotBlank()) {
                                    Spacer(Modifier.height(7.dp))
                                    Text(word.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onMastered(word, !word.mastered) }) {
                                    Icon(
                                        if (word.mastered) Icons.Rounded.Refresh else Icons.Rounded.Check,
                                        contentDescription = if (word.mastered) "Review word again" else "Mark as mastered"
                                    )
                                }
                                Row {
                                    TextButton(onClick = { onEdit(word) }) { Text("Edit") }
                                    IconButton(onClick = { onDelete(word.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete word")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarTab(
    notes: List<GrammarNote>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (GrammarNote) -> Unit,
    onDelete: (Long) -> Unit,
    onPin: (GrammarNote) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchActionRow(
            query = query,
            onQuery = onQuery,
            placeholder = "Search grammar notes",
            addLabel = "Add grammar note",
            onAdd = onAdd
        )
        Spacer(Modifier.height(10.dp))
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.Lightbulb,
                    if (query.isBlank()) "Keep grammar notes" else "No matching notes",
                    if (query.isBlank()) "Save explanations, examples, and common mistakes in one place."
                    else "Try another search or clear the search field."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    PremiumCard(
                        contentPadding = PaddingValues(start = 15.dp, top = 13.dp, end = 8.dp, bottom = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    note.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (note.explanation.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(note.explanation, style = MaterialTheme.typography.bodyMedium)
                                }
                                GrammarExample("Correct", note.correctExample, MaterialTheme.colorScheme.primary)
                                GrammarExample("Avoid", note.incorrectExample, MaterialTheme.colorScheme.error)
                                if (note.commonMistake) {
                                    Text("Common mistake", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                if (note.tags.isNotBlank()) Text(note.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onPin(note) }) {
                                    Icon(
                                        if (note.pinned) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = if (note.pinned) "Unpin note" else "Pin note"
                                    )
                                }
                                Row {
                                    TextButton(onClick = { onEdit(note) }) { Text("Edit") }
                                    IconButton(onClick = { onDelete(note.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchActionRow(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    addLabel: String,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val shape = RoundedCornerShape(18.dp)
        BasicTextField(
            value = query,
            onValueChange = { onQuery(it.take(120)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.merge(
                TextStyle(color = MaterialTheme.colorScheme.onSurface)
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .padding(start = 15.dp, end = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQuery("") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        )
        FilledIconButton(
            onClick = onAdd,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Rounded.Add, contentDescription = addLabel)
        }
    }
}

@Composable
private fun GrammarExample(label: String, text: String, tint: androidx.compose.ui.graphics.Color) {
    if (text.isNotBlank()) {
        Spacer(Modifier.height(7.dp))
        Text("$label: $text", style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun PracticeTab(
    word: VocabularyWord?,
    dueCount: Int,
    review: (VocabularyWord, Boolean) -> Unit
) {
    var revealed by remember(word?.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "$dueCount ${if (dueCount == 1) "word" else "words"} ready to review",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (word == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.Check,
                    "You’re all caught up",
                    "Add vocabulary or come back when your next review is due."
                )
            }
        } else {
            PremiumCard(
                Modifier.fillMaxWidth().weight(1f),
                accent = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(24.dp)
            ) {
                Text("WORD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(
                    word.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (word.partOfSpeech.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(word.partOfSpeech, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                if (revealed) {
                    Spacer(Modifier.height(18.dp))
                    Text("MEANING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(5.dp))
                    Text(word.meaning, style = MaterialTheme.typography.titleMedium)
                    if (word.example.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(word.example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { review(word, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Review again")
                        }
                        Button(
                            onClick = { review(word, true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Got it")
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Reveal meaning", onClick = { revealed = true }, icon = Icons.Rounded.Lightbulb)
                }
            }
            Text(
                "Correct reviews move through 1, 2, 4, 8, and 16-day intervals. Missed words return sooner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateTab(
    source: String,
    target: String,
    text: String,
    state: TranslationUiState,
    onSourceChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSwap: () -> Unit,
    onTranslate: () -> Unit
) {
    val languages = remember {
        TranslateLanguage.getAllLanguages().sortedBy { languageName(it) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("On-device translation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Translation runs on your device. Language models download the first time you use a pair.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LanguagePicker(
                label = "From",
                selected = source,
                languages = listOf(LanguageTranslator.AUTO_DETECT) + languages,
                modifier = Modifier.weight(1f),
                onSelect = onSourceChange
            )
            IconButton(onClick = onSwap, enabled = source != LanguageTranslator.AUTO_DETECT) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = "Swap languages")
            }
            LanguagePicker(
                label = "To",
                selected = target,
                languages = languages,
                modifier = Modifier.weight(1f),
                onSelect = onTargetChange
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(5_000)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
            placeholder = { Text("Type or paste text") },
            supportingText = { Text("${text.length}/5000") },
            minLines = 4,
            maxLines = 8
        )
        PrimaryButton(
            text = if (state is TranslationUiState.Loading) "Preparing model…" else "Translate",
            onClick = onTranslate,
            enabled = text.isNotBlank() && state !is TranslationUiState.Loading,
            loading = state is TranslationUiState.Loading,
            icon = Icons.AutoMirrored.Rounded.ArrowForward
        )
        when (state) {
            is TranslationUiState.Result -> PremiumCard(
                accent = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    "${languageName(state.item.sourceLanguage)} → ${languageName(state.item.targetLanguage)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(state.item.translatedText, style = MaterialTheme.typography.bodyLarge)
            }
            is TranslationUiState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    label: String,
    selected: String,
    languages: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (selected == LanguageTranslator.AUTO_DETECT) "Auto detect" else languageName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(if (language == LanguageTranslator.AUTO_DETECT) "Auto detect" else languageName(language)) },
                    onClick = { onSelect(language); expanded = false }
                )
            }
        }
    }
}

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage().ifBlank { code }.replaceFirstChar { it.uppercase() }

@Composable
private fun HistoryTab(
    history: List<TranslationHistory>,
    onFavorite: (TranslationHistory) -> Unit,
    onDelete: (TranslationHistory) -> Unit,
    onReuse: (TranslationHistory) -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Rounded.Language, "No translation history", "Your recent on-device translations will appear here.")
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(history, key = { it.id }) { item ->
                PremiumCard(contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(
                            Modifier.weight(1f).clickable { onReuse(item) }
                        ) {
                            Text(
                                "${languageName(item.sourceLanguage)} → ${languageName(item.targetLanguage)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(item.sourceText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.translatedText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Column {
                            IconButton(onClick = { onFavorite(item) }) {
                                Icon(
                                    if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (item.favorite) "Remove favorite" else "Favorite translation"
                                )
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete translation")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordEditorDialog(
    initial: VocabularyWord?,
    onDismiss: () -> Unit,
    onSave: (VocabularyWord) -> Unit
) {
    var word by remember(initial) { mutableStateOf(initial?.word.orEmpty()) }
    var meaning by remember(initial) { mutableStateOf(initial?.meaning.orEmpty()) }
    var example by remember(initial) { mutableStateOf(initial?.example.orEmpty()) }
    var partOfSpeech by remember(initial) { mutableStateOf(initial?.partOfSpeech.orEmpty()) }
    var language by remember(initial) { mutableStateOf(initial?.language ?: "en") }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add vocabulary" else "Edit vocabulary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(word, { word = it.take(100) }, label = { Text("Word or phrase") }, singleLine = true)
                OutlinedTextField(meaning, { meaning = it.take(300) }, label = { Text("Meaning") }, minLines = 2)
                OutlinedTextField(language, { language = it.take(12) }, label = { Text("Language code (e.g. en, es, fr)") }, singleLine = true)
                OutlinedTextField(example, { example = it.take(300) }, label = { Text("Example sentence") }, minLines = 2)
                OutlinedTextField(partOfSpeech, { partOfSpeech = it.take(40) }, label = { Text("Part of speech") }, singleLine = true)
                OutlinedTextField(tags, { tags = it.take(120) }, label = { Text("Tags (comma separated)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = word.isNotBlank() && meaning.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: VocabularyWord(word = "", meaning = "")).copy(
                            word = word,
                            meaning = meaning,
                            language = language.trim().ifBlank { "en" },
                            example = example,
                            partOfSpeech = partOfSpeech,
                            tags = tags,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GrammarEditorDialog(
    initial: GrammarNote?,
    onDismiss: () -> Unit,
    onSave: (GrammarNote) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "Other") }
    var explanation by remember(initial) { mutableStateOf(initial?.explanation.orEmpty()) }
    var correctExample by remember(initial) { mutableStateOf(initial?.correctExample.orEmpty()) }
    var incorrectExample by remember(initial) { mutableStateOf(initial?.incorrectExample.orEmpty()) }
    var commonMistake by remember(initial) { mutableStateOf(initial?.commonMistake ?: false) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add grammar note" else "Edit grammar note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(category, { category = it.take(40) }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(explanation, { explanation = it.take(1_000) }, label = { Text("Explanation") }, minLines = 2)
                OutlinedTextField(correctExample, { correctExample = it.take(300) }, label = { Text("Correct example") }, minLines = 2)
                OutlinedTextField(incorrectExample, { incorrectExample = it.take(300) }, label = { Text("Common incorrect example") }, minLines = 2)
                OutlinedTextField(tags, { tags = it.take(120) }, label = { Text("Tags (comma separated)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(commonMistake, { commonMistake = it })
                    Text("Mark as a common mistake")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: GrammarNote(title = "")).copy(
                            title = title,
                            category = category.ifBlank { "Other" },
                            explanation = explanation,
                            correctExample = correctExample,
                            incorrectExample = incorrectExample,
                            commonMistake = commonMistake,
                            tags = tags,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
