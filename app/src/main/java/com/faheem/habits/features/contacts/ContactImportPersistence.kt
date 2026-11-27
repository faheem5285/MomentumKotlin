package com.faheem.habits.features.contacts

import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ContactDao

/** Persists a planned import as one transaction so a failed insert cannot leave a partial batch. */
internal suspend fun persistContactImportPlan(db: AppDatabase, dao: ContactDao, plan: ContactImportPlan) {
    db.withTransaction {
        plan.contacts.forEach { dao.insert(it) }
    }
}
