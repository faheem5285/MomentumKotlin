package com.faheem.habits.features.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchStateMapperTest {
    @Test
    fun blankQueryDoesNotScan() {
        val state = GlobalSearchStateMapper.fromQuery("   ", null)
        assertEquals(GlobalSearchStatus.BLANK, state.status)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun pendingAndEmptyAreDistinct() {
        assertEquals(
            GlobalSearchStatus.LOADING,
            GlobalSearchStateMapper.fromQuery("milk", null).status
        )
        val empty = GlobalSearchStateMapper.fromQuery("milk", GlobalSearchOutcome())
        assertEquals(GlobalSearchStatus.EMPTY, empty.status)
        assertTrue(empty.results.isEmpty())
    }

    @Test
    fun resultsCarryScopeWarnings() {
        val outcome = GlobalSearchOutcome(
            results = listOf(
                GlobalSearchResult(
                    category = GlobalSearchCategory.TASKS,
                    id = 1,
                    title = "Buy milk",
                    subtitle = "Task · Personal",
                    module = com.faheem.habits.core.ui.AppModule.ToDos
                )
            ),
            hiddenCategories = setOf(GlobalSearchCategory.HABITS),
            permissionDenied = true
        )
        val state = GlobalSearchStateMapper.fromQuery(" milk ", outcome)
        assertEquals(GlobalSearchStatus.RESULTS, state.status)
        assertEquals(1, state.results.size)
        assertEquals(setOf(GlobalSearchCategory.HABITS), state.hiddenCategories)
        assertTrue(state.permissionDenied)
    }

    @Test
    fun errorStateKeepsSanitizedMessage() {
        val state = GlobalSearchStateMapper.error("milk", "Search could not be completed.")
        assertEquals(GlobalSearchStatus.ERROR, state.status)
        assertEquals("Search could not be completed.", state.errorMessage)
        assertNull(state.results.firstOrNull())
    }
}
