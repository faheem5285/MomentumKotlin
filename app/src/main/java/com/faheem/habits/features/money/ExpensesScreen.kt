package com.faheem.habits.features.money
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


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
import java.time.format.DateTimeFormatter

class ExpenseViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).expenseDao()
    private val prefs = app.getSharedPreferences("momentum", Context.MODE_PRIVATE)
    val expenses: StateFlow<List<Expense>> = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _budget = MutableStateFlow(prefs.getString("budget", "0")?.toDoubleOrNull() ?: 0.0)
    val budget: StateFlow<Double> = _budget
    fun categoryBudget(category: String) = prefs.getString("money_limit_${category}", "0")?.toDoubleOrNull() ?: 0.0
    fun setCategoryBudget(category: String, value: Double) { prefs.edit().putString("money_limit_${category}", value.toString()).apply() }
    fun setBudget(value: Double) { prefs.edit().putString("budget", value.toString()).apply(); _budget.value = value }
    fun add(title: String, amount: Double, category: String) {
        viewModelScope.launch { dao.insert(Expense(title = title.trim(), amount = amount, category = category, epochDay = today())) }
    }
    fun remove(expense: Expense) { viewModelScope.launch { dao.delete(expense) } }
}

@Composable
fun ExpensesScreen(vm: ExpenseViewModel = viewModel()) {
    val expenses by vm.expenses.collectAsState()
    val budget by vm.budget.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var showAdd by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }

    val inMonth = remember(expenses, month) {
        expenses.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
    }
    val spent = remember(inMonth) { inMonth.sumOf { it.amount } }
    val previousMonthSpent = remember(expenses, month) {
        val previousMonth = month.minusMonths(1)
        expenses.asSequence()
            .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == previousMonth }
            .sumOf { it.amount }
    }
    val byCategory = remember(inMonth) {
        inMonth.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amount } }
    }
    val budgetFraction = if (budget > 0) (spent / budget).toFloat() else 0f
    val filtered = remember(inMonth, category, query) {
        inMonth.filter {
            (category == "All" || it.category == category) &&
                (query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true))
        }
    }

    if (showBudget) BudgetSheet(budget, { showBudget = false }) { vm.setBudget(it); showBudget = false }
    if (showAdd) AddExpenseSheet({ showAdd = false }) { title, amount, cat -> vm.add(title, amount, cat); showAdd = false }
    deleting?.let { expense ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete expense?") },
            text = { Text("${expense.title} · ${money(expense.amount)} will be removed from your history.") },
            confirmButton = { TextButton(onClick = { vm.remove(expense); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { if (month < YearMonth.now()) month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                Icon(Icons.Rounded.ChevronRight, "Next month")
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> OverviewScreen(month, inMonth, spent, previousMonthSpent, budget, budgetFraction, byCategory, vm, onBudget = { showBudget = true }, onDelete = { deleting = it }, onAll = { tab = 1 })
                1 -> ActivityScreen(filtered, query, { query = it }, category, { category = it }, onDelete = { deleting = it })
                else -> BudgetScreen(spent, budget, budgetFraction, byCategory, vm, onBudget = { showBudget = true })
            }
            ExtendedFloatingActionButton(
                onClick = { showAdd = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add expense") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
        NavigationBar(windowInsets = WindowInsets(0.dp)) {
            listOf("Overview", "Activity", "Budgets").forEachIndexed { index, label ->
                val icon = when (index) { 0 -> Icons.Rounded.Dashboard; 1 -> Icons.Rounded.ReceiptLong; else -> Icons.Rounded.PieChart }
                NavigationBarItem(tab == index, { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    month: YearMonth, expenses: List<Expense>, spent: Double, previousMonthSpent: Double, budget: Double, fraction: Float, byCategory: Map<String, Double>,
    vm: ExpenseViewModel, onBudget: () -> Unit, onDelete: (Expense) -> Unit, onAll: () -> Unit
) {
    val today = LocalDate.now()
    val days = if (month == YearMonth.now()) today.dayOfMonth else month.lengthOfMonth()
    val average = if (days > 0) spent / days else 0.0
    val projection = average * month.lengthOfMonth()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monthly spending", fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                        Text(money(spent), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onBudget) { Text("Set budget", color = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(fraction, if (budget > 0 && spent > budget) Color(0xFFFFB4B4) else Color.White, Color.White.copy(alpha = .25f))
                Spacer(Modifier.height(7.dp))
                Text(when { budget <= 0 -> "Add a monthly limit to track your pace"; spent > budget -> "${money(spent - budget)} over your limit"; else -> "${money(budget - spent)} remaining" }, fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyMetric("Daily average", money(average), Modifier.weight(1f))
                MoneyMetric("Month-end estimate", money(projection), Modifier.weight(1f))
            }
        }
        item {
            val difference = spent - previousMonthSpent
            val comparison = when {
                previousMonthSpent <= 0.0 && spent <= 0.0 -> "No spending in either month"
                previousMonthSpent <= 0.0 -> "Spending started this month"
                difference < 0.0 -> "${money(-difference)} less than last month"
                difference > 0.0 -> "${money(difference)} more than last month"
                else -> "Same spending as last month"
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (difference <= 0.0) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = if (difference <= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Compared with last month", fontWeight = FontWeight.SemiBold)
                        Text(comparison, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(previousMonthSpent), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionLabel("Spending by category") }
        if (byCategory.isEmpty()) item { EmptyState(Icons.Rounded.PieChart, "No spending this month", "Add an expense to see where your money goes") }
        items(byCategory.entries.sortedByDescending { it.value }, key = { it.key }) { (name, total) -> CategorySpendRow(name, total, vm.categoryBudget(name)) }
        item { SectionLabel("Recent activity") }
        if (expenses.isEmpty()) item { EmptyState(Icons.Rounded.AccountBalanceWallet, "No expenses yet", "Add your first expense to get started") }
        items(expenses.take(5), key = { it.id }) { ExpenseRow(it) { onDelete(it) } }
        if (expenses.size > 5) item { TextButton(onClick = onAll) { Text("See all ${expenses.size} expenses") } }
    }
}

@Composable
private fun ActivityScreen(
    expenses: List<Expense>, query: String, onQuery: (String) -> Unit, category: String,
    onCategory: (String) -> Unit, onDelete: (Expense) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search expenses") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(16.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + categories.map { it.name }).forEach { name -> FilterChip(category == name, { onCategory(name) }, label = { Text(name) }) }
            }
        }
        item { SectionLabel("${expenses.size} expenses · ${money(expenses.sumOf { it.amount })}") }
        if (expenses.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, "No matching expenses", "Try a different search or category") }
        items(expenses, key = { it.id }) { ExpenseRow(it) { onDelete(it) } }
    }
}

@Composable
private fun BudgetScreen(spent: Double, budget: Double, fraction: Float, byCategory: Map<String, Double>, vm: ExpenseViewModel, onBudget: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Text("Monthly plan", fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                Text(if (budget > 0) money(budget) else "No limit set", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${money(spent)} spent · ${if (budget > spent) money(budget - spent) + " remaining" else "review your spending"}", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                ProgressBar(fraction, if (budget > 0 && spent > budget) Color(0xFFFFB4B4) else Color.White, Color.White.copy(alpha = .25f))
                TextButton(onClick = onBudget) { Text("Edit monthly limit", color = Color.White) }
            }
        }
        item { SectionLabel("Category limits") }
        items(categories, key = { it.name }) { c -> CategoryBudgetCard(c.name, byCategory[c.name] ?: 0.0, vm.categoryBudget(c.name)) { vm.setCategoryBudget(c.name, it) } }
    }
}

@Composable
private fun MoneyMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp)); Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategorySpendRow(name: String, total: Double, limit: Double) {
    val c = catOf(name)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(c.icon, c.color, 38.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(if (limit > 0) if (total <= limit) "${money(limit - total)} left" else "${money(total - limit)} over limit" else "No category limit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(total), fontWeight = FontWeight.Bold)
            }
            if (limit > 0) ProgressBar((total / limit).toFloat(), if (total > limit) MaterialTheme.colorScheme.error else c.color, MaterialTheme.colorScheme.surfaceVariant, height = 5.dp)
        }
    }
}

@Composable
private fun CategoryBudgetCard(name: String, spent: Double, limit: Double, onSave: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val c = catOf(name)
    if (editing) {
        var input by remember(limit) { mutableStateOf(if (limit > 0) limit.toString() else "") }
        AlertDialog(
            onDismissRequest = { editing = false }, title = { Text("$name limit") },
            text = { OutlinedTextField(input, { input = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Monthly limit") }, prefix = { Text(CURRENCY) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
            confirmButton = { TextButton(onClick = { input.toDoubleOrNull()?.takeIf { it >= 0 }?.let(onSave); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(c.icon, c.color, 38.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(if (limit > 0) "${money(spent)} of ${money(limit)}" else "${money(spent)} spent · no limit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editing = true }) { Text(if (limit > 0) "Edit" else "Set limit") }
            }
            if (limit > 0) {
                ProgressBar((spent / limit).toFloat(), if (spent > limit) MaterialTheme.colorScheme.error else c.color, MaterialTheme.colorScheme.surfaceVariant, height = 6.dp)
                Text(if (spent > limit) "${money(spent - limit)} over limit" else "${money(limit - spent)} remaining", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    val c = catOf(expense.category)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(c.icon, c.color); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.SemiBold)
                Text("${expense.category} · ${dayText(expense.epochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(expense.amount), fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BudgetSheet(value: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    FormSheet("Monthly budget", onDismiss) {
        OutlinedTextField(input, { input = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Spending limit") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        BigButton("Save budget") { input.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSave) }
    }
}

@Composable
private fun AddExpenseSheet(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories[0].name) }
    FormSheet("New expense", onDismiss) {
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, placeholder = { Text("e.g. Groceries, bus fare") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Text("Category", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> FilterChip(category == c.name, { category = c.name }, label = { Text(c.name) }, leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) }) }
        }
        BigButton("Save expense") { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(title.ifBlank { category }, it, category) } }
    }
}
