package io.github.rude56.poemstatus.ui.screen.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import io.github.rude56.poemstatus.PoemItem

@Immutable
data class LibraryUiState(
    val listState: LazyListState,
    val marqueeSpeedPx: Int = 120,
    val showAuthor: Boolean = true,
    val showSource: Boolean = false,

    val poems: List<PoemItem> = emptyList(),
    val visiblePoems: List<PoemItem> = emptyList(),
    val indexById: Map<String, Int> = emptyMap(),
    val query: String = "",
    val enabledCount: Int = 0,
    val draggingId: String? = null,
    val dragOffsetPx: Float = 0f,
    val exportText: String = "",
    val importVisible: Boolean = false,
    val importText: String = "",
    val batchMode: Boolean = false,
    val parsedCount: Int = 0,

    val exportVisible: Boolean = false,
) {
    val isEmpty: Boolean get() = poems.isEmpty()

    val noMatch: Boolean get() = poems.isNotEmpty() && visiblePoems.isEmpty()
}

@Immutable
data class LibraryActions(
    val onAdd: () -> Unit,
    val onEdit: (String) -> Unit,
    val onToggle: (PoemItem, Boolean) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onDragStart: (PoemItem) -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onMoveUp: (PoemItem) -> Unit,
    val onMoveDown: (PoemItem) -> Unit,
    val onRowMeasured: (Float) -> Unit,
    val onOpenImport: () -> Unit,
    val onOpenBatchEdit: () -> Unit,
    val onCloseImport: () -> Unit,
    val onImportTextChange: (String) -> Unit,
    val onPickImportFile: () -> Unit,
    val onConfirmImport: () -> Unit,
    val onOpenExport: () -> Unit,
    val onCloseExport: () -> Unit,
    val onCopyExport: () -> Unit,
    val onExportFile: () -> Unit,
)
