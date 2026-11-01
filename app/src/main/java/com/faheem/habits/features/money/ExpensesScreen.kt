@file:Suppress("FunctionNaming", "LongParameterList", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.money
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import com.faheem.habits.features.money.data.ExpenseRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {
    val expenses: StateFlow<List<Expense>> = repository.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budget: StateFlow<Long> = repository.budgetMinorUnits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    fun categoryBudget(category: String): StateFlow<Long> = repository.categoryBudgetMinorUnits(category)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    fun setCategoryBudget(category: String, value: Double) { viewModelScope.launch { repository.setCategoryBudget(category, value) } }
    fun setBudget(value: Double) { viewModelScope.launch { repository.setBudget(value) } }
    fun add(title: String, amount: Double, category: String) {
        viewModelScope.launch { repository.add(title, amount, category) }
    }
    fun remove(expense: Expense) { viewModelScope.launch { repository.delete(expense) } }
}

@Composable
fun ExpensesScreen(vm: ExpenseViewModel = hiltViewModel()) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val budgetMinorUnits by vm.budget.collectAsStateWithLifecycle()
    val budget = budgetMinorUnits / 100.0
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
    val spentMinorUnits = remember(inMonth) { inMonth.sumOf { it.amountMinorUnits } }
    val byCategory = remember(inMonth) {
        inMonth.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountMinorUnits } / 100.0 }
    }
    val spent = spentMinorUnits / 100.0
    val previousMonthSpent = remember(expenses, month) {
        val previousMonth = month.minusMonths(1)
        expenses.asSequence()
            .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == previousMonth }
            .sumOf { it.amountMinorUnits } / 100.0
    }
    val budgetFraction = if (budget > 0) (spent / budget).toFloat() else 0f
    val filtered = remember(inMonth, category, query) {
        inMonth.filter {
            (category == "All" || it.category == category) &&
                (query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true))
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val monthTrend = remember(expenses, month) {
        (5 downTo 0).map { offset ->
            val target = month.minusMonths(offset.toLong())
            target.format(DateTimeFormatter.ofPattern("MMM")) to
                expenses.asSequence()
                    .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == target }
                    .sumOf { it.amountMinorUnits } / 100.0
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { writeExpenseCsv(context, uri, inMonth) }
            }
            exportMessage = result.fold(
                { "Exported ${inMonth.size} expenses to CSV." },
                { "Export failed: ${it.message ?: "unknown error"}" }
            )
        }
    }

    if (showBudget) BudgetSheet(budget, { showBudget = false }) { vm.setBudget(it); showBudget = false }
    if (showAdd) AddExpenseSheet({ showAdd = false }) { title, amount, cat -> vm.add(title, amount, cat); showAdd = false }
    deleting?.let { expense ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete expense?") },
            text = { Text("${expense.title} · ${money(expense.amountMinorUnits)} will be removed from your history.") },
            confirmButton = { TextButton(onClick = { vm.remove(expense); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        MonthSwitcher(month, onPrevious = { month = month.minusMonths(1) }, onNext = { if (month < YearMonth.now()) month = month.plusMonths(1) })
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> OverviewScreen(
                    month, inMonth, spent, previousMonthSpent, budget, budgetFraction, byCategory, vm,
                    onBudget = { showBudget = true }, onDelete = { deleting = it }, onAll = { tab = 1 },
                    onExport = { exportLauncher.launch("momentum-expenses-$month.csv") },
                    exportMessage = exportMessage,
                    monthTrend = monthTrend
                )
                1 -> ActivityScreen(filtered, query, { query = it }, category, { category = it }, onDelete = { deleting = it })
                else -> BudgetScreen(spent, budget, budgetFraction, byCategory, vm, onBudget = { showBudget = true })
            }
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
            )
        }
        MomentumTabs(
            tabs = listOf(
                MomentumTab("Overview", Icons.Rounded.Dashboard),
                MomentumTab("Activity", Icons.Rounded.ReceiptLong),
                MomentumTab("Budgets", Icons.Rounded.PieChart)
            ),
            selected = tab,
            onSelect = { tab = it }
        )
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(Icons.Rounded.ChevronRight, "Next month")
        }
    }
}

@Composable
private fun OverviewScreen(
    month: YearMonth, expenses: List<Expense>, spent: Double, previousMonthSpent: Double, budget: Double, fraction: Float, byCategory: Map<String, Double>,
    vm: ExpenseViewModel, onBudget: () -> Unit, onDelete: (Expense) -> Unit, onAll: () -> Unit,
    onExport: () -> Unit, exportMessage: String?,
    monthTrend: List<Pair<String, Double>>
) {
    val today = LocalDate.now()
    val days = if (month == YearMonth.now()) today.dayOfMonth else month.lengthOfMonth()
    val average = if (days > 0) spent / days else 0.0
    val projection = average * month.lengthOfMonth()
    val daily = remember(expenses, month) {
        val count = month.lengthOfMonth()
        val totals = DoubleArray(count)
        expenses.forEach { expense ->
            val day = LocalDate.ofEpochDay(expense.epochDay).dayOfMonth
            if (day in 1..count) totals[day - 1] += expense.amountMinorUnits / 100.0
        }
        totals.toList()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monthly spending", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                        Text(money(spent), fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                    }
                    TextButton(onClick = onBudget) {
                        Text("Set budget", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                ProgressBar(fraction, if (budget > 0 && spent > budget) Color(0xFFFFC9C9) else Color.White, Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        budget <= 0 -> "Add a monthly limit to track your pace"
                        spent > budget -> "${money(spent - budget)} over your limit"
                        else -> "${money(budget - spent)} remaining"
                    },
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Daily average", money(average), "This month", icon = Icons.Rounded.ShowChart, modifier = Modifier.weight(1f))
                MetricTile("Month-end", money(projection), "Estimated", icon = Icons.Rounded.TrendingUp, modifier = Modifier.weight(1f))
            }
        }
        item { SectionHeader("Spending shape", subtitle = month.format(DateTimeFormatter.ofPattern("MMMM"))) }
        item {
            val slices = byCategory.entries
                .sortedByDescending { it.value }
                .map { (name, value) -> ChartSlice(name, value.toFloat(), catOf(name).color) }
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = slices,
                        modifier = Modifier.width(154.dp),
                        centerValue = money(spent),
                        centerLabel = "this month",
                        thickness = 19.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        ChartLegend(
                            slices = slices,
                            maxRows = 5,
                            valueFormatter = { money(it.toDouble()) }
                        )
                    }
                }
                if (slices.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add an expense to see how your month splits across categories.",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { SectionHeader("Daily spend", subtitle = "${days.coerceAtMost(month.lengthOfMonth())} days recorded") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Insights, MaterialTheme.colorScheme.primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Day by day", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Peak ${money(daily.maxOrNull() ?: 0.0)} · average ${money(average)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LineChart(
                    values = daily.map { it.toFloat() },
                    color = MaterialTheme.colorScheme.primary,
                    height = 136.dp
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Day 1", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Day ${daily.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("Six-month trend", subtitle = "Monthly totals") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                BarChart(
                    values = monthTrend.map { it.second.toFloat() },
                    labels = monthTrend.map { it.first },
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    ),
                    highlightIndex = monthTrend.lastIndex,
                    height = 150.dp,
                    valueFormatter = { value -> if (value >= 1000f) "${(value / 1000f).toInt()}k" else value.toInt().toString() }
                )
                Spacer(Modifier.height(8.dp))
                val change = (monthTrend.lastOrNull()?.second ?: 0.0) - (monthTrend.dropLast(1).lastOrNull()?.second ?: 0.0)
                Text(
                    when {
                        monthTrend.size < 2 -> "More history will appear as you add months"
                        change > 0 -> "${money(change)} more than the previous month"
                        change < 0 -> "${money(-change)} less than the previous month"
                        else -> "Level with the previous month"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            PremiumCard(accent = if (difference <= 0.0) Positive else MaterialTheme.colorScheme.error) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(
                        if (difference <= 0.0) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                        if (difference <= 0.0) Positive else MaterialTheme.colorScheme.error,
                        40.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Compared with last month", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(comparison, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(previousMonthSpent), fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Column {
                PremiumCard(onClick = onExport, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.TableChart, MaterialTheme.colorScheme.secondary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export month as CSV", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${expenses.size} expenses · opens in any spreadsheet",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.FileDownload, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                exportMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionHeader("Spending by category", subtitle = if (byCategory.isEmpty()) null else "${byCategory.size} active") }
        if (byCategory.isEmpty()) item { EmptyState(Icons.Rounded.PieChart, "No spending this month", "Add an expense to see where your money goes") }
        items(byCategory.entries.sortedByDescending { it.value }, key = { it.key }) { (name, total) ->
            val limit by vm.categoryBudget(name).collectAsStateWithLifecycle()
            CategorySpendRow(name, total, limit / 100.0)
        }
        item { SectionHeader("Recent activity", actionLabel = if (expenses.size > 5) "See all" else null, onAction = if (expenses.size > 5) onAll else null) }
        if (expenses.isEmpty()) item { EmptyState(Icons.Rounded.AccountBalanceWallet, "No expenses yet", "Add your first expense to get started") }
        items(expenses.take(5), key = { it.id }) { ExpenseRow(it) { onDelete(it) } }
    }
}

@Composable
private fun ActivityScreen(
    expenses: List<Expense>, query: String, onQuery: (String) -> Unit, category: String,
    onCategory: (String) -> Unit, onDelete: (Expense) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                query, onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search expenses") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(18.dp)
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All") + categories.map { it.name }).forEach { name -> FilterChip(category == name, { onCategory(name) }, label = { Text(name) }) }
            }
        }
        item { SectionHeader("${expenses.size} expenses · ${money(expenses.sumOf { it.amountMinorUnits })}") }
        if (expenses.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, "No matching expenses", "Try a different search or category") }
        items(expenses, key = { it.id }) { ExpenseRow(it) { onDelete(it) } }
    }
}

@Composable
private fun BudgetScreen(spent: Double, budget: Double, fraction: Float, byCategory: Map<String, Double>, vm: ExpenseViewModel, onBudget: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Text("Monthly plan", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                Text(if (budget > 0) money(budget) else "No limit set", fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                Text(
                    "${money(spent)} spent · ${if (budget > spent) money(budget - spent) + " remaining" else "review your spending"}",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(14.dp))
                ProgressBar(fraction, if (budget > 0 && spent > budget) Color(0xFFFFC9C9) else Color.White, Color.White.copy(alpha = 0.25f))
                TextButton(onClick = onBudget) { Text("Edit monthly limit", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
        item { SectionHeader("Category limits", subtitle = "Set a comfortable cap for each area") }
        items(categories, key = { it.name }) { c ->
            val limit by vm.categoryBudget(c.name).collectAsStateWithLifecycle()
            CategoryBudgetCard(c.name, byCategory[c.name] ?: 0.0, limit / 100.0) { vm.setCategoryBudget(c.name, it) }
        }
    }
}

@Composable
private fun CategorySpendRow(name: String, total: Double, limit: Double) {
    val c = catOf(name)
    PremiumCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(c.icon, c.color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (limit > 0) {
                        if (total <= limit) "${money(limit - total)} left of ${money(limit)}" else "${money(total - limit)} over limit"
                    } else "No category limit",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(money(total), fontWeight = FontWeight.Bold)
        }
        if (limit > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressBar((total / limit).toFloat(), if (total > limit) MaterialTheme.colorScheme.error else c.color, MaterialTheme.colorScheme.surfaceVariant, height = 5.dp)
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
    PremiumCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(c.icon, c.color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (limit > 0) "${money(spent)} of ${money(limit)}" else "${money(spent)} spent · no limit",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { editing = true }) { Text(if (limit > 0) "Edit" else "Set limit") }
        }
        if (limit > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressBar((spent / limit).toFloat(), if (spent > limit) MaterialTheme.colorScheme.error else c.color, MaterialTheme.colorScheme.surfaceVariant, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (spent > limit) "${money(spent - limit)} over limit" else "${money(limit - spent)} remaining",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    val c = catOf(expense.category)
    PremiumCard(contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(c.icon, c.color, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${expense.category} · ${dayText(expense.epochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(expense.amountMinorUnits), fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun BudgetSheet(value: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    FormSheet("Monthly budget", onDismiss) {
        OutlinedTextField(input, { input = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Spending limit") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        PrimaryButton("Save budget", onClick = { input.toDoubleOrNull()?.takeIf { it > 0 }?.let(onSave) })
    }
}

@Composable
private fun AddExpenseSheet(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories[0].name) }
    FormSheet("New expense", onDismiss) {
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, placeholder = { Text("e.g. Groceries, bus fare") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        SectionHeader("Category")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c -> FilterChip(category == c.name, { category = c.name }, label = { Text(c.name) }, leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) }) }
        }
        PrimaryButton("Save expense", onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(title.ifBlank { category }, it, category) } })
    }
}

private fun writeExpenseCsv(context: Context, uri: Uri, expenses: List<Expense>): Int {
    val header = "Date,Title,Category,Amount\n"
    val rows = expenses.sortedBy { it.epochDay }.joinToString("\n") { expense ->
        val date = LocalDate.ofEpochDay(expense.epochDay).toString()
        val amount = String.format(Locale.US, "%.2f", expense.amountMinorUnits / 100.0)
        listOf(date, expense.title.csvCell(), expense.category.csvCell(), amount).joinToString(",")
    }
    val body = header + rows + if (rows.isEmpty()) "" else "\n"
    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("Could not open the selected file")
    stream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    return expenses.size
}

private fun String.csvCell(): String =
    if (any { it in charArrayOf(',', '"', '\n', '\r') }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
