package com.faheem.habits.features.loans
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Application
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
        viewModelScope.launch { dao.insert(Loan(person = person.trim(), amount = amount, lent = lent, note = note.trim(), epochDay = today())) }
    }
    fun update(loan: Loan) { viewModelScope.launch { dao.update(loan) } }
    fun toggleSettled(loan: Loan) { update(loan.copy(settled = !loan.settled)) }
    fun remove(loan: Loan) { viewModelScope.launch { dao.delete(loan) } }
}

private val LentColor = Color(0xFF16A34A)
private val BorrowedColor = Color(0xFFF59E0B)

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

    val openLoans = loans.filter { !it.settled }
    val settledLoans = loans.filter { it.settled }
    val toReceive = openLoans.filter { it.lent }.sumOf { it.amount }
    val toPay = openLoans.filter { !it.lent }.sumOf { it.amount }
    val net = toReceive - toPay
    val filtered = (if (tab == 2) settledLoans else openLoans).filter { loan ->
        (direction == 0 || (direction == 1 && loan.lent) || (direction == 2 && !loan.lent)) &&
            (query.isBlank() || loan.person.contains(query, true) || loan.note.contains(query, true))
    }

    if (showAdd) LoanEditorSheet(null, { showAdd = false }) { person, amount, lent, note -> vm.add(person, amount, lent, note); showAdd = false }
    editing?.let { loan -> LoanEditorSheet(loan, { editing = null }) { person, amount, lent, note -> vm.update(loan.copy(person = person, amount = amount, lent = lent, note = note)); editing = null; selected = null } }
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
            text = { Text("${loan.person} · ${money(loan.amount)} will be removed from your ledger.") },
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
                    onClick = { showAdd = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add record") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
        NavigationBar(windowInsets = WindowInsets(0.dp)) {
            listOf("Overview", "Open", "Settled").forEachIndexed { index, label ->
                val icon = when (index) { 0 -> Icons.Rounded.Dashboard; 1 -> Icons.Rounded.SwapHoriz; else -> Icons.Rounded.TaskAlt }
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun LoanOverview(
    openLoans: List<Loan>, settledCount: Int, toReceive: Double, toPay: Double, net: Double,
    onOpenLedger: () -> Unit, onSelect: (Loan) -> Unit
) {
    val peopleCount = openLoans.map { it.person.trim().lowercase() }.distinct().size
    val recent = openLoans.take(4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Text("Outstanding balance", fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                Text(if (net >= 0) "${money(net)} in your favour" else "${money(-net)} net owed", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BalancePill("To receive", money(toReceive), LentColor, Modifier.weight(1f))
                    BalancePill("To repay", money(toPay), BorrowedColor, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoanMetric("Open records", "${openLoans.size}", Modifier.weight(1f))
                LoanMetric("People", "$peopleCount", Modifier.weight(1f))
                LoanMetric("Settled", "$settledCount", Modifier.weight(1f))
            }
        }
        item { SectionLabel("Needs attention") }
        if (recent.isEmpty()) item { EmptyState(Icons.Rounded.CheckCircle, "All clear", "Add a lend or borrow record whenever you need to track one") }
        items(recent, key = { it.id }) { LoanRow(it) { onSelect(it) } }
        if (openLoans.size > recent.size) item { TextButton(onClick = onOpenLedger) { Text("View all ${openLoans.size} open records") } }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Keep notes specific, like “March utilities” or “paid for dinner,” so you can recognize each record later.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoanLedger(
    loans: List<Loan>, query: String, onQuery: (String) -> Unit, direction: Int, onDirection: (Int) -> Unit,
    settled: Boolean, onSelect: (Loan) -> Unit, onEmptyAdd: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                shape = RoundedCornerShape(16.dp)
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
            val total = loans.sumOf { it.amount }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (settled) "Settled records" else "Open records", fontWeight = FontWeight.SemiBold)
                        Text("${loans.size} records matching your filters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(total), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (loans.isEmpty()) {
            item {
                Column {
                    EmptyState(if (settled) Icons.Rounded.TaskAlt else Icons.Rounded.SwapHoriz, if (settled) "No settled records" else "No matching records", if (settled) "Once an item is settled, it will be kept here" else "Try another name, note or filter")
                    if (!settled && query.isBlank() && direction == 0) Button(onClick = onEmptyAdd, modifier = Modifier.fillMaxWidth()) { Text("Add a record") }
                }
            }
        }
        items(loans, key = { it.id }) { LoanRow(it) { onSelect(it) } }
    }
}

@Composable
private fun LoanRow(loan: Loan, onClick: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
    Card(Modifier.fillMaxWidth().alpha(if (loan.settled) .68f else 1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(loan.person, fontWeight = FontWeight.SemiBold)
                Text("${if (loan.settled) "Settled" else if (loan.lent) "Owes you" else "You owe"} · ${dayText(loan.epochDay)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loan.note.isNotBlank()) Text(loan.note, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if (loan.lent) "+" else "−") + money(loan.amount), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BalancePill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .16f)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = .85f))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
private fun LoanMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) { Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LoanDetailSheet(loan: Loan, onDismiss: () -> Unit, onSettle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
    FormSheet(loan.person, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (loan.lent) "They owe you" else "You owe them", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(loan.amount), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
        LoanDetailLine("Recorded", dayText(loan.epochDay))
        LoanDetailLine("Status", if (loan.settled) "Settled" else "Open")
        if (loan.note.isNotBlank()) LoanDetailLine("Note", loan.note)
        BigButton(if (loan.settled) "Reopen record" else "Mark as settled", onSettle)
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit record") }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Delete record", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LoanDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoanEditorSheet(initial: Loan?, onDismiss: () -> Unit, onSave: (String, Double, Boolean, String) -> Unit) {
    var person by remember(initial) { mutableStateOf(initial?.person ?: "") }
    var amount by remember(initial) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var lent by remember(initial) { mutableStateOf(initial?.lent ?: true) }
    FormSheet(if (initial == null) "Add loan record" else "Edit loan record", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lent, onClick = { lent = true }, label = { Text("I lent") })
            FilterChip(selected = !lent, onClick = { lent = false }, label = { Text("I borrowed") })
        }
        OutlinedTextField(person, { person = it }, label = { Text("Person") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("Amount") }, prefix = { Text(CURRENCY) }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        BigButton(if (initial == null) "Save record" else "Save changes") {
            amount.toDoubleOrNull()?.takeIf { person.isNotBlank() && it > 0 }?.let { onSave(person.trim(), it, lent, note.trim()) }
        }
    }
}
