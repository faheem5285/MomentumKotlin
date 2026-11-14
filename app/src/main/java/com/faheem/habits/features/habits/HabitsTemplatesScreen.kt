package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun categoryColorIndex(cat: String): Int = TemplateCategories.indexOf(cat).coerceAtLeast(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(vm: HabitViewModel) {
    val all by vm.all.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }

    val existing = all.filter { !it.habit.archived }.map { it.habit.name.lowercase() }.toSet()
    val shown = HabitTemplates.filter {
        (cat == "All" || it.category == cat) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.what.contains(query, ignoreCase = true))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard(contentPadding = 18.dp) {
                Text("Habit library", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                Text("${HabitTemplates.size} ready-made habits", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every habit tells you exactly what to do. Tap Add, then change anything in Manage.",
                    fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        item {
            MomentumTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search habits") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + TemplateCategories).forEach { c ->
                    MomentumChip(selected = cat == c, onClick = { cat = c }, label = c)
                }
            }
        }
        item { SectionHeader(if (cat == "All") "All habits" else cat, subtitle = "${shown.size} to explore") }
        if (shown.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, "No match", "Try another word or category") }
        }
        items(shown, key = { it.name }) { tpl ->
            val color = habitColor(categoryColorIndex(tpl.category))
            val added = tpl.name.lowercase() in existing
            PremiumCard(
                modifier = Modifier.animateItem(),
                accent = color,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(habitIcon(tpl.icon), color, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            tpl.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        if (added) {
                            MomentumChip(
                                selected = true,
                                onClick = {},
                                label = "Added",
                                icon = Icons.Rounded.Check,
                                enabled = false
                            )
                        } else {
                            MomentumOutlinedButton(
                                text = "Add",
                                icon = Icons.Rounded.Add,
                                onClick = {
                                    vm.add(
                                        Habit(
                                            name = tpl.name,
                                            description = tpl.what,
                                            icon = tpl.icon,
                                            colorIndex = categoryColorIndex(tpl.category),
                                            timeOfDay = tpl.timeOfDay,
                                            daysMask = tpl.daysMask
                                        )
                                    )
                                    Toast.makeText(ctx, "Added to Today", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tpl.what,
                        modifier = Modifier.fillMaxWidth().padding(start = 56.dp),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${tpl.category} · ${scheduleText(tpl.daysMask)} · ${TIME_LABELS[tpl.timeOfDay]}",
                        modifier = Modifier.padding(start = 56.dp),
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
