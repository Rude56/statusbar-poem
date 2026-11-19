package io.github.rude56.poemstatus.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.BuildConfig
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator

const val REPO_URL = "https://github.com/Rude56/statusbar-poem"

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val state = AboutUiState(
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
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
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutMiuix(state, actions)
        UiMode.Material -> AboutMaterial(state, actions)
    }
}
