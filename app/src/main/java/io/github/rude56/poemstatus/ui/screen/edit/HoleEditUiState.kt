package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.runtime.Immutable

@Immutable
data class HoleEditUiState(
    val slot: Int,
    val left: String,
    val right: String,
    val wormhole: Boolean,
    val error: String?,
    val screenWidth: Int,
    val isNew: Boolean = false,
)

@Immutable
data class HoleEditActions(
    val onLeftChange: (String) -> Unit,
    val onRightChange: (String) -> Unit,
    val onModeChange: (wormhole: Boolean) -> Unit,
    val onCancel: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)
