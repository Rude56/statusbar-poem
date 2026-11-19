package io.github.rude56.poemstatus.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rude56.poemstatus.PoemItem
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.WarningCard
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.util.MarqueeText
import io.github.rude56.poemstatus.ui.component.bottombar.LocalMainPagerState
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun LibraryMiuix(
    state: LibraryUiState,
    actions: LibraryActions,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val marqueeActive = LocalMainPagerState.current.selectedPage == 1
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val toast = ToastHolder.message
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            ToastHolder.clear()
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {

                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.library_title),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = actions.onAdd,

                modifier = Modifier.padding(end = 12.dp, bottom = 24.dp + bottomInnerPadding),
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = stringResource(R.string.add_poem),
                    tint = colorScheme.onPrimary,
                )
            }
        },
        popupHost = { },

        snackbarHost = {
            if (toast != null) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    WarningCard(message = toast)
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(

                state = state.listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                if (state.isEmpty) {
                    item { EmptyLibrary(actions = actions, bottomInnerPadding = bottomInnerPadding) }
                } else {
                    item {

                        SmallTitle(text = stringResource(R.string.library_summary, state.poems.size, state.enabledCount))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                icon = MiuixIcons.FileDownloads,
                                label = stringResource(R.string.poem_import),
                                onClick = actions.onOpenImport,
                            )
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                icon = MiuixIcons.UploadCloud,
                                label = stringResource(R.string.poem_export),
                                onClick = actions.onOpenExport,
                            )
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                icon = MiuixIcons.Edit,
                                label = stringResource(R.string.poem_batch_edit),
                                onClick = actions.onOpenBatchEdit,
                            )
                        }

                        InputField(
                            query = state.query,
                            onQueryChange = actions.onQueryChange,
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = stringResource(R.string.library_search),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )

                        if (state.noMatch) {
                            Text(
                                text = stringResource(R.string.library_no_match),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                            )
                        }
                    }

                    itemsIndexed(items = state.visiblePoems, key = { _, poem -> poem.id }) { _, poem ->
                        val dragging = state.draggingId == poem.id

                        Box(
                            Modifier
                                .padding(top = 8.dp)

                                .then(if (dragging) Modifier else Modifier.animateItem()),
                        ) {
                            PoemRow(
                                marqueeActive = marqueeActive,
                                marqueeSpeedPx = state.marqueeSpeedPx,
                                showAuthor = state.showAuthor,
                                showSource = state.showSource,
                                poem = poem,
                                index = state.indexById[poem.id] ?: 0,
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

                    item { Spacer(Modifier.height(96.dp + bottomInnerPadding)) }
                }
            }
        }
    }

    ImportDialog(visible = state.importVisible, state = state, actions = actions)
    ExportDialog(visible = state.exportVisible, state = state, actions = actions)
}

@Composable
private fun PoemRow(
    poem: PoemItem,
    index: Int,
    marqueeSpeedPx: Int,
    marqueeActive: Boolean,
    showAuthor: Boolean,
    showSource: Boolean,
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
    val modifier = Modifier
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

    Card(
        modifier = modifier,
        colors = if (dragging) {
            CardDefaults.defaultColors(color = colorScheme.surfaceContainerHigh)
        } else {
            CardDefaults.defaultColors()
        },
        onClick = onEdit,
    ) {
        BasicComponent(
            endActions = {
                Switch(
                    checked = poem.enabled,
                    onCheckedChange = onToggle,
                )
            },
        ) {
            val display = poem.displayText(showAuthor, showSource)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.widthIn(min = 16.dp),
                )
                MarqueeText(
                    text = display,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.headline1.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (poem.enabled) colorScheme.onSurface
                            else colorScheme.onSurfaceVariantSummary,
                    ),
                    speedPxPerSecond = marqueeSpeedPx,
                    enabled = !dragging && marqueeActive,
                    cyclePauseMs = 1000L,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(actions: LibraryActions, bottomInnerPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()

            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp + bottomInnerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            MiuixIcons.Notes,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_library_title),
            fontSize = MiuixTheme.textStyles.title3.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_library_hint),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(R.string.poem_import),
                onClick = actions.onOpenImport,
            )
            Button(
                onClick = actions.onAdd,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.add_poem))
            }
        }
    }
}

@Composable
private fun ImportDialog(
    visible: Boolean,
    state: LibraryUiState,
    actions: LibraryActions,
) {
    WindowDialog(
        show = visible,
        title = stringResource(
            if (state.batchMode) R.string.batch_edit_title else R.string.poem_import,
        ),
        summary = stringResource(
            if (state.batchMode) R.string.batch_edit_desc else R.string.poem_import_desc,
        ),
        maxWidth = 560.dp,
        onDismissRequest = actions.onCloseImport,
        content = {
            Column {
                TextField(
                    value = state.importText,
                    onValueChange = actions.onImportTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.poem_import_hint),
                    useLabelAsPlaceholder = true,

                    maxLines = 6,
                    minLines = 6,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    if (!state.batchMode) {
                        TextButton(
                            text = stringResource(R.string.poem_import_from_file),
                            onClick = actions.onPickImportFile,
                        )
                    }
                    if (state.parsedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.poem_import_parsed, state.parsedCount),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = colorScheme.primary,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = actions.onCloseImport,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = actions.onConfirmImport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun ExportDialog(
    visible: Boolean,
    state: LibraryUiState,
    actions: LibraryActions,
) {
    WindowDialog(
        show = visible,
        title = stringResource(R.string.poem_export),
        summary = stringResource(R.string.poem_export_desc),
        maxWidth = 560.dp,
        onDismissRequest = actions.onCloseExport,
        content = {
            Column {
                TextField(
                    value = state.exportText,
                    onValueChange = { },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,

                    maxLines = 6,
                    minLines = 6,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.poem_export_file),
                        onClick = actions.onExportFile,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.poem_copy),
                        onClick = actions.onCopyExport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,

        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
            Text(
                text = label,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}
