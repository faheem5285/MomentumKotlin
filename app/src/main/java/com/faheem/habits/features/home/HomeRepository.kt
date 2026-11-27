package com.faheem.habits.features.home

import android.content.Context
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.saturatingSubtract
import com.faheem.habits.core.data.sumMinorUnits
import com.faheem.habits.core.data.today
import com.faheem.habits.features.habits.isScheduled
import com.faheem.habits.features.money.outstandingByLoan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class HomeRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context
) {
    private val vaultReadableCount = callbackFlow {
        val accountId = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
            .getString("user_id", null)
            ?.takeIf(String::isNotBlank)
            ?: "unlinked"
        val prefs = context.getSharedPreferences("momentum_vault_$accountId", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "readable_records") trySend(prefs.getInt("readable_records", -1))
        }
        trySend(prefs.getInt("readable_records", -1))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private val vaultStats = combine(database.vaultDao().observeUpdatedAt(), vaultReadableCount) { updates, readable ->
        updates to readable
    }

    private val loanData = combine(
        database.loanDao().getAll(),
        database.loanDao().getAllPayments()
    ) { loans, payments -> loans to payments }

    private val overview = combine(
        database.habitDao().getHabits(),
        database.habitDao().getCompletions(),
        database.expenseDao().getAll(),
        loanData,
        database.fileDao().getAll()
    ) { habits, completions, expenses, currentLoanData, files ->
        val (loans, payments) = currentLoanData
        withContext(Dispatchers.Default) {
            val day = today()
            val active = habits.filter { !it.archived && isScheduled(it.daysMask, day) }
            val doneIds = completions.asSequence()
                .filter { it.epochDay == day }
                .map { it.habitId }
                .toSet()
            val month = YearMonth.now()
            val spent = expenses.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
                .sumMinorUnits { it.amountMinorUnits }
            val spentByDay = expenses.filter { it.epochDay in (day - 6)..day }
                .groupBy { it.epochDay }
                .mapValues { entry -> entry.value.sumMinorUnits { it.amountMinorUnits }.toFloat() }
            val open = loans.filter { !it.settled }
            val outstanding = outstandingByLoan(open, payments)
            fun outstandingFor(loanId: Int): Long = outstanding[loanId] ?: 0L
            val loanLent = open.filter { it.lent }.sumMinorUnits { outstandingFor(it.id) }
            val loanBorrowed = open.filter { !it.lent }.sumMinorUnits { outstandingFor(it.id) }
            val habitWeek = (6L downTo 0L).map { offset ->
                val date = LocalDate.ofEpochDay(day).minusDays(offset).toEpochDay()
                val scheduledToday = habits
                    .filter { !it.archived && isScheduled(it.daysMask, date) }
                    .map { it.id }
                    .toSet()
                completions.asSequence()
                    .filter { it.epochDay == date && it.habitId in scheduledToday }
                    .count()
                    .toFloat()
            }
            HomeState(
                habitsTotal = active.size,
                habitsDone = active.count { it.id in doneIds },
                monthSpent = spent,
                loanNet = saturatingSubtract(loanLent, loanBorrowed),
                openLoans = open.size,
                fileCount = files.size,
                habitWeek = habitWeek,
                spendWeek = (6L downTo 0L).map { spentByDay[day - it] ?: 0f },
                fileMix = files.groupBy { fileKind(it.mime) }
                    .map { (kind, items) -> kind to items.size.toFloat() }
                    .sortedByDescending { it.second },
                loanLent = loanLent,
                loanBorrowed = loanBorrowed
            )
        }
    }

    val state: Flow<HomeState> = combine(
        overview,
        database.toDoDao().getAll(),
        vaultStats,
        database.syncStateDao().observeAll(),
        database.lifeRuleDao().getAll()
    ) { base, tasks, vaultStats, syncStates, lifeRules ->
        val (vaultUpdates, readableVaultCount) = vaultStats
        val liveRules = lifeRules.filter { !it.deleted }
        val day = today()
        fun weekCounts(timestamps: List<Long>): List<Float> =
            (6L downTo 0L).map { offset ->
                val epoch = day - offset
                val begin = LocalDate.ofEpochDay(epoch)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = LocalDate.ofEpochDay(epoch + 1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                timestamps.count { it in begin until end }.toFloat()
            }
        base.copy(
            tasksDueToday = tasks.count { !it.completed && it.dueEpochDay == day },
            overdueTasks = tasks.count { !it.completed && it.dueEpochDay < day },
            openTasks = tasks.count { !it.completed },
            todoWeek = weekCounts(tasks.filter { it.completed }.map { it.updatedAt }),
            vaultWeek = weekCounts(vaultUpdates),
            vaultEntries = readableVaultCount.coerceAtLeast(0),
            vaultCountKnown = readableVaultCount >= 0,
            syncPending = syncStates.sumOf { it.pendingCount },
            syncHealthy = syncStates.count { it.lastSyncEpochMillis > 0 && it.lastError.isNullOrBlank() },
            syncTracked = syncStates.size,
            lifeRules = liveRules.size,
            pinnedRules = liveRules.count { it.pinned }
        )
    }
}

private fun fileKind(mime: String): String = when {
    mime.startsWith("image/") -> "Images"
    mime.startsWith("video/") -> "Video"
    mime.startsWith("audio/") -> "Audio"
    mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
        mime.contains("sheet") || mime.contains("presentation") -> "Docs"
    else -> "Other"
}
