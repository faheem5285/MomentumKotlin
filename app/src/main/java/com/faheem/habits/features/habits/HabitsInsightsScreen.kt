package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val Gold = Color(0xFFF59E0B)

@Composable
private fun StatCard(icon: ImageVector, color: Color, value: String, label: String, modifier: Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            IconBubble(icon, color, 36.dp)
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, modifier: Modifier) {
    val color = if (a.unlocked) Gold else MaterialTheme.colorScheme.outline
    Card(
        modifier.height(176.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp).fillMaxSize()) {
            IconBubble(a.icon, color, 44.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                a.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (a.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(a.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.weight(1f))
            if (a.unlocked) {
                Text("Unlocked ✓", color = Positive, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            } else {
                ProgressBar(
                    a.current.toFloat() / a.target,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.surfaceVariant,
                    height = 6.dp
                )
                Spacer(Modifier.height(4.dp))
                Text("${a.current} / ${a.target}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InsightsScreen(vm: HabitViewModel) {
    val all by vm.all.collectAsState()
    val ins = remember(all) { computeInsights(all, today()) }
    val achs = remember(ins) { achievements(ins) }
    val active = all.filter { !it.habit.archived }.sortedByDescending { it.current }
    // Measure consistency against only the days on which each habit was actually scheduled.
    // New habits are not penalized for dates before they were created.
    fun consistency30(ui: HabitUi): Int {
        val start = maxOf(today() - 29, ui.habit.createdEpochDay)
        val scheduled = (start..today()).filter { isScheduled(ui.habit.daysMask, it) }
        if (scheduled.isEmpty()) return 0
        return (scheduled.count { it in ui.days } * 100) / scheduled.size
    }
    val primary = MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your level", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("Level ${ins.level}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("${ins.xp} XP · ${ins.totalCheckIns} check-ins", fontSize = 14.sp)
                    }
                    Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(56.dp), tint = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(ins.xpInLevel / 200f, Color.White, Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(6.dp))
                Text("${200 - ins.xpInLevel} XP to level ${ins.level + 1}", fontSize = 13.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Rounded.LocalFireDepartment, Warning, "${ins.currentStreak}", "Current streak (days)", Modifier.weight(1f))
                    StatCard(Icons.Rounded.EmojiEvents, Gold, "${ins.bestStreak}", "Best streak (days)", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Rounded.CheckCircle, primary, "${ins.rate7}%", "Done this week", Modifier.weight(1f))
                    StatCard(Icons.Rounded.Star, Positive, "${ins.perfectDays}", "Perfect days", Modifier.weight(1f))
                }
            }
        }
        item { SectionLabel("This week") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(150.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    ins.last7.forEach { dr ->
                        val dow = LocalDate.ofEpochDay(dr.day).dayOfWeek.value
                        val rate = dr.rate
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                if (rate == null) "–" else "${(rate * 100).toInt()}%",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .width(18.dp)
                                    .height(((rate ?: 0f) * 70f).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (rate == null) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else primary)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(DAY_LETTERS[dow - 1], fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { SectionLabel("Last 12 weeks") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ins.heat.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                week.forEach { dr ->
                                    val r = dr.rate
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                when {
                                                    r == null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                                    r == 0f -> MaterialTheme.colorScheme.surfaceVariant
                                                    else -> primary.copy(alpha = 0.3f + 0.7f * r)
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Darker = more habits done that day (Monday at the top)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { SectionLabel("Streaks") }
        if (active.isEmpty()) {
            item { EmptyState(Icons.Rounded.LocalFireDepartment, "No streaks yet", "Complete a habit to start one") }
        }
        items(active, key = { it.habit.id }) { ui ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(habitIcon(ui.habit.icon), habitColor(ui.habit.colorIndex), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ui.habit.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Best ${ui.best} · ${ui.days.size} check-ins",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("30-day consistency", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${consistency30(ui)}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = habitColor(ui.habit.colorIndex))
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(
                            consistency30(ui) / 100f,
                            habitColor(ui.habit.colorIndex),
                            MaterialTheme.colorScheme.surfaceVariant,
                            height = 7.dp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    StreakChip(ui.current)
                }
            }
        }
        item { SectionLabel("Achievements · ${achs.count { it.unlocked }}/${achs.size}") }
        items(achs.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> AchievementCard(a, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
