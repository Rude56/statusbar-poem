package io.github.rude56.poemstatus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.app
import io.github.rude56.poemstatus.data.repository.SettingsRepository
import io.github.rude56.poemstatus.data.repository.SettingsRepositoryImpl
import io.github.rude56.poemstatus.ui.screen.settings.SettingsUiState
import io.github.rude56.poemstatus.ui.theme.ColorMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cfg = AppState.config.value
            val holes = HoleRects.parse(cfg.holesJson)
            _uiState.update {
                it.copy(

                    uiMode = repo.uiMode,
                    themeMode = repo.themeMode,
                    miuixMonet = repo.miuixMonet,
                    keyColor = repo.keyColor,
                    colorStyle = repo.colorStyle,
                    colorSpec = repo.colorSpec,
                    enablePredictiveBack = repo.enablePredictiveBack,
                    enableSwipeDismiss = repo.enableSwipeDismiss,
                    enableBlur = repo.enableBlur,
                    enableFloatingBottomBar = repo.enableFloatingBottomBar,
                    enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur,
                    pageScale = repo.pageScale,

                    speedPx = cfg.speedPx,
                    clockSeconds = cfg.clockSeconds,
                    randomOrder = PoemLibrary.ORDER_RANDOM.equals(cfg.orderMode),
                    showAuthor = cfg.showAuthor,
                    showSource = cfg.showSource,
                    bobEnabled = cfg.bobEnabled,
                    landscapePause = cfg.landscapePause,
                    lockscreenPause = cfg.lockscreenPause,
                    debugLog = cfg.debugLog,

                    holeCount = holes.size,
                    whitelistCount = cfg.whitelist.size,
                )
            }
        }
    }

    fun setUiMode(mode: String) {
        val oldMode = repo.uiMode
        val currentThemeMode = repo.themeMode
        val newThemeMode = when (oldMode) {
            "material" if mode == "miuix" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
                if (repo.miuixMonet && !colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toMonetMode()
                } else if (!repo.miuixMonet && colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toNonMonetMode()
                } else {
                    baseMode
                }
            }

            "miuix" if mode == "material" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
            }

            else -> currentThemeMode
        }

        repo.setUiModeAndTheme(mode, newThemeMode)
        _uiState.update { it.copy(uiMode = mode, themeMode = newThemeMode) }
    }

    fun setThemeMode(mode: Int) {
        val currentUiMode = repo.uiMode
        val effectiveMode = if (currentUiMode == "miuix" && _uiState.value.miuixMonet) {
            mode + 3
        } else {
            mode
        }
        repo.themeMode = effectiveMode
        _uiState.update { it.copy(themeMode = effectiveMode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }
    fun setMiuixMonet(enabled: Boolean) {
        val currentThemeMode = repo.themeMode
        val colorMode = ColorMode.fromValue(currentThemeMode)
        val newThemeMode = if (enabled) {
            if (!colorMode.isMonet) colorMode.toMonetMode() else currentThemeMode
        } else {
            if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
        }
        repo.miuixMonet = enabled
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(miuixMonet = enabled, themeMode = newThemeMode) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    fun setEnableSwipeDismiss(enabled: Boolean) {
        repo.enableSwipeDismiss = enabled
        _uiState.update { it.copy(enableSwipeDismiss = enabled) }
    }

    fun setEnableBlur(enabled: Boolean) {
        repo.enableBlur = enabled
        _uiState.update { it.copy(enableBlur = enabled) }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repo.enableFloatingBottomBar = enabled
        _uiState.update { it.copy(enableFloatingBottomBar = enabled) }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repo.enableFloatingBottomBarBlur = enabled
        _uiState.update { it.copy(enableFloatingBottomBarBlur = enabled) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }

    fun setSpeed(px: Int) {
        val next = AppState.config.value.copy()
        next.speedPx = px
        AppState.persist(app, next)
        _uiState.update { it.copy(speedPx = px) }
    }

    fun setClock(seconds: Int) {
        val next = AppState.config.value.copy()
        next.clockSeconds = seconds
        AppState.persist(app, next)
        _uiState.update { it.copy(clockSeconds = seconds) }
    }

    fun setRandomOrder(random: Boolean) {
        val next = AppState.config.value.copy()
        next.orderMode = if (random) PoemLibrary.ORDER_RANDOM else PoemLibrary.ORDER_SEQUENTIAL
        AppState.persist(app, next)
        _uiState.update { it.copy(randomOrder = random) }
    }

    fun setShowAuthor(show: Boolean) {
        val next = AppState.config.value.copy()
        next.showAuthor = show
        AppState.persist(app, next)
        _uiState.update { it.copy(showAuthor = show) }
    }

    fun setShowSource(show: Boolean) {
        val next = AppState.config.value.copy()
        next.showSource = show
        AppState.persist(app, next)
        _uiState.update { it.copy(showSource = show) }
    }

    fun setBob(enabled: Boolean) {
        val next = AppState.config.value.copy()
        next.bobEnabled = enabled
        AppState.persist(app, next)
        _uiState.update { it.copy(bobEnabled = enabled) }
    }

    fun setLandscapePause(pause: Boolean) {
        val next = AppState.config.value.copy()
        next.landscapePause = pause
        AppState.persist(app, next)
        _uiState.update { it.copy(landscapePause = pause) }
    }

    fun setLockscreenPause(pause: Boolean) {
        val next = AppState.config.value.copy()
        next.lockscreenPause = pause
        AppState.persist(app, next)
        _uiState.update { it.copy(lockscreenPause = pause) }
    }

    fun setDebugLog(on: Boolean) {
        val next = AppState.config.value.copy()
        next.debugLog = on
        AppState.persist(app, next)
        _uiState.update { it.copy(debugLog = on) }
    }
}
