package com.faheem.habits.features.habits

import com.faheem.habits.core.data.ALL_DAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTemplatesTest {
    @Test
    fun libraryContainsMoreThanTwoHundredUniqueValidTemplates() {
        assertTrue("The library should contain more than 200 habits", HabitTemplates.size > 200)
        assertEquals(
            HabitTemplates.size,
            HabitTemplates.map { it.name.lowercase() }.distinct().size
        )
        assertTrue(HabitTemplates.all { it.category in TemplateCategories })
        assertTrue(HabitTemplates.all { it.icon in HabitIcons })
        assertTrue(HabitTemplates.all { it.timeOfDay in TIME_LABELS.indices })
        assertTrue(HabitTemplates.all { it.daysMask in 1..ALL_DAYS })
    }

    @Test
    fun availableAndAddedTemplatesCanBeFilteredIgnoringCaseAndWhitespace() {
        val templates = listOf(
            HabitTemplate("Health", "Morning walk", "Walk outside", "walk", 1, ALL_DAYS),
            HabitTemplate("Mind", "Read a page", "Read a few minutes", "book", 3, ALL_DAYS)
        )

        assertEquals(
            listOf("Read a page"),
            filterHabitTemplatesByAddedState(
                templates,
                setOf("  MORNING WALK "),
                showAdded = false
            )
                .map { it.name }
        )
        assertEquals(
            listOf("Morning walk"),
            filterHabitTemplatesByAddedState(
                templates,
                setOf("  MORNING WALK "),
                showAdded = true
            )
                .map { it.name }
        )
    }
}
