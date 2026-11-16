package io.github.rude56.poemstatus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.R

/**
 * 新增 / 编辑诗句（二级页，全屏而不是对话框）。
 *
 * <p>为什么从对话框改成整页：对话框在键盘弹出后只剩两行可见，
 * 而且「保存」按钮会被键盘顶掉；整页可以用 `imePadding()` 正确避让键盘，
 * 长诗句也能完整看到。
 */
@Composable
fun PoemEditScreen(
    poemId: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val poems = AppState.poems.value
    val undoable = LocalUndoable.current
    val initial = remember(poemId) { poems.firstOrNull { it.id == poemId } }

    var text by remember { mutableStateOf(initial?.text ?: "") }
    var author by remember { mutableStateOf(initial?.author ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf(false) }

    val duplicateMsg = stringResource(R.string.duplicate_poem)
    val emptyMsg = stringResource(R.string.poem_empty_error)

    fun save() {
        val t = text.trim()
        if (t.isEmpty()) {
            error = emptyMsg
            return
        }
        val list = poems.toMutableList()
        val ok = if (initial == null) {
            PoemLibrary.add(list, t, author, source) != null
        } else {
            PoemLibrary.edit(list, initial.id, t, author, source)
        }
        if (!ok) {
            error = duplicateMsg
            return
        }
        AppState.persistPoems(context, list)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            label = { Text(stringResource(R.string.poem_text)) },
            placeholder = { Text(stringResource(R.string.poem_text_hint)) },
            supportingText = {
                Text(
                    if (error != null) error!!
                    else stringResource(R.string.poem_text_counter, text.length)
                )
            },
            isError = error != null,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text(stringResource(R.string.poem_author)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text(stringResource(R.string.poem_source)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Text(
            stringResource(R.string.poem_author_source_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 删除只出现在「编辑已有诗」时，避免新增页出现一个无效按钮。
            if (initial != null) {
                OutlinedButton(
                    onClick = { pendingDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
            TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = { save() }, modifier = Modifier.weight(1.4f)) {
                Text(stringResource(R.string.save))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pendingDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_confirm)) },
            text = { Text(initial.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    // 从编辑页删除同样给撤销：这是比列表滑删更「重」的操作，
                    // 删完直接退回列表，用户连反悔的入口都没有。
                    val before = poems
                    AppState.persistPoems(context, poems.filterNot { it.id == initial.id })
                    pendingDelete = false
                    onDone()
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
                TextButton(onClick = { pendingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
