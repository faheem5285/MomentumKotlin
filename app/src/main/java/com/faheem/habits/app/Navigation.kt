package com.faheem.habits.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.faheem.habits.core.ui.*
import com.faheem.habits.features.battery.BatteryScreen
import com.faheem.habits.features.files.FilesScreen
import com.faheem.habits.features.habits.HabitScreen
import com.faheem.habits.features.home.HomeScreen
import com.faheem.habits.features.loans.LoansScreen
import com.faheem.habits.features.money.ExpensesScreen
import com.faheem.habits.features.network.NetworkScreen
import com.faheem.habits.features.screen_time.ScreenTimeScreen
import com.faheem.habits.features.settings.AppSettingsScreen
import com.faheem.habits.features.todos.ToDoScreen
import com.faheem.habits.features.vault.VaultScreen

sealed interface MomentumDestination {
    data object Home : MomentumDestination
    data class Module(val module: AppModule) : MomentumDestination
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
        val route = when (destination) {
            MomentumDestination.Home -> "home"
            is MomentumDestination.Module -> "module/${destination.module.name.lowercase()}"
        }
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    NavHost(navController, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(onOpen = onOpenModule) }
        composable(
            route = "module/{module}",
            arguments = listOf(navArgument("module") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "momentum://module/{module}" })
        ) { entry ->
            val module = entry.arguments?.getString("module")
                ?.let { name -> AppModule.entries.firstOrNull { it.name.equals(name, true) } }
            if (module == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ModuleScreen(module, onBack)
            }
        }
    }
}

@Composable
private fun ModuleScreen(module: AppModule, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val accent = module.gradient.first()
    CompositionLocalProvider(LocalGradient provides module.gradient) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.18f),
                onPrimaryContainer = accent,
                secondaryContainer = accent.copy(alpha = 0.18f),
                onSecondaryContainer = accent
            )
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    IconBubble(module.icon, accent, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(module.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f)) {
                    when (module) {
                        AppModule.Habits -> HabitScreen()
                        AppModule.ToDos -> ToDoScreen()
                        AppModule.Money -> ExpensesScreen()
                        AppModule.Loans -> LoansScreen()
                        AppModule.Files -> FilesScreen()
                        AppModule.Screen -> ScreenTimeScreen()
                        AppModule.Vault -> VaultScreen()
                        AppModule.Battery -> BatteryScreen()
                        AppModule.Network -> NetworkScreen()
                        AppModule.Settings -> AppSettingsScreen()
                    }
                }
            }
        }
    }
}
