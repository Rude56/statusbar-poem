package io.github.rude56.poemstatus.ui.screen.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.navigation3.Route
import io.github.rude56.poemstatus.ui.util.TopTabScrollRegistry
import io.github.rude56.poemstatus.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(bottomInnerPadding: Dp = 0.dp) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val actions = SettingsScreenActions(
        onOpenAppearance = dropUnlessResumed { navigator.push(Route.Appearance) },
        onOpenHoles = dropUnlessResumed { navigator.push(Route.Holes) },
        onOpenWhitelist = dropUnlessResumed { navigator.push(Route.Whitelist) },
        onOpenAbout = dropUnlessResumed { navigator.push(Route.About) },
        onSetSpeed = viewModel::setSpeed,
        onSetClock = viewModel::setClock,
        onSetRandomOrder = viewModel::setRandomOrder,
        onSetShowAuthor = viewModel::setShowAuthor,
        onSetShowSource = viewModel::setShowSource,
        onSetBob = viewModel::setBob,
        onSetLandscapePause = viewModel::setLandscapePause,
        onSetLockscreenPause = viewModel::setLockscreenPause,
        onSetDebugLog = viewModel::setDebugLog,
        onExportLog = { },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                TopTabScrollRegistry.register(2) { listState.animateScrollToItem(0) }
            }
            DisposableEffect(Unit) {
                onDispose { TopTabScrollRegistry.unregister(2) }
            }
            SettingsMiuix(uiState, actions, bottomInnerPadding, listState)
        }
        UiMode.Material -> {
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState) {
                TopTabScrollRegistry.register(2) { scrollState.animateScrollTo(0) }
            }
            DisposableEffect(Unit) {
                onDispose { TopTabScrollRegistry.unregister(2) }
            }
            SettingsMaterial(uiState, actions, bottomInnerPadding, scrollState)
        }
    }
}
