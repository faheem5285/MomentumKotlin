package com.faheem.habits.features.contacts

import com.faheem.habits.core.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactImportPlannerTest {
    @Test
    fun tombstonedContactsStillBlockDuplicateNumbers() {
        val existing = listOf(
            Contact(name = "Deleted", phone = "555-1000", uuid = "deleted", deleted = true)
        )
        val rows = listOf(ImportedContact(name = "Again", phoneNumbers = listOf("5551000")))

        val plan = planContactImport(existing, rows)

        assertTrue(plan.contacts.isEmpty())
        assertEquals(1, plan.counts.skippedContacts)
        assertEquals(1, plan.counts.duplicateNumbers)
    }

    @Test
    fun duplicatesWithinOneBatchAreCountedAndSkipped() {
        val rows = listOf(
            ImportedContact(name = "First", phoneNumbers = listOf("555-1000")),
            ImportedContact(name = "Second", phoneNumbers = listOf("(555) 1000"))
        )

        val plan = planContactImport(emptyList(), rows)

        assertEquals(1, plan.contacts.size)
        assertEquals("First", plan.contacts.single().name)
        assertEquals(1, plan.counts.skippedContacts)
        assertEquals(1, plan.counts.duplicateNumbers)
    }

    @Test
    fun tombstonedEmailBlocksEmailOnlyImport() {
        val existing = listOf(
            Contact(name = "Deleted", email = "Old@Example.com", uuid = "deleted", deleted = true)
        )
        val rows = listOf(
            ImportedContact(name = "Duplicate email", email = "old@example.com"),
            ImportedContact(name = "New email", email = "new@example.com")
        )

        val plan = planContactImport(existing, rows)

        assertEquals(1, plan.contacts.size)
        assertEquals("New email", plan.contacts.single().name)
        assertEquals(1, plan.counts.skippedContacts)
        assertEquals(0, plan.counts.duplicateNumbers)
    }

    @Test
    fun contactImportSummaryUsesFixedCounts() {
        assertEquals(
            "Imported 1 contact. Skipped 2 duplicate contacts. 3 duplicate numbers detected.",
            contactImportSummary(ContactImportCounts(imported = 1, skippedContacts = 2, duplicateNumbers = 3))
        )
    }
}
