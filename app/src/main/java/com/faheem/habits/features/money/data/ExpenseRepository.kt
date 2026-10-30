package com.faheem.habits.features.money.data

import com.faheem.habits.core.data.Expense
import com.faheem.habits.core.data.ExpenseDao
import com.faheem.habits.core.data.toMinorUnits
import com.faheem.habits.core.data.today
import com.faheem.habits.core.settings.MomentumSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao,
    private val settings: MomentumSettingsRepository
) {
    val expenses: Flow<List<Expense>> = dao.getAll()
    val budgetMinorUnits: Flow<Long> = settings.budgetMinorUnits
    fun categoryBudgetMinorUnits(category: String): Flow<Long> = settings.categoryBudgetMinorUnits(category)
    suspend fun setBudget(value: Double) = settings.setBudgetMinorUnits(value.toMinorUnits())
    suspend fun setCategoryBudget(category: String, value: Double) =
        settings.setCategoryBudgetMinorUnits(category, value.toMinorUnits())
    suspend fun add(title: String, amount: Double, category: String) =
        dao.insert(
            Expense(
                title = title.trim(),
                amountMinorUnits = amount.toMinorUnits(),
                category = category,
                epochDay = today()
            )
        )
    suspend fun delete(expense: Expense) = dao.delete(expense)
}
