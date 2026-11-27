package com.faheem.habits.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.BrandGradient
import com.faheem.habits.core.ui.InfoBanner
import com.faheem.habits.core.ui.ListErrorState
import com.faheem.habits.core.ui.ListLoadingState
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PremiumTopBar
import com.faheem.habits.core.ui.ScreenBackground
import com.faheem.habits.core.ui.SettingsRow

@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpenResult: (AppModule, Int) -> Unit,
    vm: GlobalSearchViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ScreenBackground(tint = BrandGradient.first()) {
        Column(Modifier.fillMaxSize()) {
            PremiumTopBar(title = "Search", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MomentumTokens.screenPadding, vertical = MomentumTokens.space1),
                verticalArrangement = Arrangement.spacedBy(MomentumTokens.space2)
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Search your records") },
                    placeholder = { Text("Habits, tasks, expenses, contacts, files, life rules") },
                    singleLine = true,
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().testTag("global-search-input")
                )
                Text(
                    "Only visible modules are searched. Vault is never included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.hiddenCategories.isNotEmpty() || state.permissionDenied) {
                    InfoBanner(
                        icon = Icons.Rounded.Info,
                        text = buildString {
                            if (state.hiddenCategories.isNotEmpty()) {
                                append("Matches in hidden modules are not shown. ")
                            }
                            if (state.permissionDenied) {
                                append("Some files need access to be granted again before they can be found.")
                            }
                        }.trim(),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            when (state.status) {
                GlobalSearchStatus.BLANK -> Text(
                    text = "Start typing to search across your local records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MomentumTokens.screenPadding)
                )

                GlobalSearchStatus.LOADING -> ListLoadingState()

                GlobalSearchStatus.ERROR -> ListErrorState(
                    message = state.errorMessage ?: "Search could not be completed.",
                    onRetry = vm::retry
                )

                GlobalSearchStatus.EMPTY -> Text(
                    text = "No matches for “${state.query.trim()}”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MomentumTokens.screenPadding).testTag("global-search-empty")
                )

                GlobalSearchStatus.RESULTS -> LazyColumn(
                    Modifier.fillMaxSize().testTag("global-search-results"),
                    contentPadding = PaddingValues(
                        horizontal = MomentumTokens.screenPadding,
                        vertical = MomentumTokens.space1
                    ),
                    verticalArrangement = Arrangement.spacedBy(MomentumTokens.space2)
                ) {
                    items(state.results, key = { "${it.category.name}-${it.id}" }) { result ->
                        SettingsRow(
                            title = result.title,
                            supportingText = "${result.category.label} · ${result.subtitle}",
                            icon = result.module.icon,
                            onClick = { onOpenResult(result.module, result.id) },
                            modifier = Modifier.testTag("global-search-result-${result.category.name}-${result.id}")
                        )
                    }
                }
            }
        }
    }
}
