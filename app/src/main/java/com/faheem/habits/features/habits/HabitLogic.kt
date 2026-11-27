package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.features.phase7.ratePercent
import com.faheem.habits.R


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val TIME_LABELS = listOf("Anytime", "Morning", "Afternoon", "Evening")
const val WEEKDAYS = 31
const val WEEKENDS = 96

fun scheduleText(mask: Int): String = when (mask) {
    ALL_DAYS -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DAY_NAMES.filterIndexed { i, _ -> ((mask shr i) and 1) == 1 }.joinToString(", ")
}

private val PrayerHabitIcon = ImageVector.Builder(
    name = "PrayerHands",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.35f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(12f, 2.2f)
        curveTo(9.95f, 2.2f, 8.3f, 3.85f, 8.3f, 5.9f)
        curveTo(8.3f, 7.95f, 9.95f, 9.6f, 12f, 9.6f)
        curveTo(14.05f, 9.6f, 15.7f, 7.95f, 15.7f, 5.9f)
        curveTo(15.7f, 3.85f, 14.05f, 2.2f, 12f, 2.2f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.4f, 9.1f)
        curveTo(7.25f, 8.9f, 6.1f, 10.25f, 6.25f, 12.5f)
        lineTo(6.7f, 17.45f)
        curveTo(6.85f, 18.8f, 6.3f, 19.6f, 5.05f, 20f)
        lineTo(2.65f, 20.75f)
        curveTo(1.5f, 21.1f, 1.7f, 22.05f, 2.9f, 22.05f)
        lineTo(10.2f, 22.05f)
        curveTo(11.6f, 22.05f, 12.25f, 21.1f, 11.65f, 20f)
        lineTo(10.55f, 17.8f)
        curveTo(12.8f, 17.6f, 14.25f, 16.45f, 15.45f, 14.9f)
        lineTo(17.7f, 13.35f)
        moveTo(9.4f, 9.1f)
        curveTo(10.65f, 9.15f, 11.3f, 10.35f, 11.8f, 11.7f)
        lineTo(12.45f, 13.1f)
        lineTo(16.55f, 11.55f)
        lineTo(18.9f, 9.45f)
        lineTo(19.85f, 7.45f)
        curveTo(20.2f, 6.65f, 20.95f, 6.8f, 20.85f, 7.6f)
        lineTo(20.5f, 9.3f)
        lineTo(21.7f, 8.05f)
        curveTo(22.3f, 7.45f, 22.9f, 7.95f, 22.5f, 8.65f)
        lineTo(20.95f, 11.4f)
        curveTo(20.55f, 12.1f, 20.05f, 12.6f, 19.35f, 13.05f)
        lineTo(16.2f, 15.15f)
    }
}.build()

val HabitIcons: Map<String, ImageVector> = linkedMapOf(
    // Dedicated symbols keep the Muslim habit library recognizable in cards,
    // templates, and the edit form instead of reusing generic icons.
    "prayer" to PrayerHabitIcon,
    "quran" to Icons.AutoMirrored.Rounded.MenuBook,
    "dhikr" to Icons.Rounded.Toll,
    "dua" to Icons.Rounded.PanTool,
    "charity" to Icons.Rounded.Handshake,
    "fast" to Icons.Rounded.NoFood,
    "mosque" to Icons.Rounded.Mosque,
    "wudu" to Icons.Rounded.WaterDrop,
    "star" to Icons.Rounded.Celebration,
    "health" to Icons.Rounded.HealthAndSafety,
    "fitness" to Icons.Rounded.FitnessCenter,
    "run" to Icons.AutoMirrored.Rounded.DirectionsRun,
    "cycle" to Icons.AutoMirrored.Rounded.DirectionsBike,
    "swim" to Icons.Rounded.Pool,
    "walk" to Icons.AutoMirrored.Rounded.DirectionsWalk,
    "water" to Icons.Rounded.WaterDrop,
    "bed" to Icons.Rounded.Bedtime,
    "book" to Icons.Rounded.AutoStories,
    "mind" to Icons.Rounded.Psychology,
    "food" to Icons.Rounded.DinnerDining,
    "savings" to Icons.AutoMirrored.Rounded.TrendingUp,
    "wallet" to Icons.Rounded.AccountBalanceWallet,
    "code" to Icons.Rounded.LaptopChromebook,
    "music" to Icons.Rounded.Podcasts,
    "brush" to Icons.Rounded.Palette,
    "heart" to Icons.Rounded.Favorite,
    "home" to Icons.Rounded.Home,
    "work" to Icons.Rounded.Work,
    "school" to Icons.Rounded.School,
    "edit" to Icons.Rounded.EditNote,
    "language" to Icons.Rounded.Translate,
    "park" to Icons.Rounded.Forest,
    "pets" to Icons.Rounded.Pets,
    "clean" to Icons.Rounded.LocalLaundryService,
    "spa" to Icons.Rounded.SelfImprovement,
    "bolt" to Icons.Rounded.TrackChanges,
    "sun" to Icons.Rounded.WbTwilight,
    "groups" to Icons.Rounded.FamilyRestroom,
    "call" to Icons.AutoMirrored.Rounded.Chat,
    "timer" to Icons.Rounded.Timer,
    "trophy" to Icons.Rounded.Celebration,
    "idea" to Icons.Rounded.Lightbulb,
    "phone" to Icons.Rounded.Devices,
    "directions" to Icons.Rounded.Hiking,
    "checklist" to Icons.Rounded.TaskAlt,
    "calendar" to Icons.Rounded.CalendarMonth,
    "psychology" to Icons.Rounded.Psychology,
    "breathing" to Icons.Rounded.Air,
    "sunrise" to Icons.Rounded.WbTwilight,
    "medicine" to Icons.Rounded.Medication,
    "eyes" to Icons.Rounded.Visibility,
    "balance" to Icons.Rounded.Balance,
    "stairs" to Icons.Rounded.Stairs,
    "target" to Icons.Rounded.TrackChanges,
    "task" to Icons.Rounded.TaskAlt,
    "email" to Icons.Rounded.Email,
    "laptop" to Icons.Rounded.LaptopChromebook,
    "review" to Icons.AutoMirrored.Rounded.FactCheck,
    "science" to Icons.Rounded.Science,
    "calculator" to Icons.Rounded.Calculate,
    "translate" to Icons.Rounded.Translate,
    "podcast" to Icons.Rounded.Podcasts,
    "history" to Icons.Rounded.HistoryEdu,
    "family" to Icons.Rounded.FamilyRestroom,
    "chat" to Icons.AutoMirrored.Rounded.Chat,
    "handshake" to Icons.Rounded.Handshake,
    "receipt" to Icons.AutoMirrored.Rounded.ReceiptLong,
    "trend" to Icons.AutoMirrored.Rounded.TrendingUp,
    "laundry" to Icons.Rounded.LocalLaundryService,
    "plant" to Icons.Rounded.LocalFlorist,
    "recycle" to Icons.Rounded.Recycling,
    "kitchen" to Icons.Rounded.Kitchen,
    "devices" to Icons.Rounded.Devices,
    "backup" to Icons.Rounded.Backup,
    "security" to Icons.Rounded.Security,
    "notifications" to Icons.Rounded.Notifications,
    "wifiOff" to Icons.Rounded.WifiOff,
    "password" to Icons.Rounded.Password,
    "palette" to Icons.Rounded.Palette,
    "camera" to Icons.Rounded.PhotoCamera,
    "movie" to Icons.Rounded.Movie,
    "design" to Icons.Rounded.DesignServices,
    "forest" to Icons.Rounded.Forest,
    "meal" to Icons.Rounded.DinnerDining,
    "sports" to Icons.Rounded.SportsSoccer,
    "basketball" to Icons.Rounded.SportsBasketball,
    "hiking" to Icons.Rounded.Hiking,
    "celebrate" to Icons.Rounded.Celebration,
    "inventory" to Icons.Rounded.Inventory2,
    "shopping" to Icons.Rounded.ShoppingCart,
    "budget" to Icons.Rounded.AccountBalanceWallet,
    "journal" to Icons.Rounded.EditNote,
    "nature" to Icons.Rounded.Forest,
)

val IslamicHabitIconKeys = listOf("prayer", "quran", "dhikr", "dua", "charity", "fast", "mosque", "wudu")

fun habitIcon(key: String): ImageVector = HabitIcons[key] ?: Icons.Rounded.Star

val HabitColors = listOf(
    Color(0xFF6366F1), Color(0xFF10B981), Color(0xFFF97316), Color(0xFFEC4899),
    Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFF14B8A6), Color(0xFFA855F7),
    Color(0xFFEAB308), Color(0xFF64748B)
)

fun habitColor(index: Int): Color = HabitColors[Math.floorMod(index, HabitColors.size)]

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
    val rate7 = ratePercent(doneSum, schedSum)

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
