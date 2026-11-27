package com.faheem.habits.features.money.data

import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Expense
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.features.money.OVERALL_BUDGET_CATEGORY
import com.faheem.habits.features.money.currentMonthKey
import com.faheem.habits.features.money.legacyBudgetsToRoom
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class ExpenseRepository @Inject constructor(
    private val database: AppDatabase,
    private val settings: MomentumSettingsRepository
) {
    private val dao get() = database.expenseDao()

    val expenses: Flow<List<Expense>> = dao.getAll()
    val budgets: Flow<List<CategoryBudget>> = dao.getBudgets()

    /** Idempotent one-time import of old DataStore values into the Room budget table. */
    suspend fun migrateLegacyBudgets() {
        val legacy = settings.legacyBudgetsForRoom() ?: return
        val rows = legacyBudgetsToRoom(legacy.overallMinorUnits, legacy.categoryMinorUnits, currentMonthKey())
        if (rows.isEmpty()) {
            settings.markLegacyBudgetsMigrated()
            return
        }
        database.withTransaction {
            rows.forEach { row ->
                if (dao.getBudget(row.category, row.monthKey) == null) dao.upsertBudget(row)
            }
        }
        settings.markLegacyBudgetsMigrated()
    }

    suspend fun setBudget(minorUnits: Long, monthKey: String = currentMonthKey()) =
        saveBudget(OVERALL_BUDGET_CATEGORY, monthKey, minorUnits)

    suspend fun setCategoryBudget(category: String, minorUnits: Long, monthKey: String = currentMonthKey()) =
        saveBudget(category.trim(), monthKey, minorUnits)

    private suspend fun saveBudget(category: String, monthKey: String, minorUnits: Long) {
        if (category.isBlank()) return
        database.withTransaction {
            val existing = dao.getBudget(category, monthKey)
            dao.upsertBudget(
                CategoryBudget(
                    category = category,
                    monthKey = monthKey,
                    limitMinorUnits = minorUnits,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                    dirty = true,
                    deviceId = existing?.deviceId.orEmpty()
                )
            )
        }
    }

    suspend fun add(title: String, amountMinorUnits: Long, category: String, epochDay: Long) =
        dao.insert(
            Expense(
                title = title.trim(),
                amountMinorUnits = amountMinorUnits,
                category = category,
                epochDay = epochDay
            )
        )

    suspend fun delete(expense: Expense) = dao.delete(expense.id)
}
