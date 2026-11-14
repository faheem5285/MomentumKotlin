package com.faheem.habits.features.contacts

internal data class ImportedContact(
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val email: String = "",
    val address: String = "",
    val notes: String = ""
)
