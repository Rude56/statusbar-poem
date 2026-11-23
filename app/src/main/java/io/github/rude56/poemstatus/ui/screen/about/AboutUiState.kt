package io.github.rude56.poemstatus.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val appName: String,
    val versionName: String,
    val links: List<AboutLink>,
    val debugLog: Boolean = false,
)

@Immutable
data class AboutLink(
    val title: String,
    val summary: String?,
    val url: String,
)

@Immutable
data class AboutActions(
    val onBack: () -> Unit,
    val onOpenLink: (String) -> Unit,
    val onSetDebugLog: (Boolean) -> Unit = {},
    val onExportLog: () -> Unit = {},
)
