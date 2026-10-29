package com.faheem.habits

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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

    val loans: StateFlow<List<Loan>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(person: String, amount: Double, lent: Boolean, note: String) {
        viewModelScope.launch {
            dao.insert(Loan(person = person.trim(), amount = amount, lent = lent, note = note.trim(), epochDay = today()))
        }
    }

    fun toggleSettled(l: Loan) {
        viewModelScope.launch { dao.update(l.copy(settled = !l.settled)) }
    }

    fun remove(l: Loan) {
        viewModelScope.launch { dao.delete(l) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(vm: LoanViewModel = viewModel()) {
    val loans by vm.loans.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    val toReceive = loans.filter { !it.settled && it.lent }.sumOf { it.amount }
    val toPay = loans.filter { !it.settled && !it.lent }.sumOf { it.amount }

    if (showAdd) {
        var person by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var lent by remember { mutableStateOf(true) }
        FormSheet("New loan", onDismiss = { showAdd = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lent, onClick = { lent = true }, label = { Text("I lent") })
                FilterChip(selected = !lent, onClick = { lent = false }, label = { Text("I borrowed") })
            }
            OutlinedTextField(
                value = person, onValueChange = { person = it },
                label = { Text("Person") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount") }, prefix = { Text(CURRENCY) }, singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
            BigButton("Save loan") {
                val a = amount.toDoubleOrNull()
                if (person.isNotBlank() && a != null && a > 0) {
                    vm.add(person, a, lent, note)
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
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("You will get", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                            Text(money(toReceive), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("You owe", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                            Text(money(toPay), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val net = toReceive - toPay
                    Text(
                        if (net >= 0) "Net: ${money(net)} in your favour" else "Net: you owe ${money(-net)}",
                        fontSize = 14.sp
                    )
                }
            }
            item { SectionLabel("Ledger") }
            if (loans.isEmpty()) {
                item { EmptyState(Icons.Rounded.SwapHoriz, "No loans yet", "Tap “New loan” to record one") }
            }
            items(loans, key = { it.id }) { l ->
                val color = if (l.lent) Positive else Warning
                Card(
                    Modifier.fillMaxWidth().animateItem().alpha(if (l.settled) 0.6f else 1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.person, fontWeight = FontWeight.SemiBold)
                                Text(
                                    (if (l.lent) "Owes you" else "You owe") + " · " + dayText(l.epochDay) +
                                        if (l.note.isNotBlank()) " · ${l.note}" else "",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (l.lent) "+" else "-") + money(l.amount),
                                color = color, fontWeight = FontWeight.Bold
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.toggleSettled(l) }) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (l.settled) "Settled · Reopen" else "Mark settled")
                            }
                            IconButton(onClick = { vm.remove(l) }) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New loan") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}
