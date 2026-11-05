@file:Suppress("FunctionNaming")

package com.faheem.habits.features.loans
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalDate

class LoanViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).loanDao()
    val loans: StateFlow<List<Loan>> = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val people: StateFlow<List<LoanPerson>> = dao.getPeople().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addPerson(person: LoanPerson) { viewModelScope.launch { dao.insertPerson(person.copy(name = person.name.trim())) } }
    fun updatePerson(person: LoanPerson) { viewModelScope.launch { dao.updatePerson(person.copy(name = person.name.trim(), updatedAt = System.currentTimeMillis(), dirty = true)) } }
    fun archivePerson(person: LoanPerson) { viewModelScope.launch { dao.archivePerson(person.id) } }
    fun deletePerson(person: LoanPerson) { viewModelScope.launch { dao.deletePerson(person.id) } }
    fun add(person: String, personId: Int?, amount: Double, lent: Boolean, note: String, title: String = "", dueEpochDay: Long? = null, status: String = "pending", proofUris: List<String> = emptyList()) {
        viewModelScope.launch {
            val normalized = person.trim()
            val resolvedPersonId = resolvePersonId(normalized, personId)
            val loanId = dao.insert(Loan(person = normalized, personId = resolvedPersonId, amountMinorUnits = amount.toMinorUnits(), lent = lent, note = note.trim(), title = title.trim(), dueEpochDay = dueEpochDay, status = status, epochDay = today())).toInt()
            proofUris.forEach { insertAttachment(loanId, it) }
        }
    }
    fun updateLoan(loan: Loan, person: String, personId: Int?, proofUris: List<String> = emptyList()) {
        viewModelScope.launch {
            val normalized = person.trim()
            val resolvedPersonId = resolvePersonId(normalized, personId)
            dao.update(loan.copy(person = normalized, personId = resolvedPersonId))
            proofUris.forEach { insertAttachment(loan.id, it) }
        }
    }
    private suspend fun resolvePersonId(name: String, requestedId: Int?): Int {
        val selectedPerson = requestedId?.let { dao.getPerson(it) }
        return selectedPerson?.id
            ?: dao.getPeople().first().firstOrNull { it.name.equals(name, true) }?.id
            ?: dao.insertPerson(LoanPerson(name = name)).toInt()
    }
    fun update(loan: Loan) { viewModelScope.launch { dao.update(loan) } }
    fun addAttachment(loanId: Int, uri: String) { viewModelScope.launch { insertAttachment(loanId, uri) } }
    private suspend fun insertAttachment(loanId: Int, uri: String) {
        val parsedUri = Uri.parse(uri)
        dao.insertAttachment(
            LoanAttachment(
                loanId = loanId,
                name = parsedUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { null } ?: "Loan proof",
                uri = uri,
                mime = getApplication<Application>().contentResolver.getType(parsedUri).orEmpty()
            )
        )
    }
    fun attachments(loanId: Int) = dao.getAttachments(loanId)
    fun toggleSettled(loan: Loan) { update(loan.copy(settled = !loan.settled)) }
    fun remove(loan: Loan) { viewModelScope.launch { dao.delete(loan) } }
}

private val LentColor = Positive
private val BorrowedColor = AccentAmber

@Composable
private fun PersonAvatar(person: LoanPerson, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, person.profileImageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(person.profileImageUri))?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
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
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
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
fun LoansScreen(vm: LoanViewModel = viewModel()) {
    val loans by vm.loans.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var showPersonEditor by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<LoanPerson?>(null) }
    var selected by remember { mutableStateOf<Loan?>(null) }
    var editing by remember { mutableStateOf<Loan?>(null) }
    var deleting by remember { mutableStateOf<Loan?>(null) }
    var deletingPerson by remember { mutableStateOf<LoanPerson?>(null) }

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

    val people by vm.people.collectAsState()
    if (showAdd) LoanEditorSheet(null, people, { showAdd = false }) { personId, person, amount, lent, note, title, due, status, proofs -> vm.add(person, personId, amount, lent, note, title, due, status, proofs); showAdd = false }
    if (showPersonEditor) {
        PersonEditorSheet(editingPerson, onDismiss = { showPersonEditor = false; editingPerson = null }) { person ->
            if (editingPerson == null) vm.addPerson(person) else vm.updatePerson(person)
            showPersonEditor = false
            editingPerson = null
        }
    }
    editing?.let { loan -> LoanEditorSheet(loan, people, { editing = null }) { personId, person, amount, lent, note, title, due, status, proofs ->
        vm.updateLoan(loan.copy(amountMinorUnits = amount.toMinorUnits(), lent = lent, note = note, title = title, dueEpochDay = due, status = status), person, personId, proofs)
        editing = null
        selected = null
    } }
    selected?.let { loan ->
        val attachments by vm.attachments(loan.id).collectAsState(initial = emptyList())
        LoanDetailSheet(
            loan = loan,
            personProfile = people.firstOrNull { it.id == loan.personId },
            attachments = attachments,
            onDismiss = { selected = null },
            onSettle = { vm.toggleSettled(loan); selected = null },
            onEdit = { editing = loan; selected = null },
            onDelete = { deleting = loan; selected = null },
            onAttach = vm::addAttachment
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
                0 -> LoanOverview(openLoans, people, settledLoans.size, toReceive, toPay, net, onOpenLedger = { tab = 1 }, onSelect = { selected = it })
                3 -> PeopleScreen(people = people, loans = loans, onAdd = { showPersonEditor = true }, onEdit = { editingPerson = it; showPersonEditor = true }, onDelete = { deletingPerson = it })
                else -> LoanLedger(
                    loans = filtered,
                    people = people,
                    query = query,
                    onQuery = { query = it },
                    direction = direction,
                    onDirection = { direction = it },
                    settled = tab == 2,
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
        onAdd: () -> Unit,
        onEdit: (LoanPerson) -> Unit,
        onDelete: (LoanPerson) -> Unit
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("People", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
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
                PremiumCard(onClick = { onEdit(person) }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(person, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, fontWeight = FontWeight.SemiBold)
                            Text(person.phone.ifBlank { person.address.ifBlank { "No contact details" } }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val related = loans.filter { loan ->
                                loan.personId == person.id || (loan.personId == null && loan.person.equals(person.name, ignoreCase = true))
                            }
                            if (related.isNotEmpty()) {
                                val openCount = related.count { !it.settled }
                                val outstanding = related.filterNot { it.settled }.sumOf { it.amountMinorUnits }
                                Text(
                                    "${related.size} loan${if (related.size == 1) "" else "s"} · $openCount open · ${money(outstanding)} outstanding",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(person) }) { Icon(Icons.Rounded.DeleteOutline, "Delete person", tint = MaterialTheme.colorScheme.error) }
                    }
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
    openLoans: List<Loan>, people: List<LoanPerson>, settledCount: Int, toReceive: Long, toPay: Long, net: Long,
    onOpenLedger: () -> Unit, onSelect: (Loan) -> Unit
) {
    val peopleCount = openLoans.map { it.person.trim().lowercase() }.distinct().size
    val recent = openLoans.take(4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        items(recent, key = { it.id }) { loan -> LoanRow(loan, people.firstOrNull { it.id == loan.personId }) { onSelect(loan) } }
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
    loans: List<Loan>, people: List<LoanPerson>, query: String, onQuery: (String) -> Unit, direction: Int, onDirection: (Int) -> Unit,
    settled: Boolean, onSelect: (Loan) -> Unit, onEmptyAdd: () -> Unit
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Lent", "Borrowed").forEachIndexed { index, label ->
                    MomentumChip(selected = direction == index, onClick = { onDirection(index) }, label = label)
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
        items(loans, key = { it.id }) { loan -> LoanRow(loan, people.firstOrNull { it.id == loan.personId }) { onSelect(loan) } }
    }
}

@Composable
private fun LoanRow(loan: Loan, person: LoanPerson?, onClick: () -> Unit) {
    val color = if (loan.lent) LentColor else BorrowedColor
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
    personProfile: LoanPerson?,
    attachments: List<LoanAttachment>,
    onDismiss: () -> Unit,
    onSettle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttach: (Int, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previewAvatar by remember(loan.id) { mutableStateOf(false) }
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
                Text(if (loan.lent) "They owe you" else "You owe them", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(money(loan.amountMinorUnits), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = (-0.4).sp)
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun LoanEditorSheet(
    initial: Loan?,
    people: List<LoanPerson>,
    onDismiss: () -> Unit,
    onSave: (Int?, String, Double, Boolean, String, String, Long?, String, List<String>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var person by remember(initial) { mutableStateOf(initial?.person ?: "") }
    var selectedPersonId by remember(initial) { mutableIntStateOf(initial?.personId ?: 0) }
    var creatingPerson by remember(initial) { mutableStateOf(initial == null) }
    var peopleExpanded by remember { mutableStateOf(false) }
    var amount by remember(initial) { mutableStateOf(initial?.amountMinorUnits?.toString() ?: "") }
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
    val selectedPerson = if (creatingPerson) null else (
        people.firstOrNull { it.id == selectedPersonId }
            ?: people.firstOrNull { initial != null && it.name.equals(initial.person, ignoreCase = true) }
        )
    FormSheet(if (initial == null) "Add loan record" else "Edit loan record", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumChip(selected = lent, onClick = { lent = true }, label = "I lent", icon = Icons.Rounded.ArrowUpward)
            MomentumChip(selected = !lent, onClick = { lent = false }, label = "I borrowed", icon = Icons.Rounded.ArrowDownward)
        }
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
            amount.toDoubleOrNull()?.takeIf { personName.isNotBlank() && it > 0 }?.let {
                onSave(selectedPerson?.id, personName, it, lent, note.trim(), title.trim(), due, status, proofUris.toList())
            }
        })
    }
}
