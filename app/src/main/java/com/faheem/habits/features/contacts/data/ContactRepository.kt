package com.faheem.habits.features.contacts.data

import com.faheem.habits.core.data.Contact
import com.faheem.habits.core.data.ContactDao
import com.faheem.habits.features.contacts.ImportedContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal data class ContactImportSummary(
    val imported: Int,
    val skippedContacts: Int,
    val duplicateNumbers: Int
)

class ContactRepository @Inject constructor(
    private val dao: ContactDao
) {
    val contacts: Flow<List<Contact>> = dao.getAll()

    suspend fun save(contact: Contact) {
        val normalized = contact.copy(
            name = contact.name.trim(),
            updatedAt = System.currentTimeMillis(),
            dirty = true
        )
        if (normalized.id == 0) dao.insert(normalized) else dao.update(normalized)
    }

    suspend fun delete(contact: Contact) {
        dao.softDelete(contact.id)
    }

    internal suspend fun importContacts(rows: List<ImportedContact>): ContactImportSummary {
        val existing = dao.getAll().first()
        val existingPhoneKeys = existing.flatMap { phoneNumbers(it.phone) }
            .map(::phoneKey).filter(String::isNotBlank).toMutableSet()
        val emailKeys = existing.map { it.email.trim().lowercase() }
            .filter(String::isNotBlank).toMutableSet()
        var imported = 0
        var skippedContacts = 0
        var duplicateNumbers = 0

        rows.forEach { row ->
            val uniqueNumbers = row.phoneNumbers.filter { number ->
                val key = phoneKey(number)
                if (key.isBlank()) return@filter false
                if (existingPhoneKeys.add(key)) true else {
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
                dao.insert(
                    Contact(
                        name = row.name,
                        phone = uniqueNumbers.joinToString("\n"),
                        email = row.email,
                        address = row.address,
                        notes = row.notes
                    )
                )
                imported++
            }
        }
        return ContactImportSummary(imported, skippedContacts, duplicateNumbers)
    }
}

private fun phoneNumbers(value: String): List<String> =
    value.split(Regex("[\\r\\n]+")).map(String::trim).filter(String::isNotBlank).distinct()

private fun phoneKey(value: String): String = value.filter(Char::isDigit)
