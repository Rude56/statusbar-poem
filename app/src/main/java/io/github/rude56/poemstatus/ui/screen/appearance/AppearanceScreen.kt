package io.github.rude56.poemstatus.ui.screen.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.rude56.poemstatus.PoemApplication
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.theme.ColorMode
import io.github.rude56.poemstatus.ui.viewmodel.SettingsViewModel

@Composable
fun AppearanceScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = AppearanceUiState(
        uiMode = uiState.uiMode,
        colorMode = ColorMode.fromValue(uiState.themeMode),
        miuixMonet = uiState.miuixMonet,
        keyColor = uiState.keyColor,
        paletteStyle = uiState.paletteStyle,
        colorSpec = uiState.specVersion,
        enableBlur = uiState.enableBlur,
        enableFloatingBottomBar = uiState.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = uiState.enableFloatingBottomBarBlur,
        enablePredictiveBack = uiState.enablePredictiveBack,
        enableSwipeDismiss = uiState.enableSwipeDismiss,
        pageScale = uiState.pageScale,
    )

    val actions = AppearanceActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetUiMode = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Material.value else UiMode.Miuix.value)
        },
        onSetColorMode = viewModel::setColorMode,
        onSetMiuixMonet = viewModel::setMiuixMonet,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetEnableFloatingBottomBarBlur = viewModel::setEnableFloatingBottomBarBlur,
        onSetEnablePredictiveBack = { enabled ->
            viewModel.setEnablePredictiveBack(enabled)

            PoemApplication.setEnableOnBackInvokedCallback(context.applicationInfo, enabled)
            (context as? android.app.Activity)?.recreate()
        },
        onSetEnableSwipeDismiss = viewModel::setEnableSwipeDismiss,
        onSetPageScale = viewModel::setPageScale,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AppearanceMiuix(state, actions)
        UiMode.Material -> AppearanceMaterial(state, actions)
    }
}

val UiModeLabels = listOf("Material", "Miuix")

@Immutable
data class AppearanceUiState(
    val uiMode: String,
    val colorMode: ColorMode,
    val miuixMonet: Boolean,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enablePredictiveBack: Boolean,
    val enableSwipeDismiss: Boolean,
    val pageScale: Float,
)

@Immutable
data class AppearanceActions(
    val onBack: () -> Unit,
    val onSetUiMode: (Int) -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetMiuixMonet: (Boolean) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
    val onSetEnableBlur: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBar: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBarBlur: (Boolean) -> Unit,
    val onSetEnablePredictiveBack: (Boolean) -> Unit,
    val onSetEnableSwipeDismiss: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
)
