package io.github.rude56.poemstatus.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.PoemItem
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.material.ExpressiveSwitch
import io.github.rude56.poemstatus.ui.component.material.SegmentedTextField
import io.github.rude56.poemstatus.ui.component.material.SegmentedItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedListItem
import io.github.rude56.poemstatus.ui.component.bottombar.LocalMainPagerState
import io.github.rude56.poemstatus.ui.component.material.TonalCard
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.util.MarqueeText
import kotlinx.coroutines.delay

@Composable
fun LibraryMaterial(
    state: LibraryUiState,
    actions: LibraryActions,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val listState = state.listState
    val marqueeActive = LocalMainPagerState.current.selectedPage == 1

    val toast = ToastHolder.message
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            ToastHolder.clear()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {

            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                modifier = Modifier.padding(bottom = bottomInnerPadding),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_poem)) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),

        snackbarHost = {
            if (toast != null) {
                Box(Modifier.padding(16.dp)) {
                    ToastCard(message = toast)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (state.isEmpty) {

                EmptyLibrary(actions = actions, bottomInnerPadding = bottomInnerPadding)
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.library_summary,
                        state.poems.size,
                        state.enabledCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                RoundIconAction(

                    icon = Icons.Filled.FileDownload,
                    description = stringResource(R.string.poem_import),
                    onClick = actions.onOpenImport,
                )
                Spacer(Modifier.width(8.dp))
                RoundIconAction(
                    icon = Icons.Filled.FileUpload,
                    description = stringResource(R.string.poem_export),
                    onClick = actions.onOpenExport,
                )
                Spacer(Modifier.width(8.dp))
                RoundIconAction(
                    icon = Icons.Filled.Edit,
                    description = stringResource(R.string.poem_batch_edit),
                    onClick = actions.onOpenBatchEdit,
                )
            }

            SegmentedTextField(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                value = state.query,
                onValueChange = actions.onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.library_search)) },
            )

            if (state.noMatch) {
                Text(
                    text = stringResource(R.string.library_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),

                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 96.dp + bottomInnerPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(items = state.visiblePoems, key = { _, poem -> poem.id }) { visibleIndex, poem ->
                    val dragging = state.draggingId == poem.id
                    val displayIndex = state.indexById[poem.id] ?: 0
                    SegmentedItem(index = visibleIndex, count = state.visiblePoems.size) {
                        PoemRow(
                            marqueeActive = marqueeActive,
                            marqueeSpeedPx = state.marqueeSpeedPx,
                            showAuthor = state.showAuthor,
                            showSource = state.showSource,

                            modifier = if (dragging) {
                                Modifier
                            } else {
                                Modifier.animateItem()
                            },
                            poem = poem,
                            index = displayIndex,
                            dragging = dragging,
                            dragOffsetPx = if (dragging) state.dragOffsetPx else 0f,
                            onMeasured = actions.onRowMeasured,
                            onEdit = { actions.onEdit(poem.id) },
                            onToggle = { on -> actions.onToggle(poem, on) },
                            onDragStart = { actions.onDragStart(poem) },
                            onDrag = actions.onDrag,
                            onDragEnd = actions.onDragEnd,
                            onMoveUp = { actions.onMoveUp(poem) },
                            onMoveDown = { actions.onMoveDown(poem) },
                        )
                    }
                }
            }
        }
    }

    if (state.importVisible) {
        ImportDialog(state = state, actions = actions)
    }

    if (state.exportVisible) {
        ExportDialog(state = state, actions = actions)
    }
}

@Composable
private fun RoundIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun PoemRow(
    poem: PoemItem,
    index: Int,
    marqueeSpeedPx: Int,
    marqueeActive: Boolean,
    showAuthor: Boolean,
    showSource: Boolean,
    modifier: Modifier = Modifier,
    dragging: Boolean,
    dragOffsetPx: Float,
    onMeasured: (Float) -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .poemRowDrag(
            poem = poem,
            dragging = dragging,
            dragOffsetPx = dragOffsetPx,
            onMeasured = onMeasured,

            onClick = null,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )

    val container = if (dragging) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }

    SegmentedListItem(
        modifier = rowModifier,
        onClick = onEdit,
        colors = ListItemDefaults.segmentedColors().copy(containerColor = container),
        headlineContent = {
            val display = poem.displayText(showAuthor, showSource)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    modifier = Modifier.widthIn(min = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarqueeText(
                    text = display,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (poem.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    speedPxPerSecond = marqueeSpeedPx,
                    enabled = !dragging && marqueeActive,
                    cyclePauseMs = 1000L,
                )
            }
        },
        trailingContent = {

            ExpressiveSwitch(
                checked = poem.enabled,
                onCheckedChange = onToggle,
            )
        },
    )
}

@Composable
private fun EmptyLibrary(actions: LibraryActions, bottomInnerPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 32.dp + bottomInnerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_library_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_library_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = actions.onOpenImport) {
                Text(stringResource(R.string.poem_import))
            }
            Button(onClick = actions.onAdd) {
                Text(stringResource(R.string.add_poem))
            }
        }
    }
}

@Composable
private fun ImportDialog(
    state: LibraryUiState,
    actions: LibraryActions,
) {
    AlertDialog(
        onDismissRequest = actions.onCloseImport,
        title = {
            Text(
                stringResource(
                    if (state.batchMode) R.string.batch_edit_title else R.string.poem_import,
                ),
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (state.batchMode) R.string.batch_edit_desc else R.string.poem_import_desc,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SegmentedTextField(
                    value = state.importText,
                    onValueChange = actions.onImportTextChange,

                    noWrapLines = true,
                    minLines = 4,
                    placeholder = { Text(stringResource(R.string.poem_import_hint)) },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (!state.batchMode) {
                        TextButton(onClick = actions.onPickImportFile) {
                            Text(stringResource(R.string.poem_import_from_file))
                        }
                    }
                    if (state.parsedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.poem_import_parsed, state.parsedCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmImport) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCloseImport) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ExportDialog(
    state: LibraryUiState,
    actions: LibraryActions,
) {
    AlertDialog(
        onDismissRequest = actions.onCloseExport,
        title = { Text(stringResource(R.string.poem_export)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.poem_export_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SegmentedTextField(
                    value = state.exportText,
                    onValueChange = {},
                    readOnly = true,
                    noWrapLines = true,
                    minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onCopyExport) {
                Text(stringResource(R.string.poem_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onExportFile) {
                Text(stringResource(R.string.poem_export_file))
            }
        },
    )
}

@Composable
private fun ToastCard(message: String, modifier: Modifier = Modifier) {
    TonalCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}
