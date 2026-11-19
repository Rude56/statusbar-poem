package io.github.rude56.poemstatus.ui.screen.holes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.navigation3.Route
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder

@Composable
fun CutoutScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val cfg = AppState.config.value
    val screenW = context.resources.displayMetrics.widthPixels
    val holes = remember(cfg.holesJson) { HoleRects.parse(cfg.holesJson) }
    val maxReachedMsg = stringResource(R.string.hole_max_reached, HoleRects.MAX_HOLES)
    val noRoomMsg = stringResource(R.string.hole_no_room)
    val state = CutoutUiState(holes = holes)
    fun addHole() {
        if (holes.size >= HoleRects.MAX_HOLES) {
            ToastHolder.show(maxReachedMsg)
            return
        }

        if (HoleRects.findFreeSpot(holes, screenW, 100) == null) {
            ToastHolder.show(noRoomMsg)
            return
        }
        navigator.push(Route.HoleNew)
    }

    fun toggle(hole: HoleRects.Hole, on: Boolean) {
        val next = cfg.copy()
        next.holesJson = HoleRects.encode(
            holes.map { if (it.slot == hole.slot) it.withEnabled(on) else it },
        )
        AppState.persist(context, next)
    }

    val actions = CutoutActions(
        onBack = { navigator.pop() },
        onEdit = { slot -> navigator.push(Route.HoleEdit(slot)) },
        onAdd = { addHole() },
        onToggle = ::toggle,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> CutoutMiuix(state, actions)
        UiMode.Material -> CutoutMaterial(state, actions)
    }
}
