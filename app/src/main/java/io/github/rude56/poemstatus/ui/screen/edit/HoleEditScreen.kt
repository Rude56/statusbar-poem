package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.HoleDraft
import io.github.rude56.poemstatus.ui.HoleDraftHolder
import io.github.rude56.poemstatus.ui.component.dialog.ConfirmResult
import io.github.rude56.poemstatus.ui.component.dialog.rememberConfirmDialog
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import kotlinx.coroutines.launch

@Composable
fun HoleEditScreen(slot: Int?) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val onDone: () -> Unit = { navigator.pop() }
    val cfg = AppState.config.value
    val holes = remember(cfg.holesJson) { HoleRects.parse(cfg.holesJson) }
    val screenW = context.resources.displayMetrics.widthPixels
    val isNew = slot == null
    val initial = remember(slot, holes) { slot?.let { s -> holes.firstOrNull { it.slot == s } } }
    if (!isNew && initial == null) {
        LaunchedEffect(slot) { onDone() }
        return
    }

    val slotForColor = initial?.slot ?: HoleRects.nextFreeSlot(holes)

    var left by remember { mutableStateOf(initial?.left?.toString() ?: "") }
    var right by remember { mutableStateOf(initial?.right?.toString() ?: "") }
    var wormhole by remember { mutableStateOf(initial?.isWormhole() ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    SideEffect {
        HoleDraftHolder.draft = HoleDraft(
            slot = slotForColor,
            left = left.toIntOrNull(),
            right = right.toIntOrNull(),
        )
    }
    DisposableEffect(Unit) {
        onDispose { HoleDraftHolder.draft = null }
    }

    val invalidMsg = stringResource(R.string.hole_invalid_range)
    val bothRequiredMsg = stringResource(R.string.hole_both_required)
    val deleteTitle = stringResource(R.string.delete)
    val deleteBody = stringResource(R.string.hole_delete_confirm_desc)
    val dialog = rememberConfirmDialog()
    fun save() {
        val l = left.toIntOrNull()
        val r = right.toIntOrNull()

        if (l == null || r == null) {
            error = bothRequiredMsg
            return
        }
        if (r <= l) {
            error = invalidMsg
            return
        }

        val clash = HoleRects.firstOverlap(holes, l, r, slotForColor)
        if (clash != null) {
            error = context.getString(
                R.string.hole_overlap,
                clash.slot + 1,
                clash.left,
                clash.right,
            )
            return
        }
        val mode = if (wormhole) HoleRects.Mode.WORMHOLE else HoleRects.Mode.TUNNEL
        val updated = if (isNew) {
            holes + HoleRects.Hole(l, r, mode, HoleRects.nextFreeSlot(holes))
        } else {
            holes.map {
                if (it.slot == slot) HoleRects.Hole(l, r, mode, slot) else it
            }
        }
        val next = cfg.copy()
        next.holesJson = HoleRects.encode(updated)
        AppState.persist(context, next)
        onDone()
    }

    fun delete() {
        val next = cfg.copy()
        next.holesJson = HoleRects.encode(holes.filterNot { it.slot == slot })
        AppState.persist(context, next)
        onDone()
    }

    val state = HoleEditUiState(
        slot = slotForColor,
        left = left,
        right = right,
        wormhole = wormhole,
        error = error,
        screenWidth = screenW,
        isNew = isNew,
    )
    val actions = HoleEditActions(
        onLeftChange = { input ->
            left = input.filter { it.isDigit() }.take(5)
            error = null
        },
        onRightChange = { input ->
            right = input.filter { it.isDigit() }.take(5)
            error = null
        },
        onModeChange = { wormhole = it },
        onCancel = onDone,
        onSave = { save() },
        onDelete = {

            scope.launch {
                val result = dialog.awaitConfirm(
                    title = deleteTitle,
                    content = deleteBody,
                    confirm = deleteTitle,
                )
                if (result == ConfirmResult.Confirmed) {
                    delete()
                }
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HoleEditMiuix(state, actions)
        UiMode.Material -> HoleEditMaterial(state, actions)
    }
}
