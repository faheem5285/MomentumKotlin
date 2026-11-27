package com.faheem.habits.features.search

import com.faheem.habits.core.ui.AppModule

/** Categories intentionally exclude Vault and its encrypted contents. */
enum class GlobalSearchCategory(val label: String, val module: AppModule) {
    HABITS("Habit", AppModule.Habits),
    TASKS("Task", AppModule.ToDos),
    EXPENSES("Expense", AppModule.Money),
    CONTACTS("Contact", AppModule.Contacts),
    FILES("File", AppModule.Files),
    LIFE_RULES("Life rule", AppModule.LifeRules)
}

data class GlobalSearchDocument(
    val category: GlobalSearchCategory,
    val id: Int,
    val title: String,
    val subtitle: String,
    val searchableText: List<String>,
    val available: Boolean = true
)

data class GlobalSearchResult(
    val category: GlobalSearchCategory,
    val id: Int,
    val title: String,
    val subtitle: String,
    val module: AppModule
)

data class GlobalSearchOutcome(
    val results: List<GlobalSearchResult> = emptyList(),
    val hiddenCategories: Set<GlobalSearchCategory> = emptySet(),
    val permissionDenied: Boolean = false
)

object GlobalSearchPolicy {
    private const val MAX_QUERY_LENGTH = 120
    private const val MAX_RESULTS = 50

    /** Blank, oversized and whitespace-heavy queries never create a broad scan. */
    fun normalizeQuery(raw: String): String = raw.trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_QUERY_LENGTH)

    fun isBlank(raw: String): Boolean = normalizeQuery(raw).isEmpty()

    fun matches(document: GlobalSearchDocument, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return false
        val haystack = (listOf(document.title, document.subtitle) + document.searchableText)
            .joinToString(" ") { it.lowercase() }
        return normalizedQuery.lowercase().split(' ').all { token -> haystack.contains(token) }
    }

    /**
     * Hidden modules are excluded from results rather than merely hidden in the
     * UI, so a search result cannot become a back door to a hidden module.
     */
    fun search(
        documents: List<GlobalSearchDocument>,
        normalizedQuery: String,
        moduleVisibility: Map<String, Boolean>
    ): GlobalSearchOutcome {
        if (normalizedQuery.isBlank()) return GlobalSearchOutcome()
        val hidden = mutableSetOf<GlobalSearchCategory>()
        var permissionDenied = false
        val results = mutableListOf<GlobalSearchResult>()

        for (document in documents) {
            val module = document.category.module
            if (moduleVisibility[module.name] == false) {
                if (matches(document, normalizedQuery)) hidden += document.category
                continue
            }
            if (!matches(document, normalizedQuery)) continue
            if (!document.available) {
                if (document.category == GlobalSearchCategory.FILES) permissionDenied = true
                continue
            }
            results += GlobalSearchResult(
                category = document.category,
                id = document.id,
                title = document.title,
                subtitle = document.subtitle,
                module = module
            )
            if (results.size >= MAX_RESULTS) break
        }
        return GlobalSearchOutcome(results, hidden, permissionDenied)
    }
}

enum class GlobalSearchStatus { BLANK, LOADING, RESULTS, EMPTY, ERROR }

data class GlobalSearchUiState(
    val query: String = "",
    val status: GlobalSearchStatus = GlobalSearchStatus.BLANK,
    val results: List<GlobalSearchResult> = emptyList(),
    val hiddenCategories: Set<GlobalSearchCategory> = emptySet(),
    val permissionDenied: Boolean = false,
    val errorMessage: String? = null
)

object GlobalSearchStateMapper {
    fun fromQuery(query: String, outcome: GlobalSearchOutcome?): GlobalSearchUiState {
        val normalized = GlobalSearchPolicy.normalizeQuery(query)
        if (normalized.isEmpty()) return GlobalSearchUiState(query = query)
        if (outcome == null) return GlobalSearchUiState(
            query = query,
            status = GlobalSearchStatus.LOADING
        )
        return GlobalSearchUiState(
            query = query,
            status = if (outcome.results.isEmpty()) GlobalSearchStatus.EMPTY else GlobalSearchStatus.RESULTS,
            results = outcome.results,
            hiddenCategories = outcome.hiddenCategories,
            permissionDenied = outcome.permissionDenied
        )
    }

    fun error(query: String, message: String): GlobalSearchUiState = GlobalSearchUiState(
        query = query,
        status = GlobalSearchStatus.ERROR,
        errorMessage = message
    )
}
