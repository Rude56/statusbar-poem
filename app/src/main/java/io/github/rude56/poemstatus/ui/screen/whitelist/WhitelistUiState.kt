package io.github.rude56.poemstatus.ui.screen.whitelist

import androidx.compose.runtime.Immutable
import io.github.rude56.poemstatus.AppState

@Immutable
data class WhitelistUiState(
    val apps: List<AppState.AppEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = false,
    val hasApps: Boolean = false,
) {
    val noMatch: Boolean get() = hasApps && apps.isEmpty()
}

@Immutable
data class WhitelistActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onToggle: (String, Boolean) -> Unit,
)
