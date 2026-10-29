package com.faheem.habits

import android.app.Application
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class ExpenseViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).expenseDao()
    private val prefs = app.getSharedPreferences("momentum", Context.MODE_PRIVATE)

    val expenses: StateFlow<List<Expense>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _budget = MutableStateFlow(prefs.getString("budget", "0")?.toDoubleOrNull() ?: 0.0)
    val budget: StateFlow<Double> = _budget

    fun setBudget(v: Double) {
        prefs.edit().putString("budget", v.toString()).apply()
        _budget.value = v
    }

    fun add(title: String, amount: Double, category: String) {
        viewModelScope.launch {
            dao.insert(Expense(title = title.trim(), amount = amount, category = category, epochDay = today()))
        }
    }

    fun remove(e: Expense) {
        viewModelScope.launch { dao.delete(e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(vm: ExpenseViewModel = viewModel()) {
    val expenses by vm.expenses.collectAsState()
    val budget by vm.budget.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }

    val month = YearMonth.now()
    val thisMonth = expenses.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
    val spent = thisMonth.sumOf { it.amount }
    val over = budget > 0 && spent > budget
    val fraction = if (budget > 0) (spent / budget).toFloat() else 0f
    val byCategory = thisMonth.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }

    if (showBudget) {
        var input by remember { mutableStateOf(if (budget > 0) budget.toLong().toString() else "") }
        FormSheet("Monthly budget", onDismiss = { showBudget = false }) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Budget") },
                prefix = { Text(CURRENCY) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            BigButton("Save budget") {
                vm.setBudget(input.toDoubleOrNull() ?: 0.0)
                showBudget = false
            }
        }
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(categories[0].name) }
        FormSheet("New expense", onDismiss = { showAdd = false }) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                prefix = { Text(CURRENCY) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c.name,
                        onClick = { category = c.name },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) }
                    )
                }
            }
            BigButton("Save expense") {
                val a = amount.toDoubleOrNull()
                if (a != null && a > 0) {
                    vm.add(title.ifBlank { category }, a, category)
                    showAdd = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spent this month", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                        TextButton(onClick = { showBudget = true }) {
                            Text("Budget", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(money(spent), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(
                        fraction = fraction,
                        color = if (over) Color(0xFFFFB4B4) else Color.White,
                        track = Color.White.copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            budget <= 0 -> "Tap Budget to set a monthly limit"
                            over -> "Over budget by ${money(spent - budget)}"
                            else -> "${money(budget - spent)} left of ${money(budget)}"
                        },
                        fontSize = 14.sp
                    )
                }
            }
            if (byCategory.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        byCategory.entries.sortedByDescending { it.value }.forEach { (name, total) ->
                            val c = catOf(name)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    IconBubble(c.icon, c.color, 36.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(money(total), fontWeight = FontWeight.Bold)
                                    Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Recent expenses") }
            if (expenses.isEmpty()) {
                item { EmptyState(Icons.Rounded.AccountBalanceWallet, "No expenses yet", "Tap “Add expense” to log one") }
            }
            items(expenses, key = { it.id }) { e ->
                val c = catOf(e.category)
                Card(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(c.icon, c.color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontWeight = FontWeight.SemiBold)
                            Text("${e.category} · ${dayText(e.epochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(e.amount), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { vm.remove(e) }) {
                            Icon(Icons.Rounded.Close, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add expense") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}
