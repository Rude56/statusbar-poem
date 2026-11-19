package io.github.rude56.poemstatus.ui.screen.holes

import androidx.compose.runtime.Immutable
import io.github.rude56.poemstatus.HoleRects

@Immutable
data class CutoutUiState(
    val holes: List<HoleRects.Hole> = emptyList(),
) {
    val isEmpty: Boolean get() = holes.isEmpty()
}

@Immutable
data class CutoutActions(
    val onBack: () -> Unit,
    val onEdit: (Int) -> Unit,
    val onAdd: () -> Unit,

    val onToggle: (HoleRects.Hole, Boolean) -> Unit,

)
