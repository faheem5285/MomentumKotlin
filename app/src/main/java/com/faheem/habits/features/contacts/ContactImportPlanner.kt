package com.faheem.habits.features.contacts

import com.faheem.habits.core.data.Contact

data class ContactImportCounts(
    val imported: Int,
    val skippedContacts: Int,
    val duplicateNumbers: Int
)

data class ContactImportPlan(
    val contacts: List<Contact>,
    val counts: ContactImportCounts
)

internal fun phoneNumbers(value: String): List<String> =
    value.split(Regex("[\\r\\n]+")).map(String::trim).filter(String::isNotBlank).distinct()

internal fun phoneKey(value: String): String = value.filter(Char::isDigit)

/**
 * Pure import planning. Tombstoned contacts are included in [existing] so a duplicate cannot be
 * recreated by importing the same number or email again. The caller persists [contacts] atomically.
 */
internal fun planContactImport(existing: List<Contact>, rows: List<ImportedContact>): ContactImportPlan {
    val phoneKeys = existing.flatMap { phoneNumbers(it.phone) }
        .map(::phoneKey).filter(String::isNotBlank).toMutableSet()
    val emailKeys = existing.map { it.email.trim().lowercase() }
        .filter(String::isNotBlank).toMutableSet()
    val contacts = mutableListOf<Contact>()
    var skippedContacts = 0
    var duplicateNumbers = 0

    rows.forEach { row ->
        val uniqueNumbers = row.phoneNumbers.filter { number ->
            val key = phoneKey(number)
            if (key.isBlank()) return@filter false
            if (phoneKeys.add(key)) true else {
                duplicateNumbers++
                false
            }
        }
        val emailKey = row.email.trim().lowercase()
        val duplicateEmail = uniqueNumbers.isEmpty() && emailKey.isNotBlank() && !emailKeys.add(emailKey)
        if ((row.phoneNumbers.isNotEmpty() && uniqueNumbers.isEmpty()) || duplicateEmail) {
            skippedContacts++
        } else {
            if (emailKey.isNotBlank()) emailKeys.add(emailKey)
            contacts += Contact(
                name = row.name,
                phone = uniqueNumbers.joinToString("\n"),
                email = row.email,
                address = row.address,
                notes = row.notes
            )
        }
    }
    return ContactImportPlan(
        contacts = contacts,
        counts = ContactImportCounts(contacts.size, skippedContacts, duplicateNumbers)
    )
}
