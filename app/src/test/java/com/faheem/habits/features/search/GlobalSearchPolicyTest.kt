package com.faheem.habits.features.search

import com.faheem.habits.core.ui.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchPolicyTest {
    private val visibleModules = GlobalSearchCategory.entries.associate { it.module.name to true }

    private fun document(
        category: GlobalSearchCategory,
        id: Int,
        title: String,
        fields: List<String> = emptyList(),
        available: Boolean = true
    ) = GlobalSearchDocument(
        category = category,
        id = id,
        title = title,
        subtitle = category.label,
        searchableText = fields,
        available = available
    )

    @Test
    fun blankAndOversizedQueriesAreBounded() {
        assertEquals("", GlobalSearchPolicy.normalizeQuery("   \n  "))
        assertTrue(GlobalSearchPolicy.isBlank("   "))
        assertEquals("alpha beta", GlobalSearchPolicy.normalizeQuery("  alpha   beta  "))
        assertEquals(120, GlobalSearchPolicy.normalizeQuery("x".repeat(500)).length)
    }

    @Test
    fun matchingRequiresEveryTokenAcrossIndexedFields() {
        val doc = document(
            GlobalSearchCategory.TASKS,
            7,
            "Renew passport",
            listOf("Travel documents", "Personal")
        )
        assertTrue(GlobalSearchPolicy.matches(doc, "passport travel"))
        assertFalse(GlobalSearchPolicy.matches(doc, "passport missing"))
        assertFalse(GlobalSearchPolicy.matches(doc, "  "))
    }

    @Test
    fun hiddenModulesAreExcludedFromResultsAndReported() {
        val habit = document(GlobalSearchCategory.HABITS, 1, "Read", listOf("Daily reading"))
        val outcome = GlobalSearchPolicy.search(
            listOf(habit),
            "read",
            visibleModules + (AppModule.Habits.name to false)
        )
        assertTrue(outcome.results.isEmpty())
        assertEquals(setOf(GlobalSearchCategory.HABITS), outcome.hiddenCategories)
    }

    @Test
    fun unreadableFilesBecomePermissionDeniedState() {
        val file = document(
            GlobalSearchCategory.FILES,
            3,
            "scan.pdf",
            listOf("contracts"),
            available = false
        )
        val outcome = GlobalSearchPolicy.search(listOf(file), "scan", visibleModules)
        assertTrue(outcome.results.isEmpty())
        assertTrue(outcome.permissionDenied)
    }

    @Test
    fun vaultIsNotASearchCategoryAndResultsAreScoped() {
        assertFalse(GlobalSearchCategory.entries.any { it.name.contains("VAULT") })
        val documents = listOf(
            document(GlobalSearchCategory.HABITS, 1, "Walk"),
            document(GlobalSearchCategory.CONTACTS, 2, "Walker", listOf("phone")),
            document(GlobalSearchCategory.LIFE_RULES, 3, "Rest")
        )
        val outcome = GlobalSearchPolicy.search(documents, "walk", visibleModules)
        assertEquals(listOf(1, 2), outcome.results.map { it.id })
        assertEquals(AppModule.Habits, outcome.results.first().module)
    }

    @Test
    fun resultCountIsBounded() {
        val documents = (1..120).map { document(GlobalSearchCategory.TASKS, it, "Task $it") }
        val outcome = GlobalSearchPolicy.search(documents, "task", visibleModules)
        assertEquals(50, outcome.results.size)
    }
}
