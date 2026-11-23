package io.github.rude56.poemstatus.ui.screen.about

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.BuildConfig
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.app
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.util.debugLogFileName
import io.github.rude56.poemstatus.ui.util.readDebugLog
import io.github.rude56.poemstatus.ui.util.writeTextTo
import kotlinx.coroutines.launch

const val REPO_URL = "https://github.com/Rude56/statusbar-poem"

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // AppState.config 是 mutableStateOf，Compose 里直接读即自动订阅。
    val cfg = AppState.config.value

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readDebugLog(context)
            if (text.isBlank()) {
                ToastHolder.show("没有日志：请先打开「调试日志」，然后让状态栏播放一首")
                return@launch
            }
            val ok = writeTextTo(context, uri, text)
            ToastHolder.show(
                if (ok) "已导出 ${text.lines().size} 行日志" else "导出失败，换个位置再试",
            )
        }
    }

    val state = AboutUiState(
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        debugLog = cfg.debugLog,
        links = listOf(
            AboutLink(
                title = stringResource(R.string.about_repo_title),
                summary = REPO_URL,
                url = REPO_URL,
            ),
        ),
    )

    val actions = AboutActions(
        onBack = { navigator.pop() },

        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
        onSetDebugLog = { on ->
            val next = AppState.config.value.copy()
            next.debugLog = on
            AppState.persist(app, next)
        },
        onExportLog = { exportLauncher.launch(debugLogFileName()) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutMiuix(state, actions)
        UiMode.Material -> AboutMaterial(state, actions)
    }
}
