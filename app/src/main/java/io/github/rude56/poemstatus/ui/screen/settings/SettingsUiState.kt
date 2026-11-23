package io.github.rude56.poemstatus.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.theme.ColorMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = true,
    val enableSwipeDismiss: Boolean = true,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,

    val speedPx: Int = 120,
    val clockSeconds: Int = 30,
    val randomOrder: Boolean = false,
    val showAuthor: Boolean = true,
    val showSource: Boolean = false,
    val bobEnabled: Boolean = false,
    val landscapePause: Boolean = true,
    val lockscreenPause: Boolean = true,
    val debugLog: Boolean = false,

    val holeCount: Int = 0,
    val whitelistCount: Int = 0,
) {

    val colorMode: ColorMode get() = ColorMode.fromValue(themeMode)

    val paletteStyle: PaletteStyle
        get() = try {
            PaletteStyle.valueOf(colorStyle)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }

    val specVersion: ColorSpec.SpecVersion
        get() = try {
            ColorSpec.SpecVersion.valueOf(colorSpec)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.Default
        }
}

@Immutable
data class SettingsScreenActions(
    val onOpenAppearance: () -> Unit,
    val onOpenHoles: () -> Unit,
    val onOpenWhitelist: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onSetSpeed: (Int) -> Unit,
    val onSetClock: (Int) -> Unit,
    val onSetRandomOrder: (Boolean) -> Unit,
    val onSetShowAuthor: (Boolean) -> Unit,
    val onSetShowSource: (Boolean) -> Unit,
    val onSetBob: (Boolean) -> Unit,
    val onSetLandscapePause: (Boolean) -> Unit,
    val onSetLockscreenPause: (Boolean) -> Unit,
    val onSetDebugLog: (Boolean) -> Unit,
    val onExportLog: () -> Unit,
)
