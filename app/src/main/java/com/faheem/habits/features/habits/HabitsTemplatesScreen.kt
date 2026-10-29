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
import androidx.compose.foundation.shape.RoundedCornerShape
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

private fun categoryColorIndex(cat: String): Int = TemplateCategories.indexOf(cat).coerceAtLeast(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(vm: HabitViewModel) {
    val all by vm.all.collectAsState()
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
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Text("Habit library", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                Text("${HabitTemplates.size} ready-made habits", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every habit tells you exactly what to do. Tap Add, then change anything in Manage.",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search habits") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + TemplateCategories).forEach { c ->
                    FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                }
            }
        }
        item { SectionLabel("${shown.size} habits") }
        if (shown.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, "No match", "Try another word or category") }
        }
        items(shown, key = { it.name }) { tpl ->
            val color = habitColor(categoryColorIndex(tpl.category))
            val added = tpl.name.lowercase() in existing
            Card(
                Modifier.fillMaxWidth().animateItem(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    IconBubble(habitIcon(tpl.icon), color, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tpl.name, fontWeight = FontWeight.SemiBold)
                        Text(tpl.what, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${tpl.category} · ${scheduleText(tpl.daysMask)} · ${TIME_LABELS[tpl.timeOfDay]}",
                            fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (added) {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        }
                    } else {
                        Button(onClick = {
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
                        }) { Text("Add") }
                    }
                }
            }
        }
    }
}
