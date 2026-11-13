@file:Suppress("FunctionNaming")

package com.faheem.habits.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.faheem.habits.core.ui.*
import com.faheem.habits.features.battery.BatteryScreen
import com.faheem.habits.features.files.FilesScreen
import com.faheem.habits.features.contacts.ContactsScreen
import com.faheem.habits.features.habits.HabitScreen
import com.faheem.habits.features.home.HomeScreen
import com.faheem.habits.features.loans.LoansScreen
import com.faheem.habits.features.liferules.LifeRulesScreen
import com.faheem.habits.features.money.ExpensesScreen
import com.faheem.habits.features.network.NetworkScreen
import com.faheem.habits.features.screen_time.ScreenTimeScreen
import com.faheem.habits.features.settings.AppSettingsScreen
import com.faheem.habits.features.supabase.SupabaseScreen
import com.faheem.habits.features.todos.ToDoScreen
import com.faheem.habits.features.vault.VaultScreen

sealed interface MomentumDestination {
    data object Home : MomentumDestination
    data class Module(
        val module: AppModule,
        val itemId: Int? = null,
        val requestToken: Long? = null
    ) : MomentumDestination
}

@Composable
fun MomentumNavigationHost(
    destination: MomentumDestination,
    onOpenModule: (AppModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    LaunchedEffect(destination) {
        if (!matchesDestination(destination, navController.currentBackStackEntry)) {
            navController.navigate(destination.route()) {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    NavHost(navController, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(onOpen = onOpenModule) }
        composable(
            route = "module/{module}?itemId={itemId}",
            arguments = listOf(
                navArgument("module") { type = NavType.StringType },
                navArgument("itemId") {
                    type = NavType.IntType
                    nullable = true
                    defaultValue = null
                },
                navArgument("requestToken") {
                    type = NavType.LongType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "momentum://module/{module}" },
                navDeepLink { uriPattern = "momentum://module/{module}?itemId={itemId}" },
                navDeepLink { uriPattern = "momentum://module/{module}?itemId={itemId}&requestToken={requestToken}" }
            )
        ) { entry ->
            val module = entry.arguments?.getString("module")
                ?.let { name -> AppModule.entries.firstOrNull { it.name.equals(name, true) } }
            if (module == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ModuleScreen(
                    module,
                    entry.arguments?.getInt("itemId", Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE },
                    entry.arguments?.getLong("requestToken", Long.MIN_VALUE)
                        ?.takeUnless { it == Long.MIN_VALUE },
                    onBack
                )
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun ModuleScreen(module: AppModule, itemId: Int?, requestToken: Long?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val accent = module.accent
    CompositionLocalProvider(LocalGradient provides module.gradient) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent,
                onPrimary = if (accent.luminance() > 0.179f) Color(0xFF101827) else Color.White,
                primaryContainer = accent.copy(alpha = 0.14f),
                onPrimaryContainer = MaterialTheme.colorScheme.onSurface,
                secondaryContainer = accent.copy(alpha = 0.12f),
                onSecondaryContainer = MaterialTheme.colorScheme.onSurface
            )
        ) {
            ScreenBackground(tint = accent) {
                Column(Modifier.fillMaxSize()) {
                    ModuleHeader(
                        title = module.label,
                        eyebrow = "Zylo",
                        icon = module.icon,
                        gradient = module.gradient,
                        onBack = onBack
                    )
                    Box(Modifier.weight(1f)) {
                        when (module) {
                            AppModule.Habits -> HabitScreen(initialHabitId = itemId, openRequestToken = requestToken)
                            AppModule.ToDos -> ToDoScreen(initialTaskId = itemId, openRequestToken = requestToken)
                            AppModule.Money -> ExpensesScreen()
                            AppModule.Loans -> LoansScreen(initialLoanId = itemId, openRequestToken = requestToken)
                            AppModule.Contacts -> ContactsScreen()
                            AppModule.Files -> FilesScreen()
                            AppModule.LifeRules -> LifeRulesScreen()
                            AppModule.Screen -> ScreenTimeScreen()
                            AppModule.Vault -> VaultScreen()
                            AppModule.Battery -> BatteryScreen()
                            AppModule.Network -> NetworkScreen()
                            AppModule.Supabase -> SupabaseScreen()
                            AppModule.Settings -> AppSettingsScreen()
                        }
                    }

                    private fun MomentumDestination.route(): String = when (this) {
                        MomentumDestination.Home -> "home"
                        is MomentumDestination.Module -> buildString {
                            append("module/${module.name.lowercase()}")
                            itemId?.let { append("?itemId=$it") }
                            requestToken?.let {
                                append(if (itemId == null) "?" else "&")
                                append("requestToken=$it")
                            }
                        }
                    }

                    private fun matchesDestination(
                        destination: MomentumDestination,
                        currentEntry: NavBackStackEntry?
                    ): Boolean = when (destination) {
                        MomentumDestination.Home -> currentEntry?.destination?.route == "home"
                        is MomentumDestination.Module -> {
                            val args = currentEntry?.arguments
                            val currentItemId = args?.getInt("itemId", Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }
                            val currentRequestToken = args?.getLong("requestToken", Long.MIN_VALUE)
                                ?.takeUnless { it == Long.MIN_VALUE }
                            currentEntry?.destination?.route?.startsWith("module/") == true &&
                                args?.getString("module").equals(destination.module.name, ignoreCase = true) &&
                                currentItemId == destination.itemId &&
                                currentRequestToken == destination.requestToken
                        }
                    }
                }
            }
        }
    }
}
