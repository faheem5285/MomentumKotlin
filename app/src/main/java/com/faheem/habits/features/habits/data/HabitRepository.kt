package com.faheem.habits.features.habits.data

import android.content.Context
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
    val habits: Flow<List<Habit>> = dao.getHabits()
    val completions: Flow<List<Completion>> = dao.getCompletions()
    val reminders: Flow<List<HabitReminder>> = dao.getReminders()
    val skips: Flow<List<HabitSkip>> = dao.getSkips()

    suspend fun add(habit: Habit) {
        if (habit.name.isBlank()) return
        dao.insertHabit(habit.copy(id = 0, createdEpochDay = today()))
    }

    suspend fun saveWithReminders(habit: Habit, drafts: List<HabitReminderDraft>) {
        if (habit.name.isBlank()) return
        val changed = habit.copy(
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            dirty = true
        )
        val id = if (habit.id == 0) {
            dao.insertHabit(changed.copy(createdEpochDay = today())).toInt()
        } else {
            dao.updateHabit(changed)
            dao.getRemindersForHabit(changed.id).forEach { old ->
                HabitReminderScheduler.cancel(context, old.id)
                dao.deleteReminder(old.id)
            }
            changed.id
        }
        if (drafts.isNotEmpty()) {
            if (!MomentumNotificationSettings.masterEnabled(context)) MomentumNotificationSettings.setMasterEnabled(context, true)
            if (!MomentumNotificationSettings.isEnabled(context, "habits")) MomentumNotificationSettings.setEnabled(context, "habits", true)
        }
        drafts.forEach { draft ->
            val reminderId = dao.insertReminder(
                HabitReminder(
                    habitId = id,
                    hour = draft.hour,
                    minute = draft.minute,
                    daysMask = draft.daysMask,
                    dateEpochDay = draft.dateEpochDay
                )
            )
            dao.getReminder(reminderId.toInt())?.let { HabitReminderScheduler.schedule(context, it) }
        }
    }

    suspend fun update(habit: Habit) {
        dao.updateHabit(habit.copy(updatedAt = System.currentTimeMillis(), deleted = false, dirty = true))
    }

    suspend fun toggle(habit: Habit, isCompleted: Boolean, day: Long) {
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

    suspend fun skipDay(habit: Habit, day: Long) {
        val existing = dao.getSkip(habit.id, day)
        dao.upsertSkip(existing?.copy(
            deleted = false,
            dirty = true,
            updatedAt = System.currentTimeMillis()
        ) ?: HabitSkip(habit.id, day))
    }

    suspend fun unskipDay(habit: Habit, day: Long) {
        dao.deleteSkip(habit.id, day)
    }

    suspend fun useStreakFreeze(habit: Habit, day: Long) {
        if (day >= today()) return
        val current = dao.getHabit(habit.id) ?: return
        if (current.streakFreezes <= 0) return
        val existing = dao.getSkip(habit.id, day)
        if (existing != null && !existing.deleted) return
        val now = System.currentTimeMillis()
        dao.upsertSkip(
            existing?.copy(reason = "freeze", deleted = false, dirty = true, updatedAt = now)
                ?: HabitSkip(habitId = habit.id, epochDay = day, reason = "freeze", updatedAt = now)
        )
        dao.updateHabit(
            current.copy(
                streakFreezes = current.streakFreezes - 1,
                updatedAt = now,
                dirty = true
            )
        )
    }

    suspend fun setArchived(habit: Habit, archived: Boolean) {
        dao.updateHabit(
            habit.copy(
                archived = archived,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                dirty = true
            )
        )
        dao.getRemindersForHabit(habit.id).forEach { reminder ->
            if (archived) HabitReminderScheduler.cancel(context, reminder.id)
            else HabitReminderScheduler.schedule(context, reminder)
        }
    }

    suspend fun delete(habit: Habit) {
        val updatedAt = System.currentTimeMillis()
        dao.getRemindersForHabit(habit.id).forEach { HabitReminderScheduler.cancel(context, it.id) }
        dao.deleteCompletionsForHabit(habit.id, updatedAt)
        dao.deleteSkipsForHabit(habit.id, updatedAt)
        dao.deleteRemindersForHabit(habit.id, updatedAt)
        dao.deleteHabit(habit.id, updatedAt)
    }

    suspend fun addReminder(habit: Habit, hour: Int, minute: Int, daysMask: Int) {
        val id = dao.insertReminder(HabitReminder(habitId = habit.id, hour = hour, minute = minute, daysMask = daysMask))
        dao.getReminder(id.toInt())?.let {
            if (!habit.archived) HabitReminderScheduler.schedule(context, it)
        }
    }

    suspend fun updateReminder(reminder: HabitReminder) {
        val changed = reminder.copy(
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            dirty = true
        )
        dao.updateReminder(changed)
        if (dao.getHabit(changed.habitId)?.archived == true) {
            HabitReminderScheduler.cancel(context, changed.id)
        } else {
            HabitReminderScheduler.schedule(context, changed)
        }
    }

    suspend fun deleteReminder(reminder: HabitReminder) {
        HabitReminderScheduler.cancel(context, reminder.id)
        dao.deleteReminder(reminder.id)
    }
}
