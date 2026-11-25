@file:Suppress("FunctionNaming", "LongMethod", "ComplexCondition")

package com.faheem.habits.features.language

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    fun removeNote(id: Long) = viewModelScope.launch {
        db.grammarNoteDao().delete(id)
    }

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
                mutableTranslationState.value = TranslationUiState.Result(
                    item = historyItem,
                    multipleMeanings = result.multipleMeanings
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.e("LanguageTranslation", "On-device translation failed", error)
                mutableTranslationState.value = TranslationUiState.Error(
                    translationFailureMessage(error)
                )
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
                val result = LanguageProofreader.proofread(
                    text = text.trim(),
                    language = language.ifBlank { "auto" }
                )
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

    fun removeTranslation(item: TranslationHistory) = viewModelScope.launch {
        db.translationHistoryDao().delete(item.id)
    }

    fun clearAllHistory() = viewModelScope.launch {
        db.translationHistoryDao().clear()
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
            "Could not download language model. Connect to Wi-Fi/data to fetch offline translation pack."
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

private enum class LanguageTab(val label: String, val icon: ImageVector) {
    Vocab("Vocabulary", Icons.AutoMirrored.Rounded.MenuBook),
    Grammar("Grammar", Icons.Rounded.School),
    Practice("Spaced Review", Icons.Rounded.Psychology),
    Translate("Translate", Icons.Rounded.Translate),
    History("History", Icons.Rounded.History)
}

@Composable
fun LanguageScreen(vm: LanguageViewModel = viewModel()) {
    val context = LocalContext.current
    val words by vm.words.collectAsState()
    val notes by vm.notes.collectAsState()
    val reviewStates by vm.reviewStates.collectAsState()
    val history by vm.history.collectAsState()
    val translationState by vm.translationState.collectAsState()
    val proofreadingState by vm.proofreadingState.collectAsState()

    var selectedTab by remember { mutableStateOf(LanguageTab.Vocab) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog & sheet states
    var wordDialogOpen by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<VocabularyWord?>(null) }
    var noteDialogOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<GrammarNote?>(null) }

    // Translation form states: English (From) to Urdu (To) by default as requested
    var sourceLanguage by remember { mutableStateOf("en") }
    var targetLanguage by remember { mutableStateOf("ur") }
    var translationText by remember { mutableStateOf("") }

    // Real-time review calculations
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val reviewByWord = remember(reviewStates) { reviewStates.associateBy { it.wordId } }
    val dueWords = remember(words, reviewByWord, now) {
        words.filter { word ->
            !word.mastered && (reviewByWord[word.id]?.nextReviewAt ?: 0L) <= now
        }.sortedBy { reviewByWord[it.id]?.nextReviewAt ?: 0L }
    }
    val masteredCount = remember(words) { words.count { it.mastered } }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Hero Studio Summary Banner
        LanguageHeroHeader(
            totalWords = words.size,
            masteredWords = masteredCount,
            dueReviews = dueWords.size,
            totalNotes = notes.size,
            onReviewClick = {
                selectedTab = LanguageTab.Practice
            }
        )

        // Segmented Horizontal Navigation with Icons and Badges
        LanguageTabBar(
            selectedTab = selectedTab,
            dueCount = dueWords.size,
            historyCount = history.size,
            onTabSelected = { tab ->
                selectedTab = tab
                searchQuery = ""
            }
        )

        Spacer(Modifier.height(8.dp))

        // Main Tab Content Area
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                LanguageTab.Vocab -> {
                    VocabularySection(
                        words = words,
                        reviewMap = reviewByWord,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editingWord = null
                            wordDialogOpen = true
                        },
                        onEdit = {
                            editingWord = it
                            wordDialogOpen = true
                        },
                        onDelete = vm::removeWord,
                        onToggleMastered = { word ->
                            val newMastered = !word.mastered
                            vm.setMastered(word, newMastered)
                            Toast.makeText(
                                context,
                                if (newMastered) "Marked as mastered! 🎉" else "Moved to active review",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                LanguageTab.Grammar -> {
                    GrammarSection(
                        notes = notes,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onAdd = {
                            editingNote = null
                            noteDialogOpen = true
                        },
                        onEdit = {
                            editingNote = it
                            noteDialogOpen = true
                        },
                        onDelete = vm::removeNote,
                        onTogglePin = { note ->
                            vm.togglePinned(note)
                        }
                    )
                }
                LanguageTab.Practice -> {
                    PracticeSection(
                        dueWords = dueWords,
                        reviewMap = reviewByWord,
                        onReview = { word, correct ->
                            vm.review(word, correct)
                        },
                        onMarkMastered = { word ->
                            vm.setMastered(word, true)
                            Toast.makeText(context, "Congratulations on mastering this word!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                LanguageTab.Translate -> {
                    TranslateSection(
                        source = sourceLanguage,
                        target = targetLanguage,
                        text = translationText,
                        state = translationState,
                        proofreadingState = proofreadingState,
                        onSourceChange = { sourceLanguage = it; vm.clearTranslationState(); vm.clearProofreadingState() },
                        onTargetChange = { targetLanguage = it; vm.clearTranslationState() },
                        onTextChange = { translationText = it; vm.clearProofreadingState() },
                        onSwap = {
                            if (sourceLanguage != LanguageTranslator.AUTO_DETECT) {
                                val temp = sourceLanguage
                                sourceLanguage = targetLanguage
                                targetLanguage = temp
                            }
                            val lastResult = (translationState as? TranslationUiState.Result)?.item?.translatedText
                            if (!lastResult.isNullOrBlank()) {
                                translationText = lastResult
                            }
                            vm.clearTranslationState()
                            vm.clearProofreadingState()
                        },
                        onTranslate = {
                            vm.translate(translationText, sourceLanguage, targetLanguage)
                        },
                        onProofread = {
                            vm.proofread(translationText, sourceLanguage)
                        },
                        onUseCorrection = { corrected ->
                            translationText = corrected
                            vm.clearProofreadingState()
                            vm.clearTranslationState()
                        },
                        onClearText = {
                            translationText = ""
                            vm.clearTranslationState()
                            vm.clearProofreadingState()
                        },
                        onSaveAsWord = { textToSave, translatedMeaning, srcLang ->
                            editingWord = VocabularyWord(
                                word = textToSave,
                                meaning = translatedMeaning,
                                language = if (srcLang == LanguageTranslator.AUTO_DETECT) "en" else srcLang
                            )
                            wordDialogOpen = true
                        }
                    )
                }
                LanguageTab.History -> {
                    HistorySection(
                        history = history,
                        onFavorite = vm::setFavorite,
                        onDelete = vm::removeTranslation,
                        onClearAll = {
                            vm.clearAllHistory()
                            Toast.makeText(context, "Translation history cleared", Toast.LENGTH_SHORT).show()
                        },
                        onReuse = { item ->
                            sourceLanguage = item.sourceLanguage
                            targetLanguage = item.targetLanguage
                            translationText = item.sourceText
                            selectedTab = LanguageTab.Translate
                            vm.clearTranslationState()
                        },
                        onSaveAsWord = { item ->
                            editingWord = VocabularyWord(
                                word = item.sourceText,
                                meaning = item.translatedText,
                                language = item.sourceLanguage
                            )
                            wordDialogOpen = true
                        }
                    )
                }
            }
        }
    }

    // Modern Editors
    if (wordDialogOpen) {
        WordEditorDialog(
            initial = editingWord,
            onDismiss = { wordDialogOpen = false },
            onSave = { saved ->
                vm.saveWord(saved)
                wordDialogOpen = false
                Toast.makeText(context, "Vocabulary saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (noteDialogOpen) {
        GrammarEditorDialog(
            initial = editingNote,
            onDismiss = { noteDialogOpen = false },
            onSave = { saved ->
                vm.saveNote(saved)
                noteDialogOpen = false
                Toast.makeText(context, "Grammar rule saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Header & Tabs                                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun LanguageHeroHeader(
    totalWords: Int,
    masteredWords: Int,
    dueReviews: Int,
    totalNotes: Int,
    onReviewClick: () -> Unit
) {
    PremiumCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        accent = AppModule.Language.accent,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LANGUAGE STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Leitner SRS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Mastery & Fluency Hub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Metrics Grid (Safe 4-column balanced layout with clean padding)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageStatCard(
                title = "Words",
                value = "$totalWords",
                subtitle = "$masteredWords mastered",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            LanguageStatCard(
                title = "Due Now",
                value = "$dueReviews",
                subtitle = if (dueReviews > 0) "Needs review" else "All caught up",
                color = if (dueReviews > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                onClick = if (dueReviews > 0) onReviewClick else null,
                modifier = Modifier.weight(1f)
            )
            LanguageStatCard(
                title = "Rules",
                value = "$totalNotes",
                subtitle = "Grammar bank",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LanguageStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LanguageTabBar(
    selectedTab: LanguageTab,
    dueCount: Int,
    historyCount: Int,
    onTabSelected: (LanguageTab) -> Unit
) {
    val tabs = remember { LanguageTab.values().toList() }
    val scrollState = rememberScrollState()

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val activeColor = MaterialTheme.colorScheme.primary
            val bgColor by animateColorAsState(
                if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                label = "tab_bg"
            )
            val contentColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "tab_content"
            )

            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(
                        1.dp,
                        if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (tab == LanguageTab.Practice && dueCount > 0) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "$dueCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onError
                        )
                    }
                } else if (tab == LanguageTab.History && historyCount > 0) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "$historyCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Tab 1: Vocabulary Section                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun VocabularySection(
    words: List<VocabularyWord>,
    reviewMap: Map<Long, ReviewState>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (VocabularyWord) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleMastered: (VocabularyWord) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Due", "Mastered", "Learning")

    val filteredWords = remember(words, query, selectedFilter, reviewMap) {
        val now = System.currentTimeMillis()
        words.filter { word ->
            val matchesQuery = query.isBlank() ||
                word.word.contains(query, ignoreCase = true) ||
                word.meaning.contains(query, ignoreCase = true) ||
                word.tags.contains(query, ignoreCase = true) ||
                word.partOfSpeech.contains(query, ignoreCase = true) ||
                word.example.contains(query, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Due" -> !word.mastered && (reviewMap[word.id]?.nextReviewAt ?: 0L) <= now
                "Mastered" -> word.mastered
                "Learning" -> !word.mastered
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Modern integrated Search Bar + Add Button
        SearchAndFilterHeader(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search vocabulary, meanings, tags…",
            addLabel = "Add Word",
            onAdd = onAdd
        )

        Spacer(Modifier.height(8.dp))

        // Quick Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(filters) { filter ->
                val isSelected = filter == selectedFilter
                val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val chipContent = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBg)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        filter,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = chipContent
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredWords.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    if (query.isBlank() && selectedFilter == "All") "Start your Word Bank" else "No matching words",
                    if (query.isBlank() && selectedFilter == "All") {
                        "Save vocabulary with context and translations. Review them daily to build long-term retention."
                    } else {
                        "Try a different search keyword or switch the filter."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredWords, key = { it.id }) { word ->
                    val review = reviewMap[word.id]
                    VocabularyCard(
                        word = word,
                        review = review,
                        onEdit = { onEdit(word) },
                        onDelete = { onDelete(word.id) },
                        onToggleMastered = { onToggleMastered(word) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabularyCard(
    word: VocabularyWord,
    review: ReviewState?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleMastered: () -> Unit
) {
    val context = LocalContext.current
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Top Bar: Word, Language Pill, Mastered Star, Edit & Delete Actions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            word.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                languageName(word.language).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (word.partOfSpeech.isNotBlank()) {
                        Text(
                            word.partOfSpeech.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action icons grouped cleanly with no overlap
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Word", "${word.word}: ${word.meaning}")
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, "Copied word & meaning", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy word",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onToggleMastered,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (word.mastered) Icons.Rounded.CheckCircle else Icons.Rounded.Check,
                            contentDescription = if (word.mastered) "Mark as learning" else "Mark as mastered",
                            tint = if (word.mastered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit word",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { deleteConfirmOpen = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete word",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Meaning section
            Text(
                word.meaning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Example block with subtle background quote appearance
            if (word.example.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "“${word.example}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom metadata: Tags & Review Status Badge
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tags
                if (word.tags.isNotBlank()) {
                    Text(
                        word.tags.split(",").joinToString(" ") { "#${it.trim()}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // SRS Leitner Stage Indicator
                val box = review?.box ?: -1
                val stageText = when {
                    word.mastered -> "Mastered"
                    box < 0 -> "Stage: New"
                    else -> "Stage: Box ${box + 1}/5"
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (word.mastered) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        stageText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (word.mastered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete Vocabulary?") },
            text = { Text("Are you sure you want to delete '${word.word}'? Its review history will also be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Tab 2: Grammar Section                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun GrammarSection(
    notes: List<GrammarNote>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (GrammarNote) -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePin: (GrammarNote) -> Unit
) {
    val categories = remember(notes) {
        listOf("All") + notes.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredNotes = remember(notes, query, selectedCategory) {
        notes.filter { note ->
            val matchesQuery = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.explanation.contains(query, ignoreCase = true) ||
                note.correctExample.contains(query, ignoreCase = true) ||
                note.incorrectExample.contains(query, ignoreCase = true) ||
                note.category.contains(query, ignoreCase = true) ||
                note.tags.contains(query, ignoreCase = true)

            val matchesCategory = selectedCategory == "All" || note.category.equals(selectedCategory, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SearchAndFilterHeader(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search grammar rules, explanations…",
            addLabel = "Add Rule",
            onAdd = onAdd
        )

        if (categories.size > 2) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val chipContent = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = chipContent
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredNotes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.Lightbulb,
                    if (query.isBlank()) "No grammar notes yet" else "No matching notes",
                    if (query.isBlank()) "Record rules, sentence patterns, and tricky common mistakes to review them anytime."
                    else "Try a different search or clear the filter."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    GrammarCard(
                        note = note,
                        onEdit = { onEdit(note) },
                        onDelete = { onDelete(note.id) },
                        onTogglePin = { onTogglePin(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GrammarCard(
    note: GrammarNote,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header Row: Category Badge, Pin State, Actions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (note.pinned) {
                            Icon(
                                Icons.Rounded.Bookmark,
                                contentDescription = "Pinned note",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                note.category.ifBlank { "Grammar Rule" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (note.commonMistake) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Watch out",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (note.pinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit rule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { deleteConfirmOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete rule",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (note.explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    note.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // High-contrast Correct vs Avoid comparison cards
            if (note.correctExample.isNotBlank() || note.incorrectExample.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (note.correctExample.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                note.correctExample,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (note.incorrectExample.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                note.incorrectExample,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (note.tags.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    note.tags.split(",").joinToString(" ") { "#${it.trim()}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete Grammar Note?") },
            text = { Text("Are you sure you want to delete '${note.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Tab 3: Spaced Review Section (SRS Flashcards)                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun PracticeSection(
    dueWords: List<VocabularyWord>,
    reviewMap: Map<Long, ReviewState>,
    onReview: (VocabularyWord, Boolean) -> Unit,
    onMarkMastered: (VocabularyWord) -> Unit
) {
    val activeWord = dueWords.firstOrNull()
    var isRevealed by remember(activeWord?.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Review Progress Bar Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Spaced Repetition Practice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (dueWords.isNotEmpty()) "${dueWords.size} words waiting for review" else "Study queue empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.FlashOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${dueWords.size} due",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (activeWord == null) {
            PremiumCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(28.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        "You're all caught up!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Great job completing your reviews! Check back later or add new words to your vocabulary bank to continue expanding your skills.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val review = reviewMap[activeWord.id]
            val box = review?.box ?: -1

            // Flashcard
            PremiumCard(
                Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Flashcard Header: Language code & Leitner Level
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                languageName(activeWord.language).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            if (box < 0) "New Word" else "Interval Box ${box + 1}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Front of card (Prompt)
                    Text(
                        activeWord.word,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (activeWord.partOfSpeech.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            activeWord.partOfSpeech,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))

                    // Back of card (Answer / Revealed info)
                    if (isRevealed) {
                        Text(
                            "MEANING & TRANSLATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            activeWord.meaning,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (activeWord.example.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "“${activeWord.example}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        // Review decision action buttons (Spaced comfortably, no overlap)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onReview(activeWord, false)
                                    isRevealed = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Forgot")
                            }

                            Button(
                                onClick = {
                                    onReview(activeWord, true)
                                    isRevealed = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remembered")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                onMarkMastered(activeWord)
                                isRevealed = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mark permanently mastered")
                        }
                    } else {
                        PrimaryButton(
                            text = "Reveal Meaning",
                            icon = Icons.Rounded.Lightbulb,
                            onClick = { isRevealed = true }
                        )
                    }
                }
            }

            // Leitner info tip
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Spaced repetition schedules words across 1, 2, 4, 8, and 16 day intervals to optimize long-term memory consolidation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Tab 4: Translate Section (360° Redesign with Quick Actions)              */
/* ------------------------------------------------------------------------- */

@Composable
private fun TranslateSection(
    source: String,
    target: String,
    text: String,
    state: TranslationUiState,
    proofreadingState: ProofreadingUiState,
    onSourceChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSwap: () -> Unit,
    onTranslate: () -> Unit,
    onProofread: () -> Unit,
    onUseCorrection: (String) -> Unit,
    onClearText: () -> Unit,
    onSaveAsWord: (text: String, meaning: String, srcLang: String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val languages = remember {
        TranslateLanguage.getAllLanguages().sortedBy { languageName(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "On-Device Translator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Private & offline capable after first download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Offline Ready",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Language Selectors with Center Swap Button
        PremiumCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModernLanguagePicker(
                    label = "From",
                    selected = source,
                    languages = listOf(LanguageTranslator.AUTO_DETECT) + languages,
                    onSelect = onSourceChange,
                    modifier = Modifier.weight(1f)
                )

                FilledIconButton(
                    onClick = onSwap,
                    enabled = source != LanguageTranslator.AUTO_DETECT,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = "Swap Languages",
                        modifier = Modifier.size(20.dp)
                    )
                }

                ModernLanguagePicker(
                    label = "To",
                    selected = target,
                    languages = languages,
                    onSelect = onTargetChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Input Box with Character Counter & Clear Action
        PremiumCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Input Text",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (text.isNotEmpty()) {
                        Text(
                            "${text.length}/5000",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { onTextChange(it.take(5_000)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = {
                        Text("Type, paste or enter words or sentences…")
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = onClearText) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear text", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
        }

        OutlinedButton(
            onClick = onProofread,
            enabled = text.isNotBlank() && proofreadingState !is ProofreadingUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (proofreadingState is ProofreadingUiState.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking spelling & grammar…")
            } else {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (proofreadingState is ProofreadingUiState.Error) "Retry spelling & grammar check" else "Check & fix spelling and grammar")
            }
        }
        Text(
            "Proofreading is online. Your text is sent to LanguageTool only when you tap this button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { uriHandler.openUri("https://languagetool.org") },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text("About LanguageTool")
        }

        when (val check = proofreadingState) {
            is ProofreadingUiState.Checked -> {
                if (check.result.correctionsApplied == 0) {
                    Text(
                        "No spelling or grammar corrections suggested.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth(),
                        accent = MaterialTheme.colorScheme.tertiary,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Text("SUGGESTED CORRECTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(check.result.correctedText, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            text = "Use corrected text",
                            onClick = { onUseCorrection(check.result.correctedText) }
                        )
                    }
                }
            }
            is ProofreadingUiState.Error -> Text(
                check.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            else -> Unit
        }

        // Action Button
        PrimaryButton(
            text = if (state is TranslationUiState.Loading) "Translating…" else "Translate Now",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = onTranslate,
            enabled = text.isNotBlank() && source != target && state !is TranslationUiState.Loading,
            loading = state is TranslationUiState.Loading
        )

        if (source == target) {
            Text(
                "Source and target languages must be different.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Translation Result Card
        when (state) {
            is TranslationUiState.Result -> {
                val item = state.item
                val meanings = state.multipleMeanings

                PremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${languageName(item.sourceLanguage)} → ${languageName(item.targetLanguage)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val clip = ClipData.newPlainText("Translation", item.translatedText)
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                        Toast.makeText(context, "Translation copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy result", modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        onSaveAsWord(item.sourceText, item.translatedText, item.sourceLanguage)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Save to Vocabulary", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.translatedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // If multiple meanings / dictionary parts of speech are available, display them clearly
                        if (meanings.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(10.dp))

                            Text(
                                "MULTIPLE MEANINGS & DEFINITIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(Modifier.height(6.dp))

                            meanings.forEach { group ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    if (group.partOfSpeech.isNotBlank()) {
                                        Text(
                                            group.partOfSpeech,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }

                                    // Stack meanings so long translations wrap instead of running off-screen.
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        group.definitions.take(6).forEach { def ->
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                    .clickable {
                                                        val clip = ClipData.newPlainText("Meaning", def)
                                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                                        Toast.makeText(context, "Copied '$def'", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    def,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(
                            text = "Save as Vocabulary Word",
                            icon = Icons.Rounded.Bookmark,
                            onClick = {
                                onSaveAsWord(item.sourceText, item.translatedText, item.sourceLanguage)
                            }
                        )
                    }
                }
            }
            is TranslationUiState.Error -> {
                PremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.error,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Translation Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernLanguagePicker(
    label: String,
    selected: String,
    languages: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (selected == LanguageTranslator.AUTO_DETECT) "Auto Detect" else languageName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (lang == LanguageTranslator.AUTO_DETECT) "Auto Detect" else languageName(lang),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onSelect(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Tab 5: History Section                                                    */
/* ------------------------------------------------------------------------- */

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
    var clearConfirmOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${history.size} saved translations",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { clearConfirmOpen = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.History,
                    "No translation history",
                    "Recent on-device translations will appear here for fast lookup and 1-tap word saving."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${languageName(item.sourceLanguage)} → ${languageName(item.targetLanguage)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val clip = ClipData.newPlainText("Translation", item.translatedText)
                                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onFavorite(item) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = if (item.favorite) "Remove favorite" else "Favorite",
                                            tint = if (item.favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onSaveAsWord(item) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = "Add to vocabulary",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDelete(item) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Clickable content to reuse in translate tab
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onReuse(item) }
                            ) {
                                Text(
                                    item.sourceText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.translatedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (clearConfirmOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmOpen = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all saved translations from your local history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmOpen = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Search & Filter Component                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun SearchAndFilterHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    addLabel: String,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(120)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        FilledIconButton(
            onClick = onAdd,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.Add, contentDescription = addLabel, modifier = Modifier.size(24.dp))
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Modern Editor Dialogs                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun WordEditorDialog(
    initial: VocabularyWord?,
    onDismiss: () -> Unit,
    onSave: (VocabularyWord) -> Unit
) {
    var word by remember(initial) { mutableStateOf(initial?.word.orEmpty()) }
    var meaning by remember(initial) { mutableStateOf(initial?.meaning.orEmpty()) }
    var language by remember(initial) { mutableStateOf(initial?.language ?: "en") }
    var example by remember(initial) { mutableStateOf(initial?.example.orEmpty()) }
    var partOfSpeech by remember(initial) { mutableStateOf(initial?.partOfSpeech.orEmpty()) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add Vocabulary Word" else "Edit Vocabulary Word",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it.take(100) },
                    label = { Text("Word or Phrase *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it.take(300) },
                    label = { Text("Meaning or Translation *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it.take(12) },
                        label = { Text("Language Code") },
                        placeholder = { Text("es, fr, de…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = partOfSpeech,
                        onValueChange = { partOfSpeech = it.take(40) },
                        label = { Text("Part of Speech") },
                        placeholder = { Text("noun, verb…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it.take(300) },
                    label = { Text("Usage Example") },
                    placeholder = { Text("Add a sentence using this word in context") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(120) },
                    label = { Text("Tags") },
                    placeholder = { Text("food, travel, business (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
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
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Word")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
        title = {
            Text(
                if (initial == null) "Add Grammar Rule" else "Edit Grammar Rule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Rule Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(40) },
                    label = { Text("Category") },
                    placeholder = { Text("Tenses, Prepositions, Articles…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it.take(1_000) },
                    label = { Text("Explanation & Rules") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = correctExample,
                    onValueChange = { correctExample = it.take(300) },
                    label = { Text("Correct Usage Example") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = incorrectExample,
                    onValueChange = { incorrectExample = it.take(300) },
                    label = { Text("Common Mistake to Avoid") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = commonMistake,
                        onCheckedChange = { commonMistake = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Highlight as frequent pitfall / common mistake", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(120) },
                    label = { Text("Tags") },
                    placeholder = { Text("basics, a2, b1 (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: GrammarNote(title = "")).copy(
                            title = title,
                            category = category.ifBlank { "Grammar Rule" },
                            explanation = explanation,
                            correctExample = correctExample,
                            incorrectExample = incorrectExample,
                            commonMistake = commonMistake,
                            tags = tags,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/* ------------------------------------------------------------------------- */
/* Helper Functions                                                          */
/* ------------------------------------------------------------------------- */

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage().ifBlank { code }.replaceFirstChar { it.uppercase() }
