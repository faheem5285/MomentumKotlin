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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

private val Gold = Color(0xFFF59E0B)

@Composable
private fun StatCard(icon: ImageVector, color: Color, value: String, label: String, modifier: Modifier) {
    MetricTile(
        title = label,
        value = value,
        icon = icon,
        accent = color,
        modifier = modifier
    )
}

@Composable
private fun AchievementCard(a: Achievement, modifier: Modifier) {
    val color = if (a.unlocked) Gold else MaterialTheme.colorScheme.outline
    PremiumCard(modifier = modifier.heightIn(min = 176.dp), contentPadding = PaddingValues(14.dp)) {
        IconBubble(a.icon, color, 44.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            a.title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
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

@Composable
fun InsightsScreen(vm: HabitViewModel) {
    val all by vm.all.collectAsStateWithLifecycle()
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
        contentPadding = PaddingValues(
            horizontal = MomentumTokens.screenPadding,
            vertical = MomentumTokens.space2
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your level", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                        Text("Level ${ins.level}", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                        Text("${ins.xp} XP · ${ins.totalCheckIns} check-ins", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                    Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(56.dp), tint = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(14.dp))
                ProgressBar(ins.xpInLevel / 200f, Color.White, Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(6.dp))
                Text("${200 - ins.xpInLevel} XP to level ${ins.level + 1}", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Rounded.LocalFireDepartment, Warning, "${ins.currentStreak}", "Current streak", Modifier.weight(1f))
                    StatCard(Icons.Rounded.EmojiEvents, Gold, "${ins.bestStreak}", "Best streak", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Rounded.CheckCircle, primary, "${ins.rate7}%", "Done this week", Modifier.weight(1f))
                    StatCard(Icons.Rounded.Star, Positive, "${ins.perfectDays}", "Perfect days", Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("This week", subtitle = "Daily completion rate") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Insights, primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Consistency", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${ins.rate7}% done this week",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        "${ins.perfectDays} perfect",
                        if (ins.perfectDays > 0) Positive else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(14.dp))
                BarChart(
                    values = ins.last7.map { it.rate ?: 0f },
                    labels = ins.last7.map { DAY_LETTERS[LocalDate.ofEpochDay(it.day).dayOfWeek.value - 1] },
                    colors = listOf(primary),
                    highlightIndex = ins.last7.lastIndex,
                    height = 150.dp,
                    valueFormatter = { rate -> "${(rate * 100).toInt()}%" }
                )
            }
        }
        item { SectionHeader("Habit mix", subtitle = "When your habits happen") }
        item {
            val times = all.filter { !it.habit.archived }
                .groupingBy { TIME_LABELS.getOrElse(it.habit.timeOfDay) { "Anytime" } }
                .eachCount()
            val slices = times.entries
                .sortedByDescending { it.value }
                .mapIndexed { index, (label, count) ->
                    ChartSlice(label, count.toFloat(), chartColor(index))
                }
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                if (slices.isEmpty()) {
                    ChartPlaceholder("Add a habit to see your mix")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier.width(146.dp),
                            centerValue = "${slices.sumOf { it.value.toInt() }}",
                            centerLabel = "habits",
                            thickness = 18.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            ChartLegend(
                                slices = slices,
                                maxRows = 4,
                                valueFormatter = { "${it.toInt()}" }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Balance your day by spreading habits across the morning, afternoon and evening.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { SectionHeader("Last 12 weeks") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                HeatmapGrid(
                    columns = ins.heat.map { week -> week.map { it.rate } },
                    color = primary,
                    cellSize = 18.dp,
                    spacing = 4.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Darker = more habits done that day (Monday at the top)",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { SectionHeader("Streaks") }
        if (active.isEmpty()) {
            item { EmptyState(Icons.Rounded.LocalFireDepartment, "No streaks yet", "Complete a habit to start one") }
        }
        items(active, key = { it.habit.id }) { ui ->
            PremiumCard(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(habitIcon(ui.habit.icon), habitColor(ui.habit.colorIndex), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ui.habit.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
        item { SectionHeader("Achievements", subtitle = "${achs.count { it.unlocked }} of ${achs.size} unlocked") }
        items(achs.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> AchievementCard(a, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
