package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val TIME_LABELS = listOf("Anytime", "Morning", "Afternoon", "Evening")
const val WEEKDAYS = 31
const val WEEKENDS = 96

/** daysMask: bit0 = Monday ... bit6 = Sunday */
fun isScheduled(mask: Int, epochDay: Long): Boolean {
    val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
    return ((mask shr (dow - 1)) and 1) == 1
}

fun scheduleText(mask: Int): String = when (mask) {
    ALL_DAYS -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DAY_NAMES.filterIndexed { i, _ -> ((mask shr i) and 1) == 1 }.joinToString(", ")
}

val HabitIcons: Map<String, ImageVector> = linkedMapOf(
    "star" to Icons.Rounded.Star,
    "fitness" to Icons.Rounded.FitnessCenter,
    "run" to Icons.Rounded.DirectionsRun,
    "walk" to Icons.Rounded.DirectionsWalk,
    "water" to Icons.Rounded.WaterDrop,
    "bed" to Icons.Rounded.Bedtime,
    "book" to Icons.Rounded.MenuBook,
    "mind" to Icons.Rounded.SelfImprovement,
    "food" to Icons.Rounded.Restaurant,
    "savings" to Icons.Rounded.Savings,
    "wallet" to Icons.Rounded.AccountBalanceWallet,
    "code" to Icons.Rounded.Code,
    "music" to Icons.Rounded.MusicNote,
    "brush" to Icons.Rounded.Brush,
    "heart" to Icons.Rounded.Favorite,
    "home" to Icons.Rounded.Home,
    "work" to Icons.Rounded.Work,
    "school" to Icons.Rounded.School,
    "edit" to Icons.Rounded.Edit,
    "language" to Icons.Rounded.Language,
    "park" to Icons.Rounded.Park,
    "pets" to Icons.Rounded.Pets,
    "clean" to Icons.Rounded.CleaningServices,
    "spa" to Icons.Rounded.Spa,
    "bolt" to Icons.Rounded.Bolt,
    "sun" to Icons.Rounded.WbSunny,
    "groups" to Icons.Rounded.Groups,
    "call" to Icons.Rounded.Call,
    "timer" to Icons.Rounded.Timer,
    "trophy" to Icons.Rounded.EmojiEvents,
    "idea" to Icons.Rounded.Lightbulb,
    "phone" to Icons.Rounded.PhoneAndroid,
    "prayer" to Icons.Rounded.AccessTime,
    "quran" to Icons.Rounded.MenuBook,
    "dhikr" to Icons.Rounded.SelfImprovement,
    "dua" to Icons.Rounded.Favorite,
    "charity" to Icons.Rounded.FavoriteBorder,
    "fast" to Icons.Rounded.Restaurant,
    "mosque" to Icons.Rounded.Home
)

fun habitIcon(key: String): ImageVector = HabitIcons[key] ?: Icons.Rounded.Star

val HabitColors = listOf(
    Color(0xFF6366F1), Color(0xFF10B981), Color(0xFFF97316), Color(0xFFEC4899),
    Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFF14B8A6), Color(0xFFA855F7),
    Color(0xFFEAB308), Color(0xFF64748B)
)

fun habitColor(index: Int): Color = HabitColors[Math.floorMod(index, HabitColors.size)]

data class StreakInfo(val current: Int, val best: Int)

/**
 * Streaks count consecutive scheduled days that were completed.
 * Days the habit is not scheduled never break a streak, and today
 * does not break the streak until the day is over.
 */
fun computeStreaks(days: Set<Long>, mask: Int, today: Long): StreakInfo {
    if (days.isEmpty() || mask == 0) return StreakInfo(0, 0)
    val first = days.minOrNull() ?: return StreakInfo(0, 0)
    var best = 0
    var run = 0
    for (day in first..today) {
        if (!isScheduled(mask, day)) continue
        if (day in days) {
            run++
            best = maxOf(best, run)
        } else if (day != today) {
            run = 0
        }
    }

    // Today remains available until midnight, so an unchecked habit does not
    // break yesterday's streak early.
    var current = 0
    var cursor = today
    if (isScheduled(mask, cursor) && cursor !in days) cursor--
    while (cursor >= first) {
        if (isScheduled(mask, cursor)) {
            if (cursor !in days) break
            current++
        }
        cursor--
    }
    return StreakInfo(current, best)
}

data class DayRate(val day: Long, val rate: Float?)   // null = nothing scheduled

data class Insights(
    val last7: List<DayRate>,
    val heat: List<List<DayRate>>,   // 12 weeks x 7 days
    val rate7: Int,
    val totalCheckIns: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val perfectDays: Int,
    val habitCount: Int,
    val xp: Int
) {
    val level: Int get() = xp / 200 + 1
    val xpInLevel: Int get() = xp % 200
}

fun computeInsights(all: List<HabitUi>, today: Long): Insights {
    val active = all.filter { !it.habit.archived }

    fun scheduledOn(d: Long): List<HabitUi> =
        active.filter { isScheduled(it.habit.daysMask, d) && it.habit.createdEpochDay <= d }

    fun rateOn(d: Long): DayRate {
        val sched = scheduledOn(d)
        if (sched.isEmpty()) return DayRate(d, null)
        return DayRate(d, sched.count { d in it.days }.toFloat() / sched.size)
    }

    val last7 = (today - 6..today).map { rateOn(it) }

    var schedSum = 0
    var doneSum = 0
    for (d in today - 6..today) {
        val sched = scheduledOn(d)
        schedSum += sched.size
        doneSum += sched.count { d in it.days }
    }
    val rate7 = if (schedSum == 0) 0 else (doneSum * 100) / schedSum

    val todayDow = java.time.LocalDate.ofEpochDay(today).dayOfWeek.value
    val weekStart = today - (todayDow - 1) - 11 * 7
    val heat = (0 until 12).map { w ->
        (0 until 7).map { i ->
            val d = weekStart + w * 7 + i
            if (d > today) DayRate(d, null) else rateOn(d)
        }
    }

    var perfect = 0
    for (d in today - 365..today) {
        val sched = scheduledOn(d)
        if (sched.size >= 2 && sched.all { d in it.days }) perfect++
    }

    val total = all.sumOf { it.days.size }
    return Insights(
        last7 = last7,
        heat = heat,
        rate7 = rate7,
        totalCheckIns = total,
        currentStreak = active.maxOfOrNull { it.current } ?: 0,
        bestStreak = all.maxOfOrNull { it.best } ?: 0,
        perfectDays = perfect,
        habitCount = active.size,
        xp = total * 10
    )
}

data class Achievement(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val current: Int,
    val target: Int
) {
    val unlocked: Boolean get() = current >= target
}

fun achievements(i: Insights): List<Achievement> = listOf(
    Achievement("First Step", "Complete a habit for the first time", Icons.Rounded.Check, i.totalCheckIns, 1),
    Achievement("Getting Started", "Create 3 habits", Icons.Rounded.Star, i.habitCount, 3),
    Achievement("Habit Builder", "Create 8 habits", Icons.Rounded.Lightbulb, i.habitCount, 8),
    Achievement("On Fire", "Reach a 3-day streak", Icons.Rounded.LocalFireDepartment, i.bestStreak, 3),
    Achievement("Week Warrior", "Reach a 7-day streak", Icons.Rounded.Bolt, i.bestStreak, 7),
    Achievement("Fortnight Focus", "Reach a 14-day streak", Icons.Rounded.WbSunny, i.bestStreak, 14),
    Achievement("Monthly Master", "Reach a 30-day streak", Icons.Rounded.EmojiEvents, i.bestStreak, 30),
    Achievement("Unstoppable", "Reach a 100-day streak", Icons.Rounded.EmojiEvents, i.bestStreak, 100),
    Achievement("Perfect Day", "Finish every habit of a day (2+ habits)", Icons.Rounded.CheckCircle, i.perfectDays, 1),
    Achievement("Perfect Five", "Have 5 perfect days", Icons.Rounded.Favorite, i.perfectDays, 5),
    Achievement("Dedicated", "Make 50 check-ins", Icons.Rounded.Timer, i.totalCheckIns, 50),
    Achievement("Century", "Make 100 check-ins", Icons.Rounded.Star, i.totalCheckIns, 100),
    Achievement("Legend", "Make 500 check-ins", Icons.Rounded.EmojiEvents, i.totalCheckIns, 500)
)
