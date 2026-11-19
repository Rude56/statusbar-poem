package io.github.rude56.poemstatus

import androidx.compose.runtime.Immutable
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.theme.AppSettings

@Immutable
data class UiPrefsState(
    val appSettings: AppSettings,
    val uiMode: UiMode,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enableSwipeDismiss: Boolean,
    val pageScale: Float,
)
