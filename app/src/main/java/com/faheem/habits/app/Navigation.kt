@file:Suppress("FunctionNaming")

package com.faheem.habits.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.faheem.habits.features.profile.ProfileScreen
import com.faheem.habits.features.loans.LoansScreen
import com.faheem.habits.features.liferules.LifeRulesScreen
import com.faheem.habits.features.language.LanguageScreen
import com.faheem.habits.features.money.ExpensesScreen
import com.faheem.habits.features.network.NetworkScreen
import com.faheem.habits.features.screen_time.ScreenTimeScreen
import com.faheem.habits.features.search.GlobalSearchScreen
import com.faheem.habits.features.settings.AppSettingsScreen
import com.faheem.habits.features.supabase.SupabaseScreen
import com.faheem.habits.features.todos.ToDoScreen
import com.faheem.habits.features.vault.VaultScreen

sealed interface MomentumDestination {
    data object Home : MomentumDestination
    data object Search : MomentumDestination
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
    modifier: Modifier = Modifier,
    isModuleVisible: (AppModule) -> Boolean = { true },
    entrySource: ModuleEntrySource = ModuleEntrySource.NORMAL,
    cloudSignedIn: Boolean = true,
    requiresAccountAttention: Boolean = false,
    offlineNotice: Boolean = false,
    profileInitialCreateAccount: Boolean = false,
    onOpenPermissions: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSearchResult: (AppModule, Int) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    LaunchedEffect(destination) {
        if (!matchesDestination(destination, navController.currentBackStackEntry)) {
            navController.navigate(destination.route()) {
                popUpTo(MomentumRoutes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    NavHost(navController, startDestination = MomentumRoutes.HOME, modifier = modifier) {
        composable(MomentumRoutes.HOME) {
            HomeScreen(
                cloudSignedIn = cloudSignedIn,
                onOpenPermissions = onOpenPermissions,
                onOpenSearch = onOpenSearch,
                onOpen = { module ->
                    if (isModuleEntryAllowed(module, isModuleVisible, ModuleEntrySource.NORMAL)) {
                        onOpenModule(module)
                    }
                }
            )
        }
        composable(MomentumRoutes.SEARCH) {
            GlobalSearchScreen(
                onBack = onBack,
                onOpenResult = { module, itemId ->
                    if (isModuleEntryAllowed(module, isModuleVisible, ModuleEntrySource.NORMAL)) {
                        onOpenSearchResult(module, itemId)
                    }
                }
            )
        }
        composable(
            route = MomentumRoutes.MODULE_PATTERN,
            arguments = listOf(
                navArgument(MomentumRoutes.ARG_MODULE) { type = NavType.StringType },
                navArgument(MomentumRoutes.ARG_ITEM_ID) {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument(MomentumRoutes.ARG_REQUEST_TOKEN) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${MomentumRoutes.DEEP_LINK_SCHEME}://${MomentumRoutes.DEEP_LINK_MODULE_HOST}/{${MomentumRoutes.ARG_MODULE}}"
                },
                navDeepLink {
                    uriPattern = "${MomentumRoutes.DEEP_LINK_SCHEME}://${MomentumRoutes.DEEP_LINK_MODULE_HOST}/{${MomentumRoutes.ARG_MODULE}}?${MomentumRoutes.ARG_ITEM_ID}={${MomentumRoutes.ARG_ITEM_ID}}"
                },
                navDeepLink {
                    uriPattern = "${MomentumRoutes.DEEP_LINK_SCHEME}://${MomentumRoutes.DEEP_LINK_MODULE_HOST}/{${MomentumRoutes.ARG_MODULE}}?${MomentumRoutes.ARG_ITEM_ID}={${MomentumRoutes.ARG_ITEM_ID}}&${MomentumRoutes.ARG_REQUEST_TOKEN}={${MomentumRoutes.ARG_REQUEST_TOKEN}}"
                }
            )
        ) { entry ->
            val module = MomentumRoutes.parseModule(entry.arguments?.getString(MomentumRoutes.ARG_MODULE))
            val allowed = module != null && isModuleEntryAllowed(
                module = module,
                isVisible = isModuleVisible,
                source = entrySource
            )
            if (!allowed) {
                LaunchedEffect(Unit) {
                    navController.navigate(MomentumRoutes.HOME) {
                        popUpTo(MomentumRoutes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                ModuleScreen(
                    module!!,
                    entry.arguments?.getInt(MomentumRoutes.ARG_ITEM_ID, -1)?.takeIf { it > 0 },
                    entry.arguments?.getLong(MomentumRoutes.ARG_REQUEST_TOKEN, 0L)?.takeIf { it > 0L },
                    onOpenModule,
                    onBack,
                    cloudSignedIn = cloudSignedIn,
                    requiresAccountAttention = requiresAccountAttention,
                    offlineNotice = offlineNotice,
                    profileInitialCreateAccount = profileInitialCreateAccount,
                    onOpenSearch = onOpenSearch
                )
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun ModuleScreen(
    module: AppModule,
    itemId: Int?,
    requestToken: Long?,
    onOpenModule: (AppModule) -> Unit,
    onBack: () -> Unit,
    cloudSignedIn: Boolean = true,
    requiresAccountAttention: Boolean = false,
    offlineNotice: Boolean = false,
    profileInitialCreateAccount: Boolean = false,
    onOpenSearch: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val accent = module.accent
    val baseColorScheme = MaterialTheme.colorScheme
    val moduleColorScheme = remember(accent, baseColorScheme) {
        baseColorScheme.copy(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.179f) Color(0xFF101827) else Color.White,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = baseColorScheme.onSurface,
            secondaryContainer = accent.copy(alpha = 0.12f),
            onSecondaryContainer = baseColorScheme.onSurface
        )
    }
    CompositionLocalProvider(LocalGradient provides module.gradient) {
        MaterialTheme(colorScheme = moduleColorScheme) {
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
                            AppModule.Language -> LanguageScreen()
                            AppModule.Supabase -> SupabaseScreen(onOpenProfile = { onOpenModule(AppModule.Profile) })
                            AppModule.Profile -> ProfileScreen(
                                onContinueWithoutAccount = onBack,
                                passwordResetRequestToken = requestToken,
                                initialCreateAccount = profileInitialCreateAccount
                            )
                            AppModule.Settings -> AppSettingsScreen(
                                onOpenModule = onOpenModule,
                                onOpenSearch = onOpenSearch,
                                cloudSignedIn = cloudSignedIn,
                                requiresAccountAttention = requiresAccountAttention,
                                offlineNotice = offlineNotice
                            )
                        }
                    }

                }
            }
        }
    }
}

private fun MomentumDestination.route(): String = when (this) {
    MomentumDestination.Home -> MomentumRoutes.HOME
    MomentumDestination.Search -> MomentumRoutes.SEARCH
    is MomentumDestination.Module -> MomentumRoutes.module(module, itemId, requestToken)
}

private fun matchesDestination(
    destination: MomentumDestination,
    currentEntry: NavBackStackEntry?
): Boolean = when (destination) {
    MomentumDestination.Home -> currentEntry?.destination?.route == MomentumRoutes.HOME
    MomentumDestination.Search -> currentEntry?.destination?.route == MomentumRoutes.SEARCH
    is MomentumDestination.Module -> {
        val args = currentEntry?.arguments
        val currentItemId = args?.getInt(MomentumRoutes.ARG_ITEM_ID, -1)?.takeIf { it > 0 }
        val currentRequestToken = args?.getLong(MomentumRoutes.ARG_REQUEST_TOKEN, 0L)?.takeIf { it > 0L }
        currentEntry?.destination?.route?.startsWith(MomentumRoutes.MODULE_PREFIX) == true &&
            args?.getString(MomentumRoutes.ARG_MODULE).equals(destination.module.name, ignoreCase = true) &&
            currentItemId == destination.itemId &&
            currentRequestToken == destination.requestToken
    }
}
