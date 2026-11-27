package com.faheem.habits.features.habits.data

import android.content.Context
import androidx.room.withTransaction
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.Completion
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.HabitDao
import com.faheem.habits.core.data.HabitReminder
import com.faheem.habits.core.data.HabitReminderDraft
import com.faheem.habits.core.data.HabitSkip
import com.faheem.habits.core.data.today
import com.faheem.habits.core.notifications.HabitReminderScheduler
import com.faheem.habits.core.notifications.MomentumNotificationSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@Suppress("TooManyFunctions")
class HabitRepository @Inject constructor(
    private val dao: HabitDao,
    @ApplicationContext private val context: Context
) {
    private val database get() = AppDatabase.get(context.applicationContext)

    val habits: Flow<List<Habit>> = dao.getHabits()
    val completions: Flow<List<Completion>> = dao.getCompletions()
    val reminders: Flow<List<HabitReminder>> = dao.getReminders()

    suspend fun add(habit: Habit) {
        if (habit.name.isBlank()) return
        database.withTransaction { dao.insertHabit(habit.copy(id = 0, createdEpochDay = today())) }
    }

    /**
     * Habit details, old-reminder tombstones and new reminders are written in
     * one Room transaction. Alarms are touched only after the transaction
     * succeeds, so a failed save cannot leave half a reminder set behind.
     */
    suspend fun saveWithReminders(habit: Habit, drafts: List<HabitReminderDraft>) {
        if (habit.name.isBlank()) return
        val now = System.currentTimeMillis()
        val changed = habit.copy(updatedAt = now, deleted = false, dirty = true)
        val oldReminders = if (habit.id == 0) emptyList() else dao.getRemindersForHabit(habit.id)
        val insertedIds = mutableListOf<Int>()

        val savedId = database.withTransaction {
            val id = if (habit.id == 0) {
                dao.insertHabit(changed.copy(createdEpochDay = today())).toInt()
            } else {
                dao.updateHabit(changed)
                oldReminders.forEach { dao.deleteReminder(it.id, now) }
                changed.id
            }
            drafts.forEach { draft ->
                insertedIds += dao.insertReminder(
                    HabitReminder(
                        habitId = id,
                        hour = draft.hour,
                        minute = draft.minute,
                        daysMask = draft.daysMask,
                        dateEpochDay = draft.dateEpochDay
                    )
                ).toInt()
            }
            id
        }

        if (drafts.isNotEmpty()) {
            if (!MomentumNotificationSettings.masterEnabled(context)) MomentumNotificationSettings.setMasterEnabled(context, true)
            if (!MomentumNotificationSettings.isEnabled(context, "habits")) MomentumNotificationSettings.setEnabled(context, "habits", true)
        }
        oldReminders.forEach { HabitReminderScheduler.cancel(context, it.id) }
        insertedIds.forEach { id -> dao.getReminder(id)?.let { HabitReminderScheduler.schedule(context, it) } }
    }

    suspend fun update(habit: Habit) = database.withTransaction {
        dao.updateHabit(habit.copy(updatedAt = System.currentTimeMillis(), deleted = false, dirty = true))
    }

    suspend fun toggle(habit: Habit, isCompleted: Boolean, day: Long) = database.withTransaction {
        if (isCompleted) {
            dao.deleteCompletion(habit.id, day)
        } else {
            val existing = dao.getCompletion(habit.id, day)
            val completion = existing?.copy(
                deleted = false,
                dirty = true,
                updatedAt = System.currentTimeMillis()
            ) ?: Completion(
                habitId = habit.id,
                epochDay = day,
                quantity = habit.targetQuantity,
                durationSeconds = habit.timerMinutes * 60L,
                completedAtEpochMillis = System.currentTimeMillis()
            )
            dao.upsertCompletion(completion)
        }
    }

    suspend fun skipDay(habit: Habit, day: Long) = database.withTransaction {
        val existing = dao.getSkip(habit.id, day)
        dao.upsertSkip(existing?.copy(
            deleted = false,
            dirty = true,
            updatedAt = System.currentTimeMillis()
        ) ?: HabitSkip(habit.id, day))
    }

    suspend fun unskipDay(habit: Habit, day: Long) = database.withTransaction {
        dao.deleteSkip(habit.id, day)
    }

    suspend fun setArchived(habit: Habit, archived: Boolean) {
        val reminders = dao.getRemindersForHabit(habit.id)
        database.withTransaction {
            dao.updateHabit(
                habit.copy(
                    archived = archived,
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                    dirty = true
                )
            )
        }
        reminders.forEach { reminder ->
            if (archived) HabitReminderScheduler.cancel(context, reminder.id)
            else HabitReminderScheduler.schedule(context, reminder)
        }
    }

    /** All child tombstones and the parent tombstone commit together. */
    suspend fun delete(habit: Habit) {
        val reminders = dao.getRemindersForHabit(habit.id)
        val updatedAt = System.currentTimeMillis()
        database.withTransaction {
            dao.deleteCompletionsForHabit(habit.id, updatedAt)
            dao.deleteSkipsForHabit(habit.id, updatedAt)
            dao.deleteRemindersForHabit(habit.id, updatedAt)
            dao.deleteHabit(habit.id, updatedAt)
        }
        reminders.forEach { HabitReminderScheduler.cancel(context, it.id) }
    }

    suspend fun addReminder(habit: Habit, hour: Int, minute: Int, daysMask: Int) {
        val id = database.withTransaction {
            dao.insertReminder(HabitReminder(habitId = habit.id, hour = hour, minute = minute, daysMask = daysMask)).toInt()
        }
        dao.getReminder(id)?.let {
            if (!habit.archived) HabitReminderScheduler.schedule(context, it)
        }
    }

    suspend fun updateReminder(reminder: HabitReminder) {
        val changed = reminder.copy(
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            dirty = true
        )
        database.withTransaction { dao.updateReminder(changed) }
        if (dao.getHabit(changed.habitId)?.archived == true) {
            HabitReminderScheduler.cancel(context, changed.id)
        } else {
            HabitReminderScheduler.schedule(context, changed)
        }
    }

    suspend fun deleteReminder(reminder: HabitReminder) {
        database.withTransaction { dao.deleteReminder(reminder.id) }
        HabitReminderScheduler.cancel(context, reminder.id)
    }
}
