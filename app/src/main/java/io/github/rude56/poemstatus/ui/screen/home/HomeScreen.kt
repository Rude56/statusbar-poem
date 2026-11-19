package io.github.rude56.poemstatus.ui.screen.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.component.bottombar.LocalMainPagerState
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.navigation3.Route
import io.github.rude56.poemstatus.ui.util.TopTabScrollRegistry
import io.github.rude56.poemstatus.ui.util.isConnected
import io.github.rude56.poemstatus.ui.util.readSecondsAgo
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(bottomInnerPadding: Dp = 0.dp) {
    val navigator = LocalNavigator.current
    val cfg = AppState.config.value
    val poems = AppState.poems.value
    val enabledPoems = poems.count { it.enabled }
    val holes = HoleRects.parse(cfg.holesJson)
    val mainPagerState = LocalMainPagerState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isOverviewVisible = mainPagerState.selectedPage == 0
    var secondsAgo by remember { mutableLongStateOf(readSecondsAgo()) }
    LaunchedEffect(lifecycleOwner, isOverviewVisible) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            secondsAgo = readSecondsAgo()
            if (isOverviewVisible) {
                while (true) {
                    delay(2000)
                    secondsAgo = readSecondsAgo()
                }
            }
        }
    }

    val connected = isConnected(secondsAgo)

    val state = HomeUiState(
        connected = connected,
        secondsAgo = secondsAgo,
        enabled = cfg.enabled,
        totalPoems = poems.size,
        enabledPoems = enabledPoems,
        speedPx = cfg.speedPx,
        clockSeconds = cfg.clockSeconds,
        randomOrder = PoemLibrary.ORDER_RANDOM.equals(cfg.orderMode),
        holeCount = holes.size,
        holeEnabledCount = holes.count { it.enabled },
        whitelistCount = cfg.whitelist.size,
    )

    val actions = HomeActions(
        onOpenStatus = { navigator.push(Route.Status) },
        onOpenAbout = { navigator.push(Route.About) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                TopTabScrollRegistry.register(0) { listState.animateScrollToItem(0) }
            }
            DisposableEffect(Unit) {
                onDispose { TopTabScrollRegistry.unregister(0) }
            }
            HomeMiuix(state, actions, bottomInnerPadding, listState)
        }
        UiMode.Material -> {
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState) {
                TopTabScrollRegistry.register(0) { scrollState.animateScrollTo(0) }
            }
            DisposableEffect(Unit) {
                onDispose { TopTabScrollRegistry.unregister(0) }
            }
            HomeMaterial(state, actions, bottomInnerPadding, scrollState)
        }
    }
}

object ToastHolder {
    var message by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun show(text: String) {
        message = text
    }

    fun clear() {
        message = null
    }
}
