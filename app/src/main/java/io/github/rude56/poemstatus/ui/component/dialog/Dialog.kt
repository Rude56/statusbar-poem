package io.github.rude56.poemstatus.ui.component.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class ConfirmDialogVisuals(
    val title: String,
    val content: String? = null,
    val confirm: String? = null,
    val dismiss: String? = null,
)

sealed interface ConfirmResult {
    data object Confirmed : ConfirmResult
    data object Canceled : ConfirmResult
}

interface ConfirmDialogHandle {

    fun showConfirm(
        title: String,
        content: String? = null,
        confirm: String? = null,
        dismiss: String? = null,
    )

    suspend fun awaitConfirm(
        title: String,
        content: String? = null,
        confirm: String? = null,
        dismiss: String? = null,
    ): ConfirmResult
}

private class ConfirmDialogHandleImpl(
    private val visible: MutableState<Boolean>,
    private val scope: CoroutineScope,
    private val results: Channel<ConfirmResult>,
    initial: ConfirmDialogVisuals,
) : ConfirmDialogHandle {

    private var visualsState by mutableStateOf(initial)

    val visuals: ConfirmDialogVisuals get() = visualsState

    override fun showConfirm(
        title: String,
        content: String?,
        confirm: String?,
        dismiss: String?,
    ) {
        scope.launch {
            visualsState = ConfirmDialogVisuals(title, content, confirm, dismiss)
            visible.value = true
        }
    }

    override suspend fun awaitConfirm(
        title: String,
        content: String?,
        confirm: String?,
        dismiss: String?,
    ): ConfirmResult {
        showConfirm(title, content, confirm, dismiss)
        return results.receive()
    }

    fun finish(result: ConfirmResult) {
        scope.launch {
            results.send(result)
            visible.value = false
        }
    }
}

@Composable
fun rememberConfirmDialog(): ConfirmDialogHandle {
    val visible = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val results = remember { Channel<ConfirmResult>(Channel.BUFFERED) }
    val handle = remember {
        ConfirmDialogHandleImpl(
            visible = visible,
            scope = scope,
            results = results,
            initial = ConfirmDialogVisuals(""),
        )
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfirmDialogMiuix(
            visuals = handle.visuals,
            showDialog = visible,
            onResult = handle::finish,
        )

        UiMode.Material -> ConfirmDialogMaterial(
            visuals = handle.visuals,
            showDialog = visible,
            onResult = handle::finish,
        )
    }

    return handle
}
