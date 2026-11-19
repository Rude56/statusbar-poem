package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.component.dialog.ConfirmResult
import io.github.rude56.poemstatus.ui.component.dialog.rememberConfirmDialog
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import kotlinx.coroutines.launch

@Composable
fun PoemEditScreen(poemId: String?) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val onDone: () -> Unit = { navigator.pop() }
    val poems = AppState.poems.value
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    val initial = remember(poemId) { poems.firstOrNull { it.id == poemId } }

    var text by remember { mutableStateOf(initial?.text ?: "") }
    var author by remember { mutableStateOf(initial?.author ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val duplicateMsg = stringResource(R.string.duplicate_poem)
    val emptyMsg = stringResource(R.string.poem_empty_error)
    val deleteConfirmTitle = stringResource(R.string.delete_confirm)
    val deleteLabel = stringResource(R.string.delete)
    val cancelLabel = stringResource(R.string.cancel)
    val deletedMsg = stringResource(R.string.deleted_one)

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

    fun delete() {
        if (initial == null) return
        scope.launch {

            val result = confirmDialog.awaitConfirm(
                title = deleteConfirmTitle,
                content = initial.text,
                confirm = deleteLabel,
                dismiss = cancelLabel,
            )
            if (result == ConfirmResult.Confirmed) {
                AppState.persistPoems(context, poems.filterNot { it.id == initial.id })
                onDone()
                ToastHolder.show(deletedMsg)
            }
        }
    }

    val state = PoemEditUiState(
        text = text,
        author = author,
        source = source,
        error = error,
        isNew = initial == null,
    )
    val actions = PoemEditActions(
        onTextChange = {
            text = it
            error = null
        },
        onAuthorChange = { author = it },
        onSourceChange = { source = it },
        onCancel = onDone,
        onSave = { save() },
        onDelete = { delete() },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> PoemEditMiuix(state, actions)
        UiMode.Material -> PoemEditMaterial(state, actions)
    }
}
