package com.faheem.habits.features.loans.data

import android.content.Context
import android.net.Uri
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanAttachment
import com.faheem.habits.core.data.LoanDao
import com.faheem.habits.core.data.LoanPerson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Suppress("TooManyFunctions")
class LoanRepository @Inject constructor(
    private val dao: LoanDao,
    @ApplicationContext private val context: Context
) {
    val loans: Flow<List<Loan>> = dao.getAll()
    val people: Flow<List<LoanPerson>> = dao.getPeople()

    suspend fun addPerson(person: LoanPerson) {
        dao.insertPerson(person.copy(name = person.name.trim()))
    }

    suspend fun updatePerson(person: LoanPerson) {
        dao.updatePerson(
            person.copy(
                name = person.name.trim(),
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        )
    }

    suspend fun archivePerson(person: LoanPerson) {
        dao.archivePerson(person.id)
    }

    suspend fun deletePerson(person: LoanPerson) {
        dao.deletePerson(person.id)
    }

    suspend fun add(loan: Loan, proofUris: List<String>) {
        val normalized = loan.person.trim()
        val personId = resolvePersonId(normalized, loan.personId)
        val loanId = dao.insert(
            loan.copy(
                person = normalized,
                personId = personId,
                note = loan.note.trim(),
                title = loan.title.trim()
            )
        ).toInt()
        proofUris.forEach { insertAttachment(loanId, it) }
    }

    suspend fun updateLoan(loan: Loan, person: String, personId: Int?, proofUris: List<String>) {
        val normalized = person.trim()
        val resolvedPersonId = resolvePersonId(normalized, personId)
        dao.update(
            loan.copy(
                person = normalized,
                personId = resolvedPersonId,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                dirty = true
            )
        )
        proofUris.forEach { insertAttachment(loan.id, it) }
    }

    private suspend fun resolvePersonId(name: String, requestedId: Int?): Int {
        val selectedPerson = requestedId?.let { dao.getPerson(it) }
        return selectedPerson?.id
            ?: dao.getPeople().first().firstOrNull { it.name.equals(name, true) }?.id
            ?: dao.insertPerson(LoanPerson(name = name)).toInt()
    }

    suspend fun update(loan: Loan) {
        dao.update(loan.copy(updatedAt = System.currentTimeMillis(), deleted = false, dirty = true))
    }

    suspend fun addAttachment(loanId: Int, uri: String) {
        insertAttachment(loanId, uri)
    }

    private suspend fun insertAttachment(loanId: Int, uri: String) {
        val parsedUri = Uri.parse(uri)
        dao.insertAttachment(
            LoanAttachment(
                loanId = loanId,
                name = parsedUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { null }
                    ?: "Loan proof",
                uri = uri,
                mime = context.contentResolver.getType(parsedUri).orEmpty()
            )
        )
    }

    fun attachments(loanId: Int) = dao.getAttachments(loanId)

    suspend fun remove(loan: Loan) {
        val updatedAt = System.currentTimeMillis()
        dao.deleteAttachmentsForLoan(loan.id, updatedAt)
        dao.deletePaymentsForLoan(loan.id, updatedAt)
        dao.delete(loan.id, updatedAt)
    }
}
