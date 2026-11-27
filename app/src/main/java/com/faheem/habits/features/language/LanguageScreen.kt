@file:Suppress("FunctionNaming", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.language

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.GrammarNote
import com.faheem.habits.core.data.ReviewState
import com.faheem.habits.core.data.TranslationHistory
import com.faheem.habits.core.data.VocabularyWord
import com.faheem.habits.core.ui.EmptyState
import com.faheem.habits.core.ui.MomentumChip
import com.faheem.habits.core.ui.PremiumCard

import com.faheem.habits.core.ui.SectionHeader
import com.google.mlkit.nl.translate.TranslateLanguage
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private var translationJob: Job? = null
    private val mutableProofreadingState = MutableStateFlow<ProofreadingUiState>(ProofreadingUiState.Idle)
    internal val proofreadingState: StateFlow<ProofreadingUiState> = mutableProofreadingState.asStateFlow()
    private var proofreadingJob: Job? = null

    fun saveWord(word: VocabularyWord) = viewModelScope.launch {
        db.vocabularyDao().upsert(
            word.copy(
                word = word.word.trim(),
                meaning = word.meaning.trim(),
                example = word.example.trim(),
                tags = word.tags.trim(),
                partOfSpeech = word.partOfSpeech.trim()
            )
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
        db.grammarNoteDao().upsert(
            note.copy(
                title = note.title.trim(),
                category = note.category.trim().ifBlank { "General" },
                explanation = note.explanation.trim(),
                correctExample = note.correctExample.trim(),
                incorrectExample = note.incorrectExample.trim(),
                tags = note.tags.trim()
            )
        )
    }

    fun removeNote(id: Long) = viewModelScope.launch { db.grammarNoteDao().delete(id) }

    fun togglePinned(note: GrammarNote) = viewModelScope.launch {
        db.grammarNoteDao().upsert(note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis()))
    }

    fun review(word: VocabularyWord, correct: Boolean) = viewModelScope.launch {
        db.withTransaction {
            val old = db.reviewStateDao().get(word.id)
            db.reviewStateDao().upsert(
                LanguageStudyLogic.schedule(old, System.currentTimeMillis(), correct).copy(wordId = word.id)
            )
        }
    }

    fun translate(text: String, source: String, target: String) {
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            if (text.isBlank()) {
                mutableTranslationState.value = TranslationUiState.Error("Enter text to translate.")
                return@launch
            }
            val normalizedText = text.trim()
            if (normalizedText.length > 5_000) {
                mutableTranslationState.value = TranslationUiState.Error("Text must be 5,000 characters or fewer.")
                return@launch
            }
            mutableTranslationState.value = TranslationUiState.Loading
            try {
                val result = LanguageTranslator.translate(normalizedText, source, target)
                val historyItem = TranslationHistory(
                    sourceLanguage = result.sourceLanguage,
                    targetLanguage = target,
                    sourceText = normalizedText,
                    translatedText = result.text
                )
                db.translationHistoryDao().insert(historyItem)
                mutableTranslationState.value = TranslationUiState.Result(historyItem, result.multipleMeanings)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableTranslationState.value = TranslationUiState.Error(translationFailureMessage(error))
            }
        }
    }

    fun clearTranslationState() {
        translationJob?.cancel()
        translationJob = null
        mutableTranslationState.value = TranslationUiState.Idle
    }

    fun proofread(text: String, language: String) {
        proofreadingJob?.cancel()
        proofreadingJob = viewModelScope.launch {
            if (text.isBlank()) {
                mutableProofreadingState.value = ProofreadingUiState.Error("Enter text to check.")
                return@launch
            }
            mutableProofreadingState.value = ProofreadingUiState.Loading
            try {
                val result = LanguageProofreader.proofread(text = text.trim(), language = language.ifBlank { "auto" })
                mutableProofreadingState.value = ProofreadingUiState.Checked(result)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableProofreadingState.value = ProofreadingUiState.Error(
                    error.message ?: "Could not check spelling and grammar. Try again."
                )
            }
        }
    }

    fun clearProofreadingState() {
        proofreadingJob?.cancel()
        proofreadingJob = null
        mutableProofreadingState.value = ProofreadingUiState.Idle
    }

    fun setFavorite(item: TranslationHistory) = viewModelScope.launch {
        db.translationHistoryDao().setFavorite(item.id, !item.favorite)
    }

    fun removeTranslation(item: TranslationHistory) = viewModelScope.launch { db.translationHistoryDao().delete(item.id) }
    fun clearAllHistory() = viewModelScope.launch { db.translationHistoryDao().clear() }
}

private fun translationFailureMessage(error: Exception): String {
    val detail = error.message.orEmpty()
    return when {
        detail.contains("different languages", ignoreCase = true) -> detail
        detail.contains("identify", ignoreCase = true) || detail.contains("und", ignoreCase = true) ->
            "Could not detect the source language. Choose it manually and try again."
        detail.contains("network", ignoreCase = true) ||
            detail.contains("download", ignoreCase = true) ||
            detail.contains("connection", ignoreCase = true) ||
            detail.contains("unavailable", ignoreCase = true) ->
            "Translation needs the on-device language model. Connect to the internet once to download it, then it can work offline."
        else -> detail.ifBlank { "Translation failed. Check the language pair and try again." }
    }
}

internal sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Loading : TranslationUiState
    data class Result(
        val item: TranslationHistory,
        val multipleMeanings: List<WordMeaningGroup> = emptyList()
    ) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

internal sealed interface ProofreadingUiState {
    data object Idle : ProofreadingUiState
    data object Loading : ProofreadingUiState
    data class Checked(val result: ProofreadingResult) : ProofreadingUiState
    data class Error(val message: String) : ProofreadingUiState
}

private enum class LanguageTab(val label: String) {
    Library("Library"),
    Practice("Practice"),
    Translate("Translate"),
    History("History")
}
private enum class LibraryMode { Words, Grammar }
private enum class WordFilter(val label: String) { All("All"), Due("Due"), Mastered("Mastered") }
private enum class GrammarFilter(val label: String) { All("All"), Pinned("Pinned"), Mistakes("Mistakes") }
private enum class PracticeMode(val label: String) { Review("Spaced review"), Quiz("Quick quiz") }

@Composable
fun LanguageScreen(vm: LanguageViewModel = viewModel()) {
    val context = LocalContext.current
    val speak = rememberTextToSpeech(context)
    val words by vm.words.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val reviewStates by vm.reviewStates.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val translationState by vm.translationState.collectAsStateWithLifecycle()
    val proofreadingState by vm.proofreadingState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(LanguageTab.Library) }
    var libraryMode by remember { mutableStateOf(LibraryMode.Words) }
    var searchQuery by remember { mutableStateOf("") }
    var wordDialogOpen by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<VocabularyWord?>(null) }
    var noteDialogOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<GrammarNote?>(null) }
    var sourceLanguage by remember { mutableStateOf("en") }
    var targetLanguage by remember { mutableStateOf("ur") }
    var translationText by remember { mutableStateOf("") }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val reviewByWord = remember(reviewStates) { reviewStates.associateBy { it.wordId } }
    val dueWords = remember(words, reviewByWord, now) {
        words.filter { !it.mastered && (reviewByWord[it.id]?.nextReviewAt ?: 0L) <= now }
            .sortedBy { reviewByWord[it.id]?.nextReviewAt ?: 0L }
    }
    val masteredCount = remember(words) { words.count { it.mastered } }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        LanguageOverviewCard(
            words = words.size,
            mastered = masteredCount,
            due = dueWords.size,
            notes = notes.size,
            onReview = { selectedTab = LanguageTab.Practice }
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LanguageTab.entries) { tab ->
                val label = when (tab) {
                    LanguageTab.Practice -> if (dueWords.isNotEmpty()) "Practice (${dueWords.size})" else "Practice"
                    else -> tab.label
                }
                val icon = when (tab) {
                    LanguageTab.Library -> Icons.AutoMirrored.Rounded.MenuBook
                    LanguageTab.Practice -> Icons.Rounded.Psychology
                    LanguageTab.Translate -> Icons.Rounded.Translate
                    LanguageTab.History -> Icons.Rounded.History
                }
                MomentumChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab; searchQuery = "" },
                    label = label,
                    icon = icon
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                LanguageTab.Library -> LibrarySection(
                    mode = libraryMode,
                    onModeChange = { libraryMode = it; searchQuery = "" },
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    words = words,
                    notes = notes,
                    reviewMap = reviewByWord,
                    now = now,
                    onSpeak = speak,
                    onAddWord = { editingWord = null; wordDialogOpen = true },
                    onEditWord = { editingWord = it; wordDialogOpen = true },
                    onDeleteWord = vm::removeWord,
                    onToggleMastered = { word ->
                        vm.setMastered(word, !word.mastered)
                        Toast.makeText(context, if (!word.mastered) "Marked as mastered" else "Moved back to review", Toast.LENGTH_SHORT).show()
                    },
                    onAddNote = { editingNote = null; noteDialogOpen = true },
                    onEditNote = { editingNote = it; noteDialogOpen = true },
                    onDeleteNote = vm::removeNote,
                    onTogglePin = vm::togglePinned
                )

                LanguageTab.Practice -> PracticeSection(
                    words = words,
                    dueWords = dueWords,
                    reviewMap = reviewByWord,
                    onSpeak = speak,
                    onReview = vm::review,
                    onMastered = { vm.setMastered(it, true) }
                )

                LanguageTab.Translate -> TranslateSection(
                    source = sourceLanguage,
                    target = targetLanguage,
                    text = translationText,
                    translationState = translationState,
                    proofreadingState = proofreadingState,
                    onSourceChange = { sourceLanguage = it },
                    onTargetChange = { targetLanguage = it },
                    onTextChange = { translationText = it },
                    onSwap = {
                        if (sourceLanguage != LanguageTranslator.AUTO_DETECT) {
                            val old = sourceLanguage
                            sourceLanguage = targetLanguage
                            targetLanguage = old
                        }
                    },
                    onTranslate = { vm.translate(translationText, sourceLanguage, targetLanguage) },
                    onProofread = { vm.proofread(translationText, if (sourceLanguage == LanguageTranslator.AUTO_DETECT) "auto" else sourceLanguage) },
                    onUseCorrection = { translationText = it; vm.clearProofreadingState() },
                    onClear = { translationText = ""; vm.clearTranslationState(); vm.clearProofreadingState() },
                    onSpeak = speak,
                    onSaveAsWord = { src, meaning, lang ->
                        vm.saveWord(
                            VocabularyWord(
                                word = src.trim(),
                                meaning = meaning.trim(),
                                language = if (lang == LanguageTranslator.AUTO_DETECT) "en" else lang
                            )
                        )
                        Toast.makeText(context, "Saved to vocabulary", Toast.LENGTH_SHORT).show()
                    }
                )

                LanguageTab.History -> HistorySection(
                    history = history,
                    onFavorite = vm::setFavorite,
                    onDelete = vm::removeTranslation,
                    onClearAll = vm::clearAllHistory,
                    onReuse = { item ->
                        sourceLanguage = item.sourceLanguage
                        targetLanguage = item.targetLanguage
                        translationText = item.sourceText
                        selectedTab = LanguageTab.Translate
                    },
                    onSaveAsWord = { item ->
                        vm.saveWord(
                            VocabularyWord(
                                word = item.sourceText.take(120),
                                meaning = item.translatedText.take(300),
                                language = item.sourceLanguage
                            )
                        )
                        Toast.makeText(context, "Saved to vocabulary", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (wordDialogOpen) {
        WordEditorDialog(
            initial = editingWord,
            onDismiss = { wordDialogOpen = false; editingWord = null },
            onSave = { vm.saveWord(it); wordDialogOpen = false; editingWord = null }
        )
    }
    if (noteDialogOpen) {
        GrammarEditorDialog(
            initial = editingNote,
            onDismiss = { noteDialogOpen = false; editingNote = null },
            onSave = { vm.saveNote(it); noteDialogOpen = false; editingNote = null }
        )
    }
}

@Composable
private fun LanguageOverviewCard(
    words: Int,
    mastered: Int,
    due: Int,
    notes: Int,
    onReview: () -> Unit
) {
    val progress = if (words == 0) 0f else (mastered.toFloat() / words.toFloat()).coerceIn(0f, 1f)
    PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Language Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Learn faster with spaced review, quizzes, pronunciation, and translation tools.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (due > 0) FilledTonalButton(onClick = onReview) { Text("Review $due") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mastery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Words", words.toString(), Modifier.weight(1f))
                StatTile("Mastered", mastered.toString(), Modifier.weight(1f))
                StatTile("Due", due.toString(), Modifier.weight(1f), highlight = due > 0)
                StatTile("Notes", notes.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val container = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val valueColor = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibrarySection(
    mode: LibraryMode,
    onModeChange: (LibraryMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    words: List<VocabularyWord>,
    notes: List<GrammarNote>,
    reviewMap: Map<Long, ReviewState>,
    now: Long,
    onSpeak: (String, String) -> Unit,
    onAddWord: () -> Unit,
    onEditWord: (VocabularyWord) -> Unit,
    onDeleteWord: (Long) -> Unit,
    onToggleMastered: (VocabularyWord) -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (GrammarNote) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onTogglePin: (GrammarNote) -> Unit
) {
    var wordFilter by remember { mutableStateOf(WordFilter.All) }
    var grammarFilter by remember { mutableStateOf(GrammarFilter.All) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MomentumChip(selected = mode == LibraryMode.Words, onClick = { onModeChange(LibraryMode.Words) }, label = "Words")
            MomentumChip(selected = mode == LibraryMode.Grammar, onClick = { onModeChange(LibraryMode.Grammar) }, label = "Grammar")
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { if (mode == LibraryMode.Words) onAddWord() else onAddNote() }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (mode == LibraryMode.Words) "Word" else "Note")
            }
        }
        SearchField(value = query, onValueChange = onQueryChange, placeholder = if (mode == LibraryMode.Words) "Search words, meaning, tags" else "Search grammar notes")
        if (mode == LibraryMode.Words) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WordFilter.entries) { filter ->
                    FilterChip(selected = wordFilter == filter, onClick = { wordFilter = filter }, label = { Text(filter.label) })
                }
            }
            WordList(words, query, wordFilter, reviewMap, now, onSpeak, onEditWord, onDeleteWord, onToggleMastered)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GrammarFilter.entries) { filter ->
                    FilterChip(selected = grammarFilter == filter, onClick = { grammarFilter = filter }, label = { Text(filter.label) })
                }
            }
            GrammarList(notes, query, grammarFilter, onEditNote, onDeleteNote, onTogglePin)
        }
    }
}

@Composable
private fun WordList(
    words: List<VocabularyWord>,
    query: String,
    filter: WordFilter,
    reviewMap: Map<Long, ReviewState>,
    now: Long,
    onSpeak: (String, String) -> Unit,
    onEdit: (VocabularyWord) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleMastered: (VocabularyWord) -> Unit
) {
    val filtered = remember(words, query, filter, reviewMap, now) {
        val q = query.trim().lowercase()
        words.filter { word ->
            val matchesQuery = q.isBlank() || word.word.lowercase().contains(q) || word.meaning.lowercase().contains(q) || word.tags.lowercase().contains(q) || word.example.lowercase().contains(q)
            val due = !word.mastered && (reviewMap[word.id]?.nextReviewAt ?: 0L) <= now
            matchesQuery && when (filter) {
                WordFilter.All -> true
                WordFilter.Due -> due
                WordFilter.Mastered -> word.mastered
            }
        }
    }
    if (filtered.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Rounded.MenuBook, if (query.isBlank()) "No words found" else "No matching words", if (query.isBlank()) "Add words or change the filter to see your vocabulary." else "Try a different search or filter.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(filtered, key = { it.id }) { word ->
            val review = reviewMap[word.id]
            PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(word.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onSpeak(word.word, word.language) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Pronounce") }
                        IconButton(onClick = { onToggleMastered(word) }) {
                            Icon(if (word.mastered) Icons.Rounded.CheckCircle else Icons.Rounded.School, contentDescription = "Toggle mastered", tint = if (word.mastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(word) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { onDelete(word.id) }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete") }
                    }
                    if (word.example.isNotBlank()) Text("Example: ${word.example}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (word.partOfSpeech.isNotBlank()) SmallTag(word.partOfSpeech)
                        SmallTag(if (word.mastered) "Mastered" else "Box ${(review?.box ?: -1) + 1}")
                        SmallTag("Difficulty ${word.difficulty}")
                        if (word.tags.isNotBlank()) SmallTag(word.tags)
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarList(
    notes: List<GrammarNote>,
    query: String,
    filter: GrammarFilter,
    onEdit: (GrammarNote) -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePin: (GrammarNote) -> Unit
) {
    val filtered = remember(notes, query, filter) {
        val q = query.trim().lowercase()
        notes.filter { note ->
            val matchesQuery = q.isBlank() || note.title.lowercase().contains(q) || note.explanation.lowercase().contains(q) || note.category.lowercase().contains(q) || note.tags.lowercase().contains(q)
            matchesQuery && when (filter) {
                GrammarFilter.All -> true
                GrammarFilter.Pinned -> note.pinned
                GrammarFilter.Mistakes -> note.commonMistake
            }
        }.sortedWith(compareByDescending<GrammarNote> { it.pinned }.thenBy { it.title.lowercase() })
    }
    if (filtered.isEmpty()) {
        EmptyState(Icons.Rounded.School, if (query.isBlank()) "No grammar notes found" else "No matching notes", if (query.isBlank()) "Save rules, examples, and mistakes you want to avoid." else "Try a different search or filter.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(filtered, key = { it.id }) { note ->
            PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(note.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onTogglePin(note) }) {
                            Icon(if (note.pinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = "Pin", tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(note) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { onDelete(note.id) }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete") }
                    }
                    if (note.explanation.isNotBlank()) Text(note.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (note.correctExample.isNotBlank()) Text("Correct: ${note.correctExample}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    if (note.incorrectExample.isNotBlank()) Text("Avoid: ${note.incorrectExample}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (note.commonMistake) SmallTag("Common mistake")
                        if (note.tags.isNotBlank()) SmallTag(note.tags)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PracticeSection(
    words: List<VocabularyWord>,
    dueWords: List<VocabularyWord>,
    reviewMap: Map<Long, ReviewState>,
    onSpeak: (String, String) -> Unit,
    onReview: (VocabularyWord, Boolean) -> Unit,
    onMastered: (VocabularyWord) -> Unit
) {
    var mode by remember { mutableStateOf(PracticeMode.Review) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PracticeMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
            }
        }
        when (mode) {
            PracticeMode.Review -> SpacedReviewCard(dueWords, reviewMap, onSpeak, onReview, onMastered)
            PracticeMode.Quiz -> QuickQuizCard(words, onSpeak)
        }
    }
}

@Composable
private fun SpacedReviewCard(
    dueWords: List<VocabularyWord>,
    reviewMap: Map<Long, ReviewState>,
    onSpeak: (String, String) -> Unit,
    onReview: (VocabularyWord, Boolean) -> Unit,
    onMastered: (VocabularyWord) -> Unit
) {
    val activeWord = dueWords.firstOrNull()
    var revealed by remember(activeWord?.id) { mutableStateOf(false) }
    SectionHeader(title = "Spaced review", subtitle = if (dueWords.isEmpty()) "No words due right now" else "${dueWords.size} word(s) due")
    if (activeWord == null) {
        EmptyState(Icons.Rounded.CheckCircle, "You're caught up", "Add words in Library and they'll appear here when it's time to review.")
        return
    }
    val review = reviewMap[activeWord.id]
    PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SmallTag(languageName(activeWord.language))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallTag("Box ${(review?.box ?: -1) + 1}")
                    IconButton(onClick = { onSpeak(activeWord.word, activeWord.language) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Pronounce") }
                }
            }
            Text(activeWord.word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (activeWord.partOfSpeech.isNotBlank()) Text(activeWord.partOfSpeech, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (!revealed) {
                Text("Try to recall the meaning, then reveal the answer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal answer") }
            } else {
                Text(activeWord.meaning, style = MaterialTheme.typography.titleMedium)
                if (activeWord.example.isNotBlank()) Text("Example: ${activeWord.example}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onReview(activeWord, false) }, modifier = Modifier.weight(1f)) { Text("Again") }
                    Button(onClick = { onReview(activeWord, true) }, modifier = Modifier.weight(1f)) { Text("Got it") }
                }
                TextButton(onClick = { onMastered(activeWord) }) { Text("Mark mastered") }
            }
        }
    }
    Text("Review intervals adapt based on your answers. \"Again\" brings the word back sooner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun QuickQuizCard(words: List<VocabularyWord>, onSpeak: (String, String) -> Unit) {
    val candidates = remember(words) { words.filter { it.meaning.isNotBlank() } }
    if (candidates.size < 2) {
        EmptyState(Icons.Rounded.Psychology, "Quiz needs more words", "Add at least 2 vocabulary words with meanings to start a quick quiz.")
        return
    }
    var seed by remember { mutableIntStateOf(0) }
    val question = remember(candidates, seed) { candidates.random() }
    val options = remember(candidates, question, seed) {
        val wrong = candidates.filter { it.id != question.id }.shuffled().take(3).map { it.meaning }
        (wrong + question.meaning).shuffled()
    }
    var selected by remember(question.id, seed) { mutableStateOf<String?>(null) }
    val correct = selected == question.meaning

    SectionHeader(title = "Quick quiz", subtitle = "Pick the correct meaning")
    PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SmallTag(languageName(question.language))
                IconButton(onClick = { onSpeak(question.word, question.language) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Pronounce") }
            }
            Text(question.word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            options.forEach { option ->
                val isSelected = selected == option
                val isAnswer = option == question.meaning
                val bg = when {
                    selected != null && isAnswer -> Color(0xFF2E7D32).copy(alpha = 0.14f)
                    isSelected && !isAnswer -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
                OutlinedButton(
                    onClick = { if (selected == null) selected = option },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(option, modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(vertical = 4.dp), textAlign = TextAlign.Start)
                }
            }
            if (selected != null) {
                Text(if (correct) "Correct!" else "Correct answer: ${question.meaning}", color = if (correct) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Button(onClick = { seed++ }, modifier = Modifier.fillMaxWidth()) { Text("Next question") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateSection(
    source: String,
    target: String,
    text: String,
    translationState: TranslationUiState,
    proofreadingState: ProofreadingUiState,
    onSourceChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSwap: () -> Unit,
    onTranslate: () -> Unit,
    onProofread: () -> Unit,
    onUseCorrection: (String) -> Unit,
    onClear: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onSaveAsWord: (text: String, meaning: String, srcLang: String) -> Unit
) {
    val context = LocalContext.current
    val languages = remember { TranslateLanguage.getAllLanguages().sortedBy { languageName(it) } }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Translate",
            subtitle = "On-device translation using ML Kit. Language models may download the first time you use a pair."
        )
        PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguagePicker("From", source, listOf(LanguageTranslator.AUTO_DETECT) + languages, onSourceChange, Modifier.weight(1f))
                    FilledIconButton(onClick = onSwap, enabled = source != LanguageTranslator.AUTO_DETECT, modifier = Modifier.size(42.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Swap languages")
                    }
                    LanguagePicker("To", target, languages, onTargetChange, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Text to translate") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onTranslate, enabled = text.isNotBlank() && translationState !is TranslationUiState.Loading) {
                        if (translationState is TranslationUiState.Loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Translate")
                    }
                    OutlinedButton(onClick = onProofread, enabled = text.isNotBlank() && proofreadingState !is ProofreadingUiState.Loading) {
                        if (proofreadingState is ProofreadingUiState.Loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check")
                    }
                    if (text.isNotBlank()) {
                        IconButton(onClick = { onSpeak(text, source) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Listen") }
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
                Text(
                    "Proofreading uses the LanguageTool online service and sends the entered text for checking.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (translationState) {
            TranslationUiState.Idle -> Unit
            TranslationUiState.Loading -> Unit
            is TranslationUiState.Error -> PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Text(translationState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            is TranslationUiState.Result -> PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row {
                            IconButton(onClick = { onSpeak(translationState.item.translatedText, translationState.item.targetLanguage) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Listen translation") }
                            IconButton(onClick = { copyText(context, translationState.item.translatedText, "Translation copied") }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy translation") }
                            IconButton(onClick = { onSaveAsWord(translationState.item.sourceText, translationState.item.translatedText, translationState.item.sourceLanguage) }) { Icon(Icons.Rounded.Add, contentDescription = "Save as word") }
                        }
                    }
                    Text(translationState.item.translatedText, style = MaterialTheme.typography.bodyLarge)
                    SmallTag("${languageName(translationState.item.sourceLanguage)} → ${languageName(translationState.item.targetLanguage)}")
                }
            }
        }

        when (proofreadingState) {
            ProofreadingUiState.Idle, ProofreadingUiState.Loading -> Unit
            is ProofreadingUiState.Error -> PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Text(proofreadingState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            is ProofreadingUiState.Checked -> PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Proofreading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val result = proofreadingState.result
                    Text(
                        if (result.correctionsApplied == 0) "No suggestions found." else "${result.correctionsApplied} suggestion(s) applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (result.correctedText.isNotBlank()) Text(result.correctedText, style = MaterialTheme.typography.bodyMedium)
                    if (result.correctionsApplied > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onUseCorrection(result.correctedText) }) { Text("Use correction") }
                            OutlinedButton(onClick = { copyText(context, result.correctedText, "Correction copied") }) { Text("Copy") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: String,
    languages: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(languageName(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { code ->
                DropdownMenuItem(
                    text = { Text(languageName(code)) },
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<TranslationHistory>,
    onFavorite: (TranslationHistory) -> Unit,
    onDelete: (TranslationHistory) -> Unit,
    onClearAll: () -> Unit,
    onReuse: (TranslationHistory) -> Unit,
    onSaveAsWord: (TranslationHistory) -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (history.isNotEmpty()) SmallTag("${history.size}")
            }
            if (history.isNotEmpty()) TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Clear all") }
        }
        if (history.isEmpty()) {
            EmptyState(Icons.Rounded.History, "No translation history", "Translations you make will appear here for quick reuse.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(history, key = { it.id }) { item ->
                PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${languageName(item.sourceLanguage)} → ${languageName(item.targetLanguage)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onFavorite(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(item.sourceText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.translatedText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onReuse(item) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Reuse") }
                            TextButton(onClick = { copyText(context, item.translatedText, "Copied") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Copy") }
                            TextButton(onClick = { onSaveAsWord(item) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Save") }
                            TextButton(onClick = { onDelete(item) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Delete") }
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
    var partOfSpeech by remember(initial) { mutableStateOf(initial?.partOfSpeech.orEmpty()) }
    var example by remember(initial) { mutableStateOf(initial?.example.orEmpty()) }
    var language by remember(initial) { mutableStateOf(initial?.language ?: "en") }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var difficulty by remember(initial) { mutableIntStateOf(initial?.difficulty ?: 1) }
    var langExpanded by remember { mutableStateOf(false) }
    val languages = remember { TranslateLanguage.getAllLanguages().sortedBy { languageName(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add word" else "Edit word") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it.take(120) }, label = { Text("Word *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = meaning, onValueChange = { meaning = it.take(300) }, label = { Text("Meaning *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = partOfSpeech, onValueChange = { partOfSpeech = it.take(40) }, label = { Text("Part of speech") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = example, onValueChange = { example = it.take(300) }, label = { Text("Example") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { langExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Language: ${languageName(language)}") }
                    DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        languages.forEach { code ->
                            DropdownMenuItem(text = { Text(languageName(code)) }, onClick = { language = code; langExpanded = false })
                        }
                    }
                }
                Text("Difficulty", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(selected = difficulty == level, onClick = { difficulty = level }, label = { Text("$level") })
                    }
                }
                OutlinedTextField(value = tags, onValueChange = { tags = it.take(120) }, label = { Text("Tags") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = word.isNotBlank() && meaning.isNotBlank(), onClick = {
                onSave((initial ?: VocabularyWord(word = "", meaning = "")).copy(word = word, meaning = meaning, partOfSpeech = partOfSpeech, example = example, language = language, tags = tags, difficulty = difficulty, updatedAt = System.currentTimeMillis()))
            }) { Text("Save") }
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
    var category by remember(initial) { mutableStateOf(initial?.category ?: "Grammar Rule") }
    var explanation by remember(initial) { mutableStateOf(initial?.explanation.orEmpty()) }
    var correctExample by remember(initial) { mutableStateOf(initial?.correctExample.orEmpty()) }
    var incorrectExample by remember(initial) { mutableStateOf(initial?.incorrectExample.orEmpty()) }
    var commonMistake by remember(initial) { mutableStateOf(initial?.commonMistake ?: false) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add grammar note" else "Edit grammar note") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it.take(40) }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = explanation, onValueChange = { explanation = it.take(1000) }, label = { Text("Explanation") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = correctExample, onValueChange = { correctExample = it.take(300) }, label = { Text("Correct example") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = incorrectExample, onValueChange = { incorrectExample = it.take(300) }, label = { Text("Common mistake") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = commonMistake, onCheckedChange = { commonMistake = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Mark as common mistake", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(value = tags, onValueChange = { tags = it.take(120) }, label = { Text("Tags") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                onSave((initial ?: GrammarNote(title = "")).copy(title = title, category = category.ifBlank { "Grammar Rule" }, explanation = explanation, correctExample = correctExample, incorrectExample = incorrectExample, commonMistake = commonMistake, tags = tags, updatedAt = System.currentTimeMillis()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (value.isNotBlank()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun rememberTextToSpeech(context: Context): (String, String) -> Unit {
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            // Ready state handled on speak; no-op here.
            if (status != TextToSpeech.SUCCESS) {
                // Keep null-safe behavior on speak.
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }
    return remember(tts) {
        { text: String, langCode: String ->
            val engine = tts
            if (engine == null || text.isBlank()) {
                Toast.makeText(context, "Text-to-speech not available", Toast.LENGTH_SHORT).show()
            } else {
                val locale = if (langCode == LanguageTranslator.AUTO_DETECT) Locale.getDefault() else Locale.forLanguageTag(langCode)
                val result = engine.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(context, "Voice not available for ${languageName(langCode)}", Toast.LENGTH_SHORT).show()
                } else {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zylo-tts")
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Zylo", text))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun languageName(code: String): String {
    if (code == LanguageTranslator.AUTO_DETECT) return "Auto detect"
    return Locale.forLanguageTag(code).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }.ifBlank { code }
}
