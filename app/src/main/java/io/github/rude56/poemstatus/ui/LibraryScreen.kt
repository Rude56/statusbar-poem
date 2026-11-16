package io.github.rude56.poemstatus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.PoemItem
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.PoemTextCodec
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 古诗库（一级页）。
 *
 * <p>交互约定（尽量贴近系统「联系人」「备忘录」这类列表页）：
 * <ul>
 *   <li><b>点一下</b> = 打开编辑页；</li>
 *   <li><b>长按拖动</b> = 排序（拖动时行浮起、有阴影）；</li>
 *   <li><b>左右滑动</b> = 删除，带二次确认；</li>
 *   <li><b>右侧开关</b> = 是否参与播放，不用进详情页；</li>
 *   <li>右下角 <b>FAB</b> = 新增；顶部「导入 / 导出」= 批量编辑。</li>
 * </ul>
 *
 * <p>排序只在松手时落盘一次：拖动过程中每跨一格就写一次 prefs 会频繁唤醒 SystemUI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val poems = AppState.poems.value
    val snackbar = LocalSnackbar.current
    val undoable = LocalUndoable.current
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<PoemItem?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val exportedText = remember(poems) { PoemTextCodec.encode(poems) }

    // 导出用 SAF 的「创建文档」。早期版本靠 Activity 回调 + 静态槽位接 Uri，
    // 现在换成 Activity Result API：契约挂在 Activity 上，页面被回收也不会丢结果。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = exportedText
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeTextTo(context, uri, payload) }
            snackbar(
                context.getString(
                    if (ok) R.string.poem_export_done else R.string.poem_export_failed
                )
            )
        }
    }

    // 拖动排序状态。拖动期间用工作副本，松手才提交。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val working = remember(poems) { mutableStateOf(poems) }
    val shown = if (draggingId != null) working.value else poems

    // 行高必须实测：以前写死 108px，在 640dpi（density 4.0）下只有 27dp，
    // 而真实卡片约 67dp ≈ 268px —— 拖 1/4 行就换位，列表会「抢跑」。
    var rowHeightPx by remember { mutableStateOf(0f) }

    val filtered = remember(shown, query) {
        if (query.isBlank()) shown
        else shown.filter {
            it.text.contains(query, true) ||
                it.author.contains(query, true) ||
                it.source.contains(query, true)
        }
    }

    // 序号必须取「整张列表里的位置」，而不是过滤后的位置 ——
    // 搜索时若从 1 重新编号，第 47 首会显示成「1」，用户会以为顺序变了。
    val indexById = remember(shown) {
        HashMap<String, Int>(shown.size * 2).apply {
            shown.forEachIndexed { i, p -> put(p.id, i) }
        }
    }

    // 拖动时的换位对象限定在「当前可见的诗」之间。
    //
    // 修的是一个会写坏数据的 bug：以前拖动时显示的是过滤后的列表，
    // 换位却按整张列表的下标算 —— 搜索状态下长按拖动，屏幕上一动不动，
    // 实际却在和看不见的诗悄悄换位，松手后顺序已经变了。
    // 现在只在可见的诗之间换位，被过滤掉的诗相对顺序保持不变（整体平移一格）。
    val visibleIds = remember(filtered) { filtered.mapTo(HashSet(filtered.size * 2)) { it.id } }

    if (poems.isEmpty()) {
        Column {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.empty_library_title),
                description = stringResource(R.string.empty_library_hint),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showImport = true }) {
                            Text(stringResource(R.string.poem_import))
                        }
                        TextButton(onClick = onAdd) {
                            Text(stringResource(R.string.add_poem))
                        }
                    }
                },
            )
        }
        ImportDialog(
            visible = showImport,
            onDismiss = { showImport = false },
            onImport = { text ->
                val list = poems.toMutableList()
                val result = PoemTextCodec.merge(list, PoemTextCodec.decode(text))
                if (result[0] > 0) AppState.persistPoems(context, list)
                showImport = false
                snackbar(
                    context.getString(R.string.import_result, result[0], result[1])
                )
            },
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        // 顶部摘要 + 导入导出入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.library_summary,
                    poems.size,
                    poems.count { it.enabled },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showImport = true }) {
                Text(stringResource(R.string.poem_import))
            }
            TextButton(onClick = { showExport = true }) {
                Text(stringResource(R.string.poem_export))
            }
        }
        Text(
            stringResource(R.string.library_swipe_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
        )

        // 诗一多（导入几百首后）就没法只靠滑：加一个即时过滤框。
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.library_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.library_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items = filtered, key = { _, poem -> poem.id }) { _, poem ->
                val isDragging = draggingId == poem.id
                val displayIndex = indexById[poem.id] ?: 0
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            // 不直接删：先弹确认框，用户取消时把行滑回去。
                            pendingDelete = poem
                            false
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.animateItem(),
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = draggingId == null,
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp)
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.shapes.medium,
                                )
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.delete),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    },
                ) {
                    PoemCard(
                        poem = poem,
                        index = displayIndex,
                        dragging = isDragging,
                        dragOffsetPx = if (isDragging) dragOffset else 0f,
                        onMeasured = { h ->
                            if (h > 0f && h != rowHeightPx) rowHeightPx = h
                        },
                        onEdit = { if (draggingId == null) onEdit(poem.id) },
                        onToggle = { on ->
                            val base = if (draggingId != null) working.value else poems
                            val list = base.map { if (it.id == poem.id) it.withEnabled(on) else it }
                            if (draggingId != null) working.value = list
                            else AppState.persistPoems(context, list)
                        },
                        onDragStart = {
                            working.value = poems
                            draggingId = poem.id
                            dragOffset = 0f
                        },
                        onDrag = { dy ->
                            dragOffset += dy
                            // 行高没量到时用一个保守值，绝不退回到写死的像素数。
                            val step = if (rowHeightPx > 0f) rowHeightPx else 1f
                            while (dragOffset > step / 2f) {
                                val next = moveAmongVisible(working.value, poem.id, visibleIds, 1)
                                if (next === working.value) break
                                working.value = next
                                dragOffset -= step
                            }
                            while (dragOffset < -step / 2f) {
                                val next = moveAmongVisible(working.value, poem.id, visibleIds, -1)
                                if (next === working.value) break
                                working.value = next
                                dragOffset += step
                            }
                        },
                        onDragEnd = {
                            val finalList = working.value
                            draggingId = null
                            dragOffset = 0f
                            if (finalList.map { it.id } != poems.map { it.id }) {
                                AppState.persistPoems(context, finalList)
                            }
                        },
                        onMoveUp = {
                            AppState.persistPoems(
                                context,
                                moveAmongVisible(poems, poem.id, visibleIds, -1),
                            )
                        },
                        onMoveDown = {
                            AppState.persistPoems(
                                context,
                                moveAmongVisible(poems, poem.id, visibleIds, 1),
                            )
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_confirm)) },
            text = {
                Text(
                    target.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 记下删除前的整份列表：撤销时直接还原，不需要撤销栈。
                    val before = poems
                    AppState.persistPoems(context, poems.filterNot { it.id == target.id })
                    pendingDelete = null
                    undoable(context.getString(R.string.deleted_one)) {
                        AppState.persistPoems(context, before)
                    }
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    ImportDialog(
        visible = showImport,
        onDismiss = { showImport = false },
        onImport = { text ->
            val list = poems.toMutableList()
            val result = PoemTextCodec.merge(list, PoemTextCodec.decode(text))
            if (result[0] > 0) AppState.persistPoems(context, list)
            showImport = false
            snackbar(context.getString(R.string.import_result, result[0], result[1]))
        },
    )

    if (showExport) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.poem_export)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.poem_export_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = exportedText,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("poems", exportedText)
                    )
                    showExport = false
                    snackbar(context.getString(R.string.poem_copied))
                }) {
                    Text(stringResource(R.string.poem_copy))
                }
            },
            dismissButton = {
                Row {
                    // 只进剪贴板的话，换机/重装时早就没了 —— 再给一个「存成文件」。
                    TextButton(onClick = {
                        showExport = false
                        exportLauncher.launch("poems.txt")
                    }) {
                        Text(stringResource(R.string.poem_export_file))
                    }
                    TextButton(onClick = { showExport = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

/**
 * 从 SAF 选好的文档里读出文本。
 *
 * <p>不用任何存储权限：系统选择器把 Uri 交给我们，`contentResolver` 直接读。
 * 读失败（文件被删、权限被回收、编码异常）统一返回 null，由调用方提示，
 * 绝不让异常冒到 Compose 的协程里把页面打崩。
 */
private fun readTextFrom(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        // 一次最多读 1 MB：诗库文本不会有这么大，超了说明选错了文件。
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

/** 把文本写到 SAF 选好的文档；返回是否成功。 */
private fun writeTextTo(context: Context, uri: android.net.Uri, text: String): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    }
    true
} catch (t: Throwable) {
    false
}

/** 导入对话框：粘贴或选文件 → 解析 → 合并（自动跳过重复）。 */
@Composable
private fun ImportDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    if (!visible) return
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { PoemTextCodec.decode(text) }
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val readFailedMsg = stringResource(R.string.poem_import_read_failed)
    val scope = rememberCoroutineScope()
    // 「从文件导入」是异步的：选完文件要读盘，读完把内容填进输入框。
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) { readTextFrom(context, uri) }
                if (content.isNullOrBlank()) snackbar(readFailedMsg)
                else text = content
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poem_import)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.poem_import_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.poem_import_hint)) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            // text/plain 之外再放开 */*：部分文件管理器把 .txt
                            // 报成 application/octet-stream，只给 text/plain 会选不中。
                            picker.launch(arrayOf("text/plain", "*/*"))
                        },
                    ) {
                        Text(stringResource(R.string.poem_import_from_file))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (parsed.isEmpty()) ""
                        else stringResource(R.string.poem_import_parsed, parsed.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = parsed.isNotEmpty(),
            ) {
                Text(stringResource(R.string.poem_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 单张诗卡。 */
@Composable
private fun PoemCard(
    poem: PoemItem,
    index: Int,
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
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // 实测行高（含间距由外层 spacedBy 提供，这里只要卡片自身高度）。
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
                        // 长按真正「抓住」卡片时给一下触感，确认拖的是这一张。
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
            .clickable(onClick = onEdit)
            // 长按拖动对 TalkBack / 无障碍用户不可用（触摸被朗读接管）。
            // 用系统标准的「自定义操作」补上移/下移，读屏用户从操作菜单里就能调顺序。
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true },
                    CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true },
                )
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 序号：让「播放顺序」这件事在列表里可见。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    poem.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (poem.enabled) FontWeight.Medium else FontWeight.Normal,
                    color = if (poem.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    if (poem.author.isNotEmpty()) append(poem.author)
                    if (poem.source.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append("《").append(poem.source).append("》")
                    }
                    if (isNotEmpty()) append(" · ")
                    append(poem.charCount).append(" 字")
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = poem.enabled, onCheckedChange = onToggle)
        }
    }
}

/** 行高由 {@code onGloballyPositioned} 实测，不再写死像素值。 */

/**
 * 在「可见的诗」之间移动一格，返回新的列表；移不动时原样返回。
 *
 * <p>真正的算法在 {@link PoemLibrary.moveAmongVisible}（可单测）：
 * 搜索过滤后按整表下标换位会把看不见的诗卷进来，用户毫无察觉但顺序已乱。
 *
 * <p>无路可走时<b>原样返回同一个对象</b>（`===` 可比），调用方据此退出循环。
 *
 * @param step -1 上移一格，+1 下移一格
 */
private fun moveAmongVisible(
    list: List<PoemItem>,
    id: String,
    visible: Set<String>,
    step: Int,
): List<PoemItem> {
    val out = list.toMutableList()
    return if (PoemLibrary.moveAmongVisible(out, id, visible, step)) out else list
}
