@file:Suppress("FunctionNaming")

package com.faheem.habits.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.faheem.habits.core.settings.OnboardingCopy
import com.faheem.habits.core.settings.OnboardingProgress
import com.faheem.habits.core.settings.OnboardingSignals
import com.faheem.habits.core.settings.OnboardingTask
import com.faheem.habits.core.settings.visibleOnboardingTasks
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.GlassCard
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SectionHeader

private data class CardCopy(
    val title: String,
    val description: String,
    val primaryLabel: String
)

@Composable
fun OnboardingCards(
    progress: OnboardingProgress,
    signals: OnboardingSignals,
    onOpenModule: (AppModule) -> Unit,
    onOpenPermissions: () -> Unit,
    onDone: (OnboardingTask) -> Unit,
    onDismiss: (OnboardingTask) -> Unit,
    onSnooze: (OnboardingTask) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    val tasks = visibleOnboardingTasks(progress, signals, nowMillis)
    if (tasks.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MomentumTokens.space2)) {
        SectionHeader("Getting started")
        tasks.forEach { task ->
            val copy = task.copy()
            GlassCard(contentPadding = PaddingValues(MomentumTokens.cardPadding)) {
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    copy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(
                    text = copy.primaryLabel,
                    onClick = {
                        when (task) {
                            OnboardingTask.NOTIFICATIONS,
                            OnboardingTask.EXACT_ALARMS,
                            OnboardingTask.USAGE_ACCESS -> onOpenPermissions()
                            OnboardingTask.EXPLORE -> {
                                onDone(task)
                                onOpenModule(AppModule.Settings)
                            }
                            else -> {
                                onDone(task)
                                onOpenModule(task.module)
                            }
                        }
                    }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MomentumTokens.space2)
                ) {
                    TextButton(onClick = { onSnooze(task) }) { Text("Remind me later") }
                    TextButton(onClick = { onDismiss(task) }) { Text("Don't show again") }
                }
            }
        }
        TextButton(onClick = onFinish) { Text("Finish setup") }
    }
}

private val OnboardingTask.module: AppModule
    get() = when (this) {
        OnboardingTask.FIRST_HABIT -> AppModule.Habits
        OnboardingTask.FIRST_TASK -> AppModule.ToDos
        OnboardingTask.FIRST_EXPENSE -> AppModule.Money
        OnboardingTask.CLOUD_BACKUP -> AppModule.Profile
        else -> AppModule.Settings
    }

private fun OnboardingTask.copy(): CardCopy = when (this) {
    OnboardingTask.WELCOME -> CardCopy(
        "Welcome to Zylo",
        "Your habits, tasks, money and private notes live on this device. Sign-in is optional.",
        "Explore your dashboard"
    )
    OnboardingTask.FIRST_HABIT -> CardCopy(
        "Create your first habit",
        "Build a routine and see your streak grow. You can change it any time.",
        "Create habit"
    )
    OnboardingTask.FIRST_TASK -> CardCopy(
        "Add your first task",
        "Keep appointments and to-dos in one place, with reminders when you want them.",
        "Add task"
    )
    OnboardingTask.FIRST_EXPENSE -> CardCopy(
        "Track your first expense",
        "See where money goes this month. Records stay local unless you sign in.",
        "Add expense"
    )
    OnboardingTask.CLOUD_BACKUP -> CardCopy(
        "Protect your data with cloud backup",
        listOf(
            OnboardingCopy.LOCAL_DATA,
            OnboardingCopy.NO_CLOUD_BACKUP,
            OnboardingCopy.SYNC_UNAVAILABLE
        ).joinToString("\n"),
        "Sign in from Profile"
    )
    OnboardingTask.NOTIFICATIONS -> CardCopy(
        "Turn on notifications",
        "Reminders and ongoing network status need Android notification access. Zylo stays usable without it.",
        "Review notifications"
    )
    OnboardingTask.EXACT_ALARMS -> CardCopy(
        "Allow precise alarms",
        "Reminders can be delayed without exact alarm access. Habits and tasks still work.",
        "Review alarms"
    )
    OnboardingTask.USAGE_ACCESS -> CardCopy(
        "Allow usage access",
        "Screen Time and data summaries need usage access. Other modules stay available.",
        "Review usage access"
    )
    OnboardingTask.EXPLORE -> CardCopy(
        "Explore features",
        "Set up modules, appearance and backup from Settings whenever you like.",
        "Open Settings"
    )
}
