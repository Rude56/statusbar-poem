package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.runtime.Immutable

@Immutable
data class PoemEditUiState(
    val text: String,
    val author: String,
    val source: String,
    val error: String?,
    val isNew: Boolean,
)

@Immutable
data class PoemEditActions(
    val onTextChange: (String) -> Unit,
    val onAuthorChange: (String) -> Unit,
    val onSourceChange: (String) -> Unit,
    val onCancel: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)
