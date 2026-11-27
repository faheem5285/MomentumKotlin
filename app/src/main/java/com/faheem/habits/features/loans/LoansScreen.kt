@file:Suppress("FunctionNaming")

package com.faheem.habits.features.loans
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.features.loans.data.LoanRepository
import com.faheem.habits.features.money.outstandingByLoan
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek

@HiltViewModel
@Suppress("TooManyFunctions")
class LoanViewModel @Inject constructor(
    private val repository: LoanRepository,
    private val settings: MomentumSettingsRepository
) : ViewModel() {
    val loans: StateFlow<List<Loan>> = repository.loans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val people: StateFlow<List<LoanPerson>> = repository.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val payments: StateFlow<List<LoanPayment>> = repository.payments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val outstandingBalances: StateFlow<Map<Int, Long>> = combine(loans, payments) { currentLoans, currentPayments ->
        outstandingByLoan(currentLoans, currentPayments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val accountBalanceMinorUnits: StateFlow<Long?> = settings.loansAccountBalanceMinorUnits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setAccountBalance(minorUnits: Long) {
        viewModelScope.launch { settings.setLoansAccountBalanceMinorUnits(minorUnits) }
    }
    fun addPerson(person: LoanPerson) { viewModelScope.launch { repository.addPerson(person) } }
    fun updatePerson(person: LoanPerson) { viewModelScope.launch { repository.updatePerson(person) } }
    fun archivePerson(person: LoanPerson) { viewModelScope.launch { repository.archivePerson(person) } }
    fun deletePerson(person: LoanPerson) { viewModelScope.launch { repository.deletePerson(person) } }
    fun add(person: String, personId: Int?, amountMinorUnits: Long, lent: Boolean, note: String, title: String = "", dueEpochDay: Long? = null, status: String = "pending", proofUris: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.add(
                Loan(
                    person = person,
                    personId = personId,
                    amountMinorUnits = amountMinorUnits,
                    lent = lent,
                    note = note,
                    epochDay = today(),
                    title = title,
                    dueEpochDay = dueEpochDay,
                    status = status
                ),
                proofUris
            )
        }
    }
    fun updateLoan(loan: Loan, person: String, personId: Int?, proofUris: List<String> = emptyList()) {
        viewModelScope.launch { repository.updateLoan(loan, person, personId, proofUris) }
    }
    fun update(loan: Loan) {
        viewModelScope.launch { repository.update(loan) }
    }
    fun addAttachment(loanId: Int, uri: String) { viewModelScope.launch { repository.addAttachment(loanId, uri) } }
    fun attachments(loanId: Int) = repository.attachments(loanId)
    fun paymentsForLoan(loanId: Int) = repository.paymentsForLoan(loanId)
    fun paidAmount(loanId: Int) = repository.paidAmount(loanId)
    fun addPayment(loanId: Int, amountMinorUnits: Long, note: String = "") {
        viewModelScope.launch { repository.addPayment(loanId, amountMinorUnits, note) }
    }
    fun deletePayment(payment: LoanPayment) { viewModelScope.launch { repository.deletePayment(payment) } }
    fun toggleSettled(loan: Loan) { update(loan.copy(settled = !loan.settled)) }
    fun remove(loan: Loan) { viewModelScope.launch { repository.remove(loan) } }
}

private val LentColor = Positive
private val BorrowedColor = AccentAmber

private fun remainingFor(loan: Loan, balances: Map<Int, Long>): Long =
    balances[loan.id] ?: loan.amountMinorUnits

private enum class LoanDateRange(val label: String) {
    TODAY("Today"), THIS_WEEK("This week"), THIS_MONTH("This month"), THIS_YEAR("This year"), ALL_TIME("All time")
}

private fun LoanDateRange.includes(epochDay: Long, today: LocalDate = LocalDate.now()): Boolean {
    val date = LocalDate.ofEpochDay(epochDay)
    return when (this) {
        LoanDateRange.TODAY -> date == today
        LoanDateRange.THIS_WEEK -> !date.isBefore(today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) && !date.isAfter(today)
        LoanDateRange.THIS_MONTH -> date.year == today.year && date.month == today.month
        LoanDateRange.THIS_YEAR -> date.year == today.year
        LoanDateRange.ALL_TIME -> true
    }
}

@Composable
private fun LoanDateRangeFilters(selected: LoanDateRange, onSelect: (LoanDateRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        LoanDateRange.entries.forEach { range ->
            MomentumChip(selected = selected == range, onClick = { onSelect(range) }, label = range.label)
        }
    }
}

@Composable
private fun PersonAvatar(person: LoanPerson, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, person.profileImageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeBoundedImage(
                    context,
                    Uri.parse(person.profileImageUri),
                    ImagePreviewPolicy.AVATAR_SOURCE_BYTES,
                    ImagePreviewPolicy.AVATAR_DECODE_DIMENSION
                )?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(image!!, contentDescription = "${person.name} profile image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(person.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ImagePreviewDialog(uri: String, title: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeBoundedImage(context, Uri.parse(uri))?.asImageBitmap() }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (image != null) Image(image!!, contentDescription = title, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp), contentScale = ContentScale.Fit)
            else Text("This image can’t be previewed.")
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun openProofFile(context: android.content.Context, uri: String, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), mime.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "No app can preview this file type.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun LoansScreen(
    vm: LoanViewModel = hiltViewModel(),
    initialLoanId: Int? = null,
    openRequestToken: Long? = null
) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val outstandingBalances by vm.outstandingBalances.collectAsStateWithLifecycle()
    fun remaining(loan: Loan): Long = outstandingBalances[loan.id] ?: loan.amountMinorUnits
    var tab by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(0) }
    var dateRange by remember { mutableStateOf(LoanDateRange.ALL_TIME) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var showPersonEditor by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<LoanPerson?>(null) }
    var personDetails by remember { mutableStateOf<LoanPerson?>(null) }
    var addLoanForPerson by remember { mutableStateOf<LoanPerson?>(null) }
    var selected by remember { mutableStateOf<Loan?>(null) }
    var editing by remember { mutableStateOf<Loan?>(null) }
    var deleting by remember { mutableStateOf<Loan?>(null) }
    var deletingPerson by remember { mutableStateOf<LoanPerson?>(null) }
    var editingAccountBalance by remember { mutableStateOf(false) }

    LaunchedEffect(initialLoanId, openRequestToken, loans) {
        initialLoanId?.let { id ->
            loans.firstOrNull { it.id == id }?.let { selected = it }
        }
    }

    val accountBalanceMinorUnits by vm.accountBalanceMinorUnits.collectAsStateWithLifecycle()
    val dateFilteredLoans = remember(loans, dateRange) { loans.filter { dateRange.includes(it.epochDay) } }
    val openLoans = remember(dateFilteredLoans) { dateFilteredLoans.filter { !it.settled } }
    val settledLoans = remember(dateFilteredLoans) { dateFilteredLoans.filter { it.settled } }
    val toReceive = remember(openLoans, outstandingBalances) {
        openLoans.filter { it.lent }.sumMinorUnits { remaining(it) }
    }
    val toPay = remember(openLoans, outstandingBalances) {
        openLoans.filter { !it.lent }.sumMinorUnits { remaining(it) }
    }
    val net = saturatingSubtract(toReceive, toPay)
    val filtered = remember(openLoans, settledLoans, tab, direction, query) {
            (if (tab == 2) settledLoans else openLoans).filter { loan ->
                (direction == 0 || (direction == 1 && loan.lent) || (direction == 2 && !loan.lent)) &&
                (query.isBlank() || loan.person.contains(query, true) || loan.title.contains(query, true) ||
                    loan.note.contains(query, true) || loan.status.contains(query, true))
            }
    }

    val people by vm.people.collectAsStateWithLifecycle()
    if (editingAccountBalance) {
        AccountBalanceSheet(
            initialBalanceMinorUnits = accountBalanceMinorUnits,
            onDismiss = { editingAccountBalance = false },
            onSave = {
                vm.setAccountBalance(it)
                editingAccountBalance = false
            }
        )
    }
    if (showAdd) LoanEditorSheet(
        initial = null,
        people = people,
        onDismiss = { showAdd = false; addLoanForPerson = null },
        presetPerson = addLoanForPerson
    ) { personId, person, amountMinorUnits, lent, note, title, due, status, proofs ->
        vm.add(person, personId, amountMinorUnits, lent, note, title, due, status, proofs)
        showAdd = false
        addLoanForPerson = null
    }
    if (showPersonEditor) {
        PersonEditorSheet(editingPerson, onDismiss = { showPersonEditor = false; editingPerson = null }) { person ->
            if (editingPerson == null) vm.addPerson(person) else vm.updatePerson(person)
            showPersonEditor = false
            editingPerson = null
        }
    }
    editing?.let { loan -> LoanEditorSheet(loan, people, { editing = null }) { personId, person, amountMinorUnits, lent, note, title, due, status, proofs ->
        vm.updateLoan(loan.copy(amountMinorUnits = amountMinorUnits, lent = lent, note = note, title = title, dueEpochDay = due, status = status), person, personId, proofs)
        editing = null
        selected = null
    } }
    selected?.let { loan ->
        val attachments by vm.attachments(loan.id).collectAsStateWithLifecycle(initialValue = emptyList())
        val loanPayments by vm.paymentsForLoan(loan.id).collectAsStateWithLifecycle(initialValue = emptyList())
        LoanDetailSheet(
            loan = loan,
            outstandingMinorUnits = remaining(loan),
            payments = loanPayments,
            personProfile = people.firstOrNull { it.id == loan.personId },
            attachments = attachments,
            onDismiss = { selected = null },
            onSettle = { vm.toggleSettled(loan); selected = null },
            onEdit = { editing = loan; selected = null },
            onDelete = { deleting = loan; selected = null },
            onAttach = vm::addAttachment,
            onAddPayment = { amountMinorUnits, note -> vm.addPayment(loan.id, amountMinorUnits, note) },
            onDeletePayment = vm::deletePayment
        )
    }
    personDetails?.let { profile ->
        val currentProfile = people.firstOrNull { it.id == profile.id } ?: profile
        val personLoans = loans.filter {
            it.personId == currentProfile.id ||
                (it.personId == null && it.person.equals(currentProfile.name, ignoreCase = true))
        }.sortedWith(compareBy<Loan> { it.settled }.thenByDescending { it.epochDay })
        PersonDetailsSheet(
            person = currentProfile,
            loans = personLoans,
            outstandingBalances = outstandingBalances,
            onDismiss = { personDetails = null },
            onAddLoan = {
                addLoanForPerson = currentProfile
                personDetails = null
                showAdd = true
            },
            onEdit = {
                editingPerson = currentProfile
                personDetails = null
                showPersonEditor = true
            },
            onSelectLoan = { loan ->
                selected = loan
                personDetails = null
            }
        )
    }
    deleting?.let { loan ->
        MomentumDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete this record?") },
            text = { Text("${loan.person} · ${money(loan.amountMinorUnits)} will be removed from your ledger.") },
            confirmButton = { TextButton(onClick = { vm.remove(loan); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    deletingPerson?.let { person ->
        MomentumDialog(
            onDismissRequest = { deletingPerson = null },
            title = { Text("Delete ${person.name}?") },
            text = { Text("This removes the person profile. Existing loan records remain in your ledger, but they will no longer be linked to this profile.") },
            confirmButton = { TextButton(onClick = { vm.deletePerson(person); deletingPerson = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingPerson = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LoanOverview(
                    openLoans, people, outstandingBalances, settledLoans.size, toReceive, toPay, net,
                    dateRange = dateRange,
                    onDateRange = { dateRange = it },
                    accountBalanceMinorUnits = accountBalanceMinorUnits,
                    onEditAccountBalance = { editingAccountBalance = true },
                    onOpenLedger = { tab = 1 },
                    onSelect = { selected = it }
                )
                3 -> PeopleScreen(
                    people = people,
                    loans = loans,
                    outstandingBalances = outstandingBalances,
                    onAdd = { showPersonEditor = true },
                    onOpen = { personDetails = it },
                    onEdit = { editingPerson = it; showPersonEditor = true },
                    onDelete = { deletingPerson = it }
                )
                else -> LoanLedger(
                    loans = filtered,
                    people = people,
                    outstandingBalances = outstandingBalances,
                    query = query,
                    onQuery = { query = it },
                    direction = direction,
                    onDirection = { direction = it },
                    settled = tab == 2,
                    dateRange = dateRange,
                    onDateRange = { dateRange = it },
                    onSelect = { selected = it },
                    onEmptyAdd = { showAdd = true }
                )
            }
            if (tab != 2 && tab != 3) {
                MomentumFab(
                    text = "Add record",
                    onClick = { showAdd = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
                )
            }
        }
        MomentumTabs(
            tabs = listOf(
                MomentumTab("Overview", Icons.Rounded.Dashboard),
                MomentumTab("Open", Icons.Rounded.SwapHoriz),
                MomentumTab("Settled", Icons.Rounded.TaskAlt),
                MomentumTab("People", Icons.Rounded.People)
            ),
            selected = tab,
            onSelect = { tab = it }
        )
    }

}

@Composable
private fun PeopleScreen(
        people: List<LoanPerson>,
        loans: List<Loan>,
        outstandingBalances: Map<Int, Long>,
        onAdd: () -> Unit,
        onOpen: (LoanPerson) -> Unit,
        onEdit: (LoanPerson) -> Unit,
        onDelete: (LoanPerson) -> Unit
    ) {
        val loansByPerson = remember(people, loans) {
            val linked = loans.filter { it.personId != null }.groupBy { it.personId }
            val legacy = loans.filter { it.personId == null }.groupBy { it.person.trim().lowercase() }
            people.associate { person ->
                person.id to (linked[person.id].orEmpty() + legacy[person.name.trim().lowercase()].orEmpty())
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    HeroCaption("People")
                    Text("${people.size} people in your ledger", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Keep contact details and every related loan together.", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
            if (people.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.People, "No people yet", "Create a person before adding related loan records")
                    PrimaryButton("Add person", onAdd, icon = Icons.Rounded.PersonAdd)
                }
            }
            items(people, key = { it.id }) { person ->
                PremiumCard(onClick = { onOpen(person) }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(person, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, fontWeight = FontWeight.SemiBold)
                            Text(person.phone.ifBlank { person.address.ifBlank { "No contact details" } }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val related = loansByPerson[person.id].orEmpty()
                            if (related.isNotEmpty()) {
                                val openCount = related.count { !it.settled }
                                val receive = related.filter { !it.settled && it.lent }
                                    .sumMinorUnits { remainingFor(it, outstandingBalances) }
                                val repay = related.filter { !it.settled && !it.lent }
                                    .sumMinorUnits { remainingFor(it, outstandingBalances) }
                                Text(
                                    "${related.size} record${if (related.size == 1) "" else "s"} · $openCount open · ${money(receive)} to receive · ${money(repay)} to repay",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(person) }) { Icon(Icons.Rounded.Edit, "Edit ${person.name}") }
                        IconButton(onClick = { onDelete(person) }) { Icon(Icons.Rounded.DeleteOutline, "Delete person", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

@Composable
private fun PersonDetailsSheet(
    person: LoanPerson,
    loans: List<Loan>,
    outstandingBalances: Map<Int, Long>,
    onDismiss: () -> Unit,
    onAddLoan: () -> Unit,
    onEdit: () -> Unit,
    onSelectLoan: (Loan) -> Unit
) {
    val openLoans = loans.filterNot { it.settled }
    val toReceive = openLoans.filter { it.lent }.sumMinorUnits { remainingFor(it, outstandingBalances) }
    val toRepay = openLoans.filterNot { it.lent }.sumMinorUnits { remainingFor(it, outstandingBalances) }
    var previewAvatar by remember(person.id) { mutableStateOf(false) }

    FormSheet(person.name, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(person, 64.dp) {
                if (person.profileImageUri.isNotBlank()) previewAvatar = true
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${loans.size} record${if (loans.size == 1) "" else "s"} · ${openLoans.size} open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${person.name}")
            }
        }
        if (previewAvatar && person.profileImageUri.isNotBlank()) {
            ImagePreviewDialog(person.profileImageUri, "${person.name} profile photo") { previewAvatar = false }
        }
        if (person.phone.isNotBlank()) LoanDetailLine("Phone", person.phone)
        if (person.address.isNotBlank()) LoanDetailLine("Address", person.address)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoanMetric("To receive", money(toReceive), Icons.Rounded.ArrowUpward, Modifier.weight(1f))
            LoanMetric("To repay", money(toRepay), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
        }
        PrimaryButton("Add loan for ${person.name}", onAddLoan, icon = Icons.Rounded.Add)

        SectionHeader("Loan records", subtitle = "Select a record to view its details")
        if (loans.isEmpty()) {
            EmptyState(Icons.Rounded.ReceiptLong, "No loans yet", "Add the first loan directly to this person.")
        } else {
            loans.forEach { loan ->
                LoanRow(loan, person, outstandingBalances) { onSelectLoan(loan) }
            }
        }
    }
}

@Composable
private fun PersonEditorSheet(initial: LoanPerson?, onDismiss: () -> Unit, onSave: (LoanPerson) -> Unit) {
        var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
        var phone by remember(initial) { mutableStateOf(initial?.phone.orEmpty()) }
        var address by remember(initial) { mutableStateOf(initial?.address.orEmpty()) }
        var imageUri by remember(initial) { mutableStateOf(initial?.profileImageUri.orEmpty()) }
        var previewAvatar by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                imageUri = uri.toString()
            }
        }
        FormSheet(if (initial == null) "Add person" else "Edit person", onDismiss) {
            PersonAvatar(LoanPerson(name = name.ifBlank { "?" }, profileImageUri = imageUri), 72.dp) { if (imageUri.isNotBlank()) previewAvatar = true }
            if (previewAvatar && imageUri.isNotBlank()) ImagePreviewDialog(imageUri, name.ifBlank { "Profile photo" }) { previewAvatar = false }
            MomentumTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            MomentumTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MomentumTextField(address, { address = it }, label = { Text("Address") }, singleLine = true)
            SecondaryButton(if (imageUri.isBlank()) "Add profile image" else "Change profile image", onClick = { picker.launch(arrayOf("image/*")) }, icon = Icons.Rounded.AddPhotoAlternate)
            if (imageUri.isNotBlank()) Text("Profile image selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            PrimaryButton("Save person", onClick = {
                if (name.isNotBlank()) onSave(initial?.copy(name = name, phone = phone, address = address, profileImageUri = imageUri) ?: LoanPerson(name = name, phone = phone, address = address, profileImageUri = imageUri))
            })
    }
}

@Composable
private fun LoanOverview(
    openLoans: List<Loan>, people: List<LoanPerson>, outstandingBalances: Map<Int, Long>,
    settledCount: Int, toReceive: Long, toPay: Long, net: Long,
    dateRange: LoanDateRange, onDateRange: (LoanDateRange) -> Unit,
    accountBalanceMinorUnits: Long?,
    onEditAccountBalance: () -> Unit,
    onOpenLedger: () -> Unit,
    onSelect: (Loan) -> Unit
) {
    val peopleCount = openLoans.map { it.person.trim().lowercase() }.distinct().size
    val recent = openLoans.take(4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { LoanDateRangeFilters(dateRange, onDateRange) }
        item {
            HeroCard {
                HeroCaption("Outstanding balance")
                Text(
                    if (net >= 0) "${money(net)} in your favour" else "${money(-net)} net owed",
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BalancePill("To receive", money(toReceive), Modifier.weight(1f))
                    BalancePill("To repay", money(toPay), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = .15f))
                        .clickable(onClick = onEditAccountBalance)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("In my account", fontSize = 11.5.sp, color = Color.White.copy(alpha = .85f))
                        Text(
                            accountBalanceMinorUnits?.let(::money) ?: "Set account balance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        if (accountBalanceMinorUnits == null) "Add" else "Edit",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
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
                                valueFormatter = { money(it.toLong()) }
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
                    .mapValues { (_, loans) -> loans.sumMinorUnits { remainingFor(it, outstandingBalances) } }
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
                        valueFormatter = { money(it.toLong()) }
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
        items(recent, key = { it.id }) { loan ->
            LoanRow(loan, people.firstOrNull { it.id == loan.personId }, outstandingBalances) { onSelect(loan) }
        }
    }
}

@Composable
private fun LoanLedger(
    loans: List<Loan>, people: List<LoanPerson>, outstandingBalances: Map<Int, Long>,
    query: String, onQuery: (String) -> Unit, direction: Int, onDirection: (Int) -> Unit,
    settled: Boolean, dateRange: LoanDateRange, onDateRange: (LoanDateRange) -> Unit,
    onSelect: (Loan) -> Unit, onEmptyAdd: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            MomentumTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Search people or notes") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )
        }
        item {
            LoanDateRangeFilters(dateRange, onDateRange)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Lent", "Borrowed").forEachIndexed { index, label ->
                    MomentumChip(selected = direction == index, onClick = { onDirection(index) }, label = label)
                }
            }
        }
        item {
            val total = loans.sumMinorUnits { remainingFor(it, outstandingBalances) }
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
        items(loans, key = { it.id }) { loan ->
            LoanRow(loan, people.firstOrNull { it.id == loan.personId }, outstandingBalances) { onSelect(loan) }
        }
    }
}

@Composable
private fun LoanRow(loan: Loan, person: LoanPerson?, outstandingBalances: Map<Int, Long>, onClick: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
    val shownAmount = if (loan.settled) loan.amountMinorUnits else remainingFor(loan, outstandingBalances)
    PremiumCard(
        onClick = onClick,
        modifier = Modifier.alpha(if (loan.settled) 0.62f else 1f),
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (person != null) PersonAvatar(person, 44.dp) else Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .13f)),
                contentAlignment = Alignment.Center
            ) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(loan.person, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (loan.settled) "Settled" else if (loan.lent) "Owes you" else "You owe"} · ${if (loan.settled) "" else "outstanding · "}${dayText(loan.epochDay)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (loan.note.isNotBlank()) {
                    Text(loan.note, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text((if (loan.lent) "+" else "−") + money(shownAmount), color = color, fontWeight = FontWeight.Bold)
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
private fun AccountBalanceSheet(
    initialBalanceMinorUnits: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var amount by remember(initialBalanceMinorUnits) {
        mutableStateOf(initialBalanceMinorUnits?.let(::minorUnitsToInput).orEmpty())
    }
    val parsedAmount = parseMinorUnits(amount, allowZero = true)
    MomentumDialog(
        onDismissRequest = onDismiss,
        title = { Text("In my account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your current bank or cash balance. This is tracked separately from money to receive or repay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MomentumTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    label = { Text("Current balance") },
                    prefix = { Text(CURRENCY) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedAmount != null,
                onClick = { parsedAmount?.let(onSave) }
            ) { Text("Save balance") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LoanMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    GlassCard(
        modifier = modifier,
        level = GlassLevel.Low,
        shape = RoundedCornerShape(MomentumTokens.radiusSmall + 4.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoanDetailSheet(
    loan: Loan,
    outstandingMinorUnits: Long,
    payments: List<LoanPayment>,
    personProfile: LoanPerson?,
    attachments: List<LoanAttachment>,
    onDismiss: () -> Unit,
    onSettle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttach: (Int, String) -> Unit,
    onAddPayment: (Long, String) -> Unit,
    onDeletePayment: (LoanPayment) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previewAvatar by remember(loan.id) { mutableStateOf(false) }
    var paymentAmount by remember(loan.id) { mutableStateOf("") }
    var paymentNote by remember(loan.id) { mutableStateOf("") }
    val parsedPayment = parseMinorUnits(paymentAmount)
    val paidMinorUnits = remember(payments) { payments.sumMinorUnits { it.amountMinorUnits } }
    val color = if (loan.lent) LentColor else BorrowedColor
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onAttach(loan.id, it.toString())
        }
    }
    FormSheet(loan.person, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (personProfile != null) PersonAvatar(personProfile, 54.dp) { if (personProfile.profileImageUri.isNotBlank()) previewAvatar = true } else Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text(loan.person.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (loan.lent) "Outstanding from them" else "Outstanding you owe", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(money(outstandingMinorUnits), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = (-0.4).sp)
                Text("Principal ${money(loan.amountMinorUnits)}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(if (loan.settled) "Settled" else "Open", if (loan.settled) Positive else color)
        }
        if (previewAvatar && personProfile?.profileImageUri?.isNotBlank() == true) {
            ImagePreviewDialog(personProfile.profileImageUri, "${personProfile.name} profile photo") { previewAvatar = false }
        }
        SectionHeader("Details")
        LoanDetailLine("Recorded", dayText(loan.epochDay))
        LoanDetailLine("Status", if (loan.settled) "Completed" else loan.status.replace('_', ' ').replaceFirstChar { it.uppercase() })
        if (loan.title.isNotBlank()) LoanDetailLine("Purpose", loan.title)
        loan.dueEpochDay?.let { LoanDetailLine("Deadline", dayText(it)) }
        if (loan.note.isNotBlank()) LoanDetailLine("Note", loan.note)
        LoanDetailLine("Paid so far", money(paidMinorUnits))
        SectionHeader("Payments", subtitle = if (payments.isEmpty()) "No payments recorded" else "${payments.size} payment${if (payments.size == 1) "" else "s"} recorded")
        payments.forEach { payment ->
            PremiumCard(contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${money(payment.amountMinorUnits)} · ${dayText(payment.epochDay)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (payment.note.isNotBlank()) {
                            Text(payment.note, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onDeletePayment(payment) }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete payment", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        MomentumTextField(
            value = paymentAmount,
            onValueChange = { paymentAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
            label = { Text("Payment amount") },
            prefix = { Text(CURRENCY) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        MomentumTextField(value = paymentNote, onValueChange = { paymentNote = it }, label = { Text("Payment note (optional)") }, singleLine = true)
        Text(
            "Payments reduce the outstanding balance. Extra payments are kept in history but cannot make the balance negative.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(
            "Add payment",
            enabled = parsedPayment != null,
            icon = Icons.Rounded.Add,
            onClick = {
                parsedPayment?.let { amount ->
                    onAddPayment(amount, paymentNote.trim())
                    paymentAmount = ""
                    paymentNote = ""
                }
            }
        )
        SectionHeader("Loan proof", subtitle = if (attachments.isEmpty()) "No proof files attached" else "${attachments.size} file${if (attachments.size == 1) "" else "s"}")
        attachments.forEach { attachment ->
            SecondaryButton("Preview · ${attachment.name}", onClick = { openProofFile(context, attachment.uri, attachment.mime) }, icon = Icons.Rounded.Visibility)
        }
        SecondaryButton("Attach proof file", onClick = { picker.launch(arrayOf("*/*")) }, icon = Icons.Rounded.AttachFile)
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
    val glass = LocalGlassPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MomentumTokens.radiusSmall))
            .background(glass.chip)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.End)
    }
}

@Composable
private fun LoanEditorSheet(
    initial: Loan?,
    people: List<LoanPerson>,
    onDismiss: () -> Unit,
    presetPerson: LoanPerson? = null,
    onSave: (Int?, String, Long, Boolean, String, String, Long?, String, List<String>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var person by remember(initial, presetPerson) { mutableStateOf(initial?.person ?: presetPerson?.name.orEmpty()) }
    var selectedPersonId by remember(initial, presetPerson) { mutableIntStateOf(initial?.personId ?: presetPerson?.id ?: 0) }
    var creatingPerson by remember(initial, presetPerson) { mutableStateOf(initial == null && presetPerson == null) }
    var peopleExpanded by remember { mutableStateOf(false) }
    var amount by remember(initial) {
        mutableStateOf(initial?.amountMinorUnits?.let(::minorUnitsToInput) ?: "")
    }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var lent by remember(initial) { mutableStateOf(initial?.lent ?: true) }
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var dueText by remember(initial) { mutableStateOf(initial?.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var status by remember(initial) { mutableStateOf(initial?.status ?: "pending") }
    val proofUris = remember(initial) { mutableStateListOf<String>() }
    var previewAvatar by remember { mutableStateOf(false) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (uri.toString() !in proofUris) proofUris.add(uri.toString())
        }
    }
    val selectedPerson = if (presetPerson != null) people.firstOrNull { it.id == presetPerson.id } ?: presetPerson else if (creatingPerson) null else (
        people.firstOrNull { it.id == selectedPersonId }
            ?: people.firstOrNull { initial != null && it.name.equals(initial.person, ignoreCase = true) }
        )
    FormSheet(if (initial == null) "Add loan record" else "Edit loan record", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumChip(selected = lent, onClick = { lent = true }, label = "I lent", icon = Icons.Rounded.ArrowUpward)
            MomentumChip(selected = !lent, onClick = { lent = false }, label = "I borrowed", icon = Icons.Rounded.ArrowDownward)
        }
        if (presetPerson != null) {
            PremiumCard(contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(selectedPerson ?: presetPerson, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Person", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedPerson?.name ?: presetPerson.name, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
        Text("Person", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { peopleExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    selectedPerson?.let { PersonAvatar(it, 34.dp) { previewAvatar = true }; Spacer(Modifier.width(10.dp)) }
                    Text(selectedPerson?.name ?: person.ifBlank { "Choose a person or add new" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose person")
                }
            }
            DropdownMenu(expanded = peopleExpanded, onDismissRequest = { peopleExpanded = false }) {
                people.forEach { option ->
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { PersonAvatar(option, 32.dp); Spacer(Modifier.width(10.dp)); Text(option.name) } },
                        onClick = { selectedPersonId = option.id; creatingPerson = false; person = option.name; peopleExpanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ Add a new person") },
                    onClick = { selectedPersonId = 0; creatingPerson = true; person = ""; peopleExpanded = false }
                )
            }
        }
        if (previewAvatar && selectedPerson?.profileImageUri?.isNotBlank() == true) {
            ImagePreviewDialog(selectedPerson.profileImageUri, "${selectedPerson.name} profile photo") { previewAvatar = false }
        }
        if (selectedPerson == null) {
            MomentumTextField(value = person, onValueChange = { person = it }, label = { Text("New person name") }, singleLine = true)
            Text("Saving this loan will add this person to your people list, so you can reuse the profile for future loans.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        MomentumTextField(value = title, onValueChange = { title = it }, label = { Text("Purpose / title") }, singleLine = true)
        MomentumTextField(
            value = amount,
            onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
            label = { Text("Amount") },
            prefix = { Text(CURRENCY) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        MomentumTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
        SectionHeader("Loan proof", subtitle = "Attach any document, image, or other file")
        SecondaryButton("Choose proof files", onClick = { proofPicker.launch(arrayOf("*/*")) }, icon = Icons.Rounded.AttachFile)
        proofUris.forEach { uri ->
            val name = Uri.parse(uri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { null } ?: "Loan proof"
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openProofFile(context, uri, context.contentResolver.getType(Uri.parse(uri)).orEmpty()) }, modifier = Modifier.weight(1f)) {
                    Text("Preview · $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { proofUris.remove(uri) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove $name") }
            }
        }
        MomentumTextField(
            value = dueText,
            onValueChange = { dueText = it.take(10) },
            label = { Text("Deadline (YYYY-MM-DD, optional)") },
            singleLine = true
        )
        Text("Status", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("planned", "pending", "partially_paid", "completed", "overdue").forEach { value ->
                MomentumChip(selected = status == value, onClick = { status = value }, label = value.replace('_', ' ').replaceFirstChar { it.uppercase() })
            }
        }
        PrimaryButton(if (initial == null) "Save record" else "Save changes", onClick = {
            val due = dueText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
            val personName = selectedPerson?.name ?: person.trim()
            val amountMinorUnits = parseMinorUnits(amount)
            if (personName.isNotBlank() && amountMinorUnits != null) {
                onSave(selectedPerson?.id, personName, amountMinorUnits, lent, note.trim(), title.trim(), due, status, proofUris.toList())
            }
        })
    }
}
