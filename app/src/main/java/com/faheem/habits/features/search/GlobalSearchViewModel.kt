package com.faheem.habits.features.search

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.Expense
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.LifeRule
import com.faheem.habits.core.data.ToDoItem
import com.faheem.habits.core.data.supabase.HttpErrorSanitizer
import com.faheem.habits.core.settings.MomentumSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    database: AppDatabase,
    settings: MomentumSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val retryTick = MutableStateFlow(0)
    private val moduleNames = GlobalSearchCategory.entries.map { it.module.name }.distinct()

    private val habitDocuments: Flow<List<GlobalSearchDocument>> = database.habitDao().getHabits()
        .map { habits -> habits.filterNot { it.deleted || it.archived }.map(::habitDocument) }
    private val taskDocuments: Flow<List<GlobalSearchDocument>> = database.toDoDao().getAll()
        .map { items -> items.filterNot { it.deleted }.map(::taskDocument) }
    private val expenseDocuments: Flow<List<GlobalSearchDocument>> = database.expenseDao().getAll()
        .map { items -> items.filterNot { it.deleted }.map(::expenseDocument) }

    private val contactDocuments: Flow<List<GlobalSearchDocument>> = database.contactDao().getAll()
        .map { contacts -> contacts.filterNot { it.deleted }.map(::contactDocument) }
    private val fileDocuments: Flow<List<GlobalSearchDocument>> = database.fileDao().getAll()
        .map { files ->
            files.filterNot { it.deleted }.map { file ->
                fileDocument(file, !file.missing && hasPersistedReadPermission(context, Uri.parse(file.uri)))
            }
        }
        .flowOn(Dispatchers.IO)
    private val lifeRuleDocuments: Flow<List<GlobalSearchDocument>> = database.lifeRuleDao().getAll()
        .map { rules -> rules.filterNot { it.deleted }.map(::lifeRuleDocument) }

    private val firstHalf = combine(habitDocuments, taskDocuments, expenseDocuments) { habits, tasks, expenses ->
        habits + tasks + expenses
    }
    private val secondHalf = combine(contactDocuments, fileDocuments, lifeRuleDocuments) { contacts, files, rules ->
        contacts + files + rules
    }
    private val documents = combine(firstHalf, secondHalf) { first, second -> first + second }

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        query.debounce(120).distinctUntilChanged(),
        documents,
        settings.moduleVisibility(moduleNames),
        retryTick
    ) { currentQuery, index, visibility, _ ->
        val normalized = GlobalSearchPolicy.normalizeQuery(currentQuery)
        if (normalized.isEmpty()) {
            GlobalSearchStateMapper.fromQuery(currentQuery, null)
        } else {
            GlobalSearchStateMapper.fromQuery(
                currentQuery,
                GlobalSearchPolicy.search(index, normalized, visibility)
            )
        }
    }.catch { failure ->
        emit(GlobalSearchStateMapper.error(query.value, HttpErrorSanitizer.forDisplay(failure.message)))
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GlobalSearchUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun retry() {
        retryTick.value++
    }

    private fun habitDocument(habit: Habit) = GlobalSearchDocument(
        category = GlobalSearchCategory.HABITS,
        id = habit.id,
        title = habit.name,
        subtitle = habit.category,
        searchableText = listOf(habit.name, habit.description, habit.category)
    )

    private fun taskDocument(item: ToDoItem) = GlobalSearchDocument(
        category = GlobalSearchCategory.TASKS,
        id = item.id,
        title = item.title,
        subtitle = item.category,
        searchableText = listOf(item.title, item.notes, item.category, item.project, item.labels)
    )

    private fun expenseDocument(expense: Expense) = GlobalSearchDocument(
        category = GlobalSearchCategory.EXPENSES,
        id = expense.id,
        title = expense.title,
        subtitle = expense.category,
        searchableText = listOf(expense.title, expense.category, expense.account)
    )

    private fun contactDocument(contact: com.faheem.habits.core.data.Contact) = GlobalSearchDocument(
        category = GlobalSearchCategory.CONTACTS,
        id = contact.id,
        title = contact.name,
        subtitle = GlobalSearchCategory.CONTACTS.label,
        searchableText = listOf(contact.name, contact.phone, contact.email, contact.address, contact.notes)
    )

    private fun fileDocument(file: FileItem, available: Boolean) = GlobalSearchDocument(
        category = GlobalSearchCategory.FILES,
        id = file.id,
        title = file.name,
        subtitle = file.folder.ifBlank { file.mime },
        searchableText = listOf(file.name, file.mime, file.tags, file.folder),
        available = available
    )

    private fun lifeRuleDocument(rule: LifeRule) = GlobalSearchDocument(
        category = GlobalSearchCategory.LIFE_RULES,
        id = rule.id,
        title = rule.title,
        subtitle = rule.kind,
        searchableText = listOf(rule.title, rule.body, rule.kind)
    )

    private fun hasPersistedReadPermission(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } catch (_: Exception) {
        false
    }
}
