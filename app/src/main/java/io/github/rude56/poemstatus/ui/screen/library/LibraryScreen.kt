package io.github.rude56.poemstatus.ui.screen.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.PoemItem
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.PoemTextCodec
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.navigation3.Route
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.util.TopTabScrollRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(bottomInnerPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val poems = AppState.poems.value
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var importVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var batchMode by remember { mutableStateOf(false) }
    var exportVisible by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val working = remember(poems) { mutableStateOf(poems) }
    val shown = if (draggingId != null) working.value else poems

    var rowHeightPx by remember { mutableStateOf(0f) }

    val filtered = remember(shown, query) {
        if (query.isBlank()) {
            shown
        } else {
            shown.filter {
                it.text.contains(query, true) ||
                    it.author.contains(query, true) ||
                    it.source.contains(query, true)
            }
        }
    }

    val listState = rememberLazyListState()

    val cfg = AppState.config.value
    val marqueeSpeedPx = cfg.speedPx

    val indexById = remember(shown) {
        HashMap<String, Int>(shown.size * 2).apply {
            shown.forEachIndexed { i, p -> put(p.id, i) }
        }
    }

    val visibleIds = remember(filtered) { filtered.mapTo(HashSet(filtered.size * 2)) { it.id } }
    val exportedText = remember(poems) { PoemTextCodec.encode(poems) }
    val parsedCount = remember(importText) { PoemTextCodec.decode(importText).size }

    fun toggle(target: PoemItem, on: Boolean) {
        val base = if (draggingId != null) working.value else poems
        val list = base.map { if (it.id == target.id) it.withEnabled(on) else it }
        if (draggingId != null) working.value = list else AppState.persistPoems(context, list)
    }

    fun drag(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val step = if (rowHeightPx > 0f) rowHeightPx else 1f
        while (dragOffset > step / 2f) {
            val next = moveAmongVisible(working.value, id, visibleIds, 1)
            if (next === working.value) break
            working.value = next
            dragOffset -= step
        }
        while (dragOffset < -step / 2f) {
            val next = moveAmongVisible(working.value, id, visibleIds, -1)
            if (next === working.value) break
            working.value = next
            dragOffset += step
        }
    }
    fun commitImport(text: String) {
        if (batchMode) {

            val parsed = PoemTextCodec.decode(text)
            AppState.persistPoems(context, parsed)
            importVisible = false
            importText = ""
            batchMode = false
            ToastHolder.show(context.getString(R.string.batch_saved, parsed.size))
            return
        }
        val list = poems.toMutableList()
        val result = PoemTextCodec.merge(list, PoemTextCodec.decode(text))
        if (result[0] > 0) AppState.persistPoems(context, list)
        importVisible = false
        importText = ""
        ToastHolder.show(context.getString(R.string.import_result, result[0], result[1]))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = exportedText
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeTextTo(context, uri, payload) }
            ToastHolder.show(
                context.getString(
                    if (ok) R.string.poem_export_done else R.string.poem_export_failed
                )
            )
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) { readTextFrom(context, uri) }
            if (content.isNullOrBlank()) {
                ToastHolder.show(context.getString(R.string.poem_import_read_failed))
            } else {
                importText = content
            }
        }
    }

    val actions = LibraryActions(
        onAdd = { navigator.push(Route.PoemEdit(null)) },
        onEdit = { id -> navigator.push(Route.PoemEdit(id)) },
        onToggle = ::toggle,
        onQueryChange = { query = it },
        onDragStart = { poem ->
            working.value = poems
            draggingId = poem.id
            dragOffset = 0f
        },
        onDrag = { drag(it) },
        onDragEnd = {
            val finalList = working.value
            draggingId = null
            dragOffset = 0f
            if (finalList.map { it.id } != poems.map { it.id }) {
                AppState.persistPoems(context, finalList)
            }
        },
        onMoveUp = { poem ->
            AppState.persistPoems(context, moveAmongVisible(poems, poem.id, visibleIds, -1))
        },
        onMoveDown = { poem ->
            AppState.persistPoems(context, moveAmongVisible(poems, poem.id, visibleIds, 1))
        },
        onRowMeasured = { h ->
            if (h > 0f && h != rowHeightPx) rowHeightPx = h
        },
        onOpenImport = {
            batchMode = false
            importText = ""
            importVisible = true
        },
        onOpenBatchEdit = {

            batchMode = true
            importText = exportedText
            importVisible = true
        },
        onCloseImport = {
            importVisible = false
            importText = ""
            batchMode = false
        },
        onImportTextChange = { importText = it },
        onPickImportFile = {

            importPicker.launch(arrayOf("text/plain", "*/*"))
        },
        onConfirmImport = { commitImport(importText) },
        onOpenExport = { exportVisible = true },
        onCloseExport = { exportVisible = false },
        onCopyExport = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("poems", exportedText))
            exportVisible = false
            ToastHolder.show(context.getString(R.string.poem_copied))
        },
        onExportFile = {
            exportVisible = false

            exportLauncher.launch(exportFileName(poems.size))
        },
    )

    val state = LibraryUiState(
        listState = listState,
        marqueeSpeedPx = marqueeSpeedPx,
        showAuthor = cfg.showAuthor,
        showSource = cfg.showSource,
        poems = poems,
        visiblePoems = filtered,
        indexById = indexById,
        query = query,
        enabledCount = poems.count { it.enabled },
        draggingId = draggingId,
        dragOffsetPx = dragOffset,
        exportText = exportedText,
        importVisible = importVisible,
        importText = importText,
        batchMode = batchMode,
        parsedCount = parsedCount,
        exportVisible = exportVisible,
    )

    LaunchedEffect(listState) {
        TopTabScrollRegistry.register(1) { listState.animateScrollToItem(0) }
    }
    DisposableEffect(Unit) {
        onDispose { TopTabScrollRegistry.unregister(1) }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> LibraryMiuix(state, actions, bottomInnerPadding)
        UiMode.Material -> LibraryMaterial(state, actions, bottomInnerPadding)
    }
}

@Composable
internal fun Modifier.poemRowDrag(
    poem: PoemItem,
    dragging: Boolean,
    dragOffsetPx: Float,
    onMeasured: (Float) -> Unit,
    onClick: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
): Modifier {
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val haptics = LocalHapticFeedback.current

    return this
        .onGloballyPositioned { coords ->
            onMeasured(coords.size.height.toFloat())
        }
        .graphicsLayer {
            translationY = dragOffsetPx
            if (dragging) {
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 12.dp.toPx()
            }
        }
        .pointerInput(poem.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {

                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDragStart()
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount.y)
                },
            )
        }

        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true },
                CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true },
            )
        }
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
}

private fun moveAmongVisible(
    list: List<PoemItem>,
    id: String,
    visible: Set<String>,
    step: Int,
): List<PoemItem> {
    val out = list.toMutableList()
    return if (PoemLibrary.moveAmongVisible(out, id, visible, step)) out else list
}

private fun readTextFrom(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->

        val buf = ByteArray(1 shl 20)
        var len = 0
        var n: Int
        while (len < buf.size) {
            n = input.read(buf, len, buf.size - len)
            if (n <= 0) break
            len += n
        }
        String(buf, 0, len, Charsets.UTF_8).removePrefix("\uFEFF")
    }
} catch (t: Throwable) {
    null
}
private fun writeTextTo(context: Context, uri: android.net.Uri, text: String): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    }
    true
} catch (t: Throwable) {
    false
}

private fun exportFileName(count: Int): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())
    return "古诗库-$count-$date.txt"
}
