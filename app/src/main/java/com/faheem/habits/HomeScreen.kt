package com.faheem.habits

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class HomeState(
    val habitsTotal: Int = 0,
    val habitsDone: Int = 0,
    val monthSpent: Double = 0.0,
    val loanNet: Double = 0.0,
    val openLoans: Int = 0,
    val fileCount: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val state: StateFlow<HomeState> = combine(
        db.habitDao().getHabits(),
        db.habitDao().getCompletions(),
        db.expenseDao().getAll(),
        db.loanDao().getAll(),
        db.fileDao().getAll()
    ) { habits, comps, exps, loans, files ->
        val day = today()
        val doneIds = comps.filter { it.epochDay == day }.map { it.habitId }.toSet()
        val month = YearMonth.now()
        val spent = exps
            .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
            .sumOf { it.amount }
        val open = loans.filter { !it.settled }
        HomeState(
            habitsTotal = habits.size,
            habitsDone = habits.count { it.id in doneIds },
            monthSpent = spent,
            loanNet = open.filter { it.lent }.sumOf { it.amount } - open.filter { !it.lent }.sumOf { it.amount },
            openLoans = open.size,
            fileCount = files.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private data class TileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val module: AppModule?   // null = locked / coming soon
)

@Composable
fun HomeScreen(onOpen: (AppModule) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    var screenText by remember { mutableStateOf("Tap to set up") }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) {
        val m = withContext(Dispatchers.IO) { todayScreenMinutes(ctx) }
        screenText = if (m == null) "Tap to set up" else "${durationText(m)} today"
    }

    val tiles = listOf(
        TileSpec(
            "Habits",
            if (s.habitsTotal == 0) "Start your first habit" else "${s.habitsDone} of ${s.habitsTotal} done today",
            AppModule.Habits.icon, AppModule.Habits.gradient, AppModule.Habits
        ),
        TileSpec(
            "Money",
            if (s.monthSpent <= 0.0) "No spending yet" else "${money(s.monthSpent)} this month",
            AppModule.Money.icon, AppModule.Money.gradient, AppModule.Money
        ),
        TileSpec(
            "Loans",
            when {
                s.openLoans == 0 -> "All clear"
                s.loanNet >= 0 -> "${money(s.loanNet)} to receive"
                else -> "${money(-s.loanNet)} to pay"
            },
            AppModule.Loans.icon, AppModule.Loans.gradient, AppModule.Loans
        ),
        TileSpec(
            "Files",
            if (s.fileCount == 0) "Your file library" else "${s.fileCount} file${if (s.fileCount == 1) "" else "s"} saved",
            AppModule.Files.icon, AppModule.Files.gradient, AppModule.Files
        ),
        TileSpec("Screen time", screenText, AppModule.Screen.icon, AppModule.Screen.gradient, AppModule.Screen),
        TileSpec("Vault", "Coming soon", Icons.Rounded.Lock, listOf(Color(0xFF9CA3AF), Color(0xFF6B7280)), null)
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("Momentum", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (s.habitsTotal == 0) "Open Habits to begin"
                            else if (s.habitsDone == s.habitsTotal) "All habits done today 🎉"
                            else "${s.habitsTotal - s.habitsDone} habits left today",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    ProgressRing(
                        if (s.habitsTotal == 0) 0f else s.habitsDone.toFloat() / s.habitsTotal,
                        "${s.habitsDone}/${s.habitsTotal}"
                    )
                }
            }
        }
        item { SectionLabel("Your modules") }
        items(tiles.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { spec ->
                    ModuleTile(spec, Modifier.weight(1f)) { spec.module?.let(onOpen) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleTile(spec: TileSpec, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")

    Box(
        modifier
            .height(165.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(spec.gradient))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = spec.module != null,
                onClick = onClick
            )
    ) {
        Icon(
            spec.icon, null,
            Modifier.size(120.dp).align(Alignment.BottomEnd).offset(x = 28.dp, y = 28.dp),
            tint = Color.White.copy(alpha = 0.14f)
        )
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(spec.icon, null, Modifier.size(26.dp), tint = Color.White)
            }
            Column {
                Text(spec.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(spec.subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
            }
        }
    }
}
