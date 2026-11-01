@file:Suppress("FunctionNaming")

package com.faheem.habits.features.loans
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoanViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).loanDao()
    val loans: StateFlow<List<Loan>> = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(person: String, amount: Double, lent: Boolean, note: String) {
        viewModelScope.launch { dao.insert(Loan(person = person.trim(), amountMinorUnits = amount.toMinorUnits(), lent = lent, note = note.trim(), epochDay = today())) }
    }
    fun update(loan: Loan) { viewModelScope.launch { dao.update(loan) } }
    fun toggleSettled(loan: Loan) { update(loan.copy(settled = !loan.settled)) }
    fun remove(loan: Loan) { viewModelScope.launch { dao.delete(loan) } }
}

private val LentColor = Positive
private val BorrowedColor = AccentAmber

@Composable
fun LoansScreen(vm: LoanViewModel = viewModel()) {
    val loans by vm.loans.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Loan?>(null) }
    var editing by remember { mutableStateOf<Loan?>(null) }
    var deleting by remember { mutableStateOf<Loan?>(null) }

    val openLoans = remember(loans) { loans.filter { !it.settled } }
    val settledLoans = remember(loans) { loans.filter { it.settled } }
    val toReceive = remember(openLoans) { openLoans.asSequence().filter { it.lent }.sumOf { it.amountMinorUnits } }
    val toPay = remember(openLoans) { openLoans.asSequence().filter { !it.lent }.sumOf { it.amountMinorUnits } }
    val net = toReceive - toPay
    val filtered = remember(openLoans, settledLoans, tab, direction, query) {
        (if (tab == 2) settledLoans else openLoans).filter { loan ->
            (direction == 0 || (direction == 1 && loan.lent) || (direction == 2 && !loan.lent)) &&
                (query.isBlank() || loan.person.contains(query, true) || loan.note.contains(query, true))
        }
    }

    if (showAdd) LoanEditorSheet(null, { showAdd = false }) { person, amount, lent, note -> vm.add(person, amount, lent, note); showAdd = false }
    editing?.let { loan -> LoanEditorSheet(loan, { editing = null }) { person, amount, lent, note -> vm.update(loan.copy(person = person, amountMinorUnits = amount.toMinorUnits(), lent = lent, note = note)); editing = null; selected = null } }
    selected?.let { loan ->
        LoanDetailSheet(
            loan = loan,
            onDismiss = { selected = null },
            onSettle = { vm.toggleSettled(loan); selected = null },
            onEdit = { editing = loan; selected = null },
            onDelete = { deleting = loan; selected = null }
        )
    }
    deleting?.let { loan ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete this record?") },
            text = { Text("${loan.person} · ${money(loan.amountMinorUnits)} will be removed from your ledger.") },
            confirmButton = { TextButton(onClick = { vm.remove(loan); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LoanOverview(openLoans, settledLoans.size, toReceive, toPay, net, onOpenLedger = { tab = 1 }, onSelect = { selected = it })
                else -> LoanLedger(
                    loans = filtered,
                    query = query,
                    onQuery = { query = it },
                    direction = direction,
                    onDirection = { direction = it },
                    settled = tab == 2,
                    onSelect = { selected = it },
                    onEmptyAdd = { showAdd = true }
                )
            }
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add record", fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                )
            }
        }
        MomentumTabs(
            tabs = listOf(
                MomentumTab("Overview", Icons.Rounded.Dashboard),
                MomentumTab("Open", Icons.Rounded.SwapHoriz),
                MomentumTab("Settled", Icons.Rounded.TaskAlt)
            ),
            selected = tab,
            onSelect = { tab = it }
        )
    }
}

@Composable
private fun LoanOverview(
    openLoans: List<Loan>, settledCount: Int, toReceive: Long, toPay: Long, net: Long,
    onOpenLedger: () -> Unit, onSelect: (Loan) -> Unit
) {
    val peopleCount = openLoans.map { it.person.trim().lowercase() }.distinct().size
    val recent = openLoans.take(4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Text("Outstanding balance", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                Text(
                    if (net >= 0) "${money(net)} in your favour" else "${money(-net)} net owed",
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BalancePill("To receive", money(toReceive), Modifier.weight(1f))
                    BalancePill("To repay", money(toPay), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoanMetric("Open", "${openLoans.size}", Icons.Rounded.ReceiptLong, Modifier.weight(1f))
                LoanMetric("People", "$peopleCount", Icons.Rounded.People, Modifier.weight(1f))
                LoanMetric("Settled", "$settledCount", Icons.Rounded.TaskAlt, Modifier.weight(1f))
            }
        }
        if (openLoans.isNotEmpty()) {
            item {
                val slices = buildList {
                    if (toReceive > 0) add(ChartSlice("To receive", toReceive.toFloat(), Positive))
                    if (toPay > 0) add(ChartSlice("To repay", toPay.toFloat(), Warning))
                }
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier.width(146.dp),
                            centerValue = money(kotlin.math.abs(net)),
                            centerLabel = if (net >= 0) "in your favour" else "net owed",
                            thickness = 18.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Balance split", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            ChartLegend(
                                slices = slices,
                                maxRows = 2,
                                valueFormatter = { money(it.toDouble()) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Across $peopleCount ${if (peopleCount == 1) "person" else "people"}",
                                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                val byPerson = openLoans
                    .groupBy { it.person.trim().ifBlank { "Unnamed" } }
                    .mapValues { (_, loans) -> loans.sumOf { it.amountMinorUnits } / 100.0 }
                    .entries
                    .sortedByDescending { it.value }
                    .take(6)
                    .mapIndexed { index, entry ->
                        ChartSlice(entry.key, entry.value.toFloat(), chartColor(index))
                    }
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.People, MaterialTheme.colorScheme.secondary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("By person", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Outstanding amounts",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalBarList(
                        items = byPerson,
                        maxItems = 6,
                        valueFormatter = { money(it.toDouble()) }
                    )
                }
            }
        }
        item {
            SectionHeader(
                title = "Needs attention",
                subtitle = if (openLoans.isEmpty()) null else "${openLoans.size} open record${if (openLoans.size == 1) "" else "s"}",
                actionLabel = if (openLoans.size > recent.size) "View all" else null,
                onAction = if (openLoans.size > recent.size) onOpenLedger else null
            )
        }
        if (recent.isEmpty()) item { EmptyState(Icons.Rounded.CheckCircle, "All clear", "Add a lend or borrow record whenever you need to track one") }
        items(recent, key = { it.id }) { LoanRow(it) { onSelect(it) } }
        item {
            InfoBanner(
                icon = Icons.Rounded.Lightbulb,
                text = "Keep notes specific, like “March utilities” or “paid for dinner,” so you can recognize each record later.",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun LoanLedger(
    loans: List<Loan>, query: String, onQuery: (String) -> Unit, direction: Int, onDirection: (Int) -> Unit,
    settled: Boolean, onSelect: (Loan) -> Unit, onEmptyAdd: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search people or notes") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Lent", "Borrowed").forEachIndexed { index, label ->
                    FilterChip(selected = direction == index, onClick = { onDirection(index) }, label = { Text(label) })
                }
            }
        }
        item {
            val total = loans.sumOf { it.amountMinorUnits }
            PremiumCard(accent = if (settled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (settled) "Settled records" else "Open records", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${loans.size} record${if (loans.size == 1) "" else "s"} matching your filters",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(money(total), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (loans.isEmpty()) {
            item {
                Column {
                    EmptyState(
                        if (settled) Icons.Rounded.TaskAlt else Icons.Rounded.SwapHoriz,
                        if (settled) "No settled records" else "No matching records",
                        if (settled) "Once an item is settled, it will be kept here" else "Try another name, note or filter"
                    )
                    if (!settled && query.isBlank() && direction == 0) {
                        SecondaryButton("Add a record", onEmptyAdd, icon = Icons.Rounded.Add)
                    }
                }
            }
        }
        items(loans, key = { it.id }) { LoanRow(it) { onSelect(it) } }
    }
}

@Composable
private fun LoanRow(loan: Loan, onClick: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
    PremiumCard(
        onClick = onClick,
        modifier = Modifier.alpha(if (loan.settled) 0.62f else 1f),
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .13f)),
                contentAlignment = Alignment.Center
            ) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(loan.person, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (loan.settled) "Settled" else if (loan.lent) "Owes you" else "You owe"} · ${dayText(loan.epochDay)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (loan.note.isNotBlank()) {
                    Text(loan.note, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text((if (loan.lent) "+" else "−") + money(loan.amountMinorUnits), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BalancePill(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .15f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.5.sp, color = Color.White.copy(alpha = .85f))
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
    }
}

@Composable
private fun LoanMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoanDetailSheet(loan: Loan, onDismiss: () -> Unit, onSettle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
    FormSheet(loan.person, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (loan.lent) "They owe you" else "You owe them", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(money(loan.amountMinorUnits), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = (-0.4).sp)
            }
            StatusPill(if (loan.settled) "Settled" else "Open", if (loan.settled) Positive else color)
        }
        SectionHeader("Details")
        LoanDetailLine("Recorded", dayText(loan.epochDay))
        LoanDetailLine("Status", if (loan.settled) "Settled" else "Open")
        if (loan.note.isNotBlank()) LoanDetailLine("Note", loan.note)
        PrimaryButton(if (loan.settled) "Reopen record" else "Mark as settled", onSettle)
        SecondaryButton("Edit record", onEdit, icon = Icons.Rounded.Edit)
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete record", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LoanDetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun LoanEditorSheet(initial: Loan?, onDismiss: () -> Unit, onSave: (String, Double, Boolean, String) -> Unit) {
    var person by remember(initial) { mutableStateOf(initial?.person ?: "") }
    var amount by remember(initial) { mutableStateOf(initial?.amountMinorUnits?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var lent by remember(initial) { mutableStateOf(initial?.lent ?: true) }
    FormSheet(if (initial == null) "Add loan record" else "Edit loan record", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lent, onClick = { lent = true }, label = { Text("I lent") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp)) })
            FilterChip(selected = !lent, onClick = { lent = false }, label = { Text("I borrowed") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp)) })
        }
        OutlinedTextField(person, { person = it }, label = { Text("Person") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        PrimaryButton(if (initial == null) "Save record" else "Save changes", onClick = {
            amount.toDoubleOrNull()?.takeIf { person.isNotBlank() && it > 0 }?.let { onSave(person.trim(), it, lent, note.trim()) }
        })
    }
}
