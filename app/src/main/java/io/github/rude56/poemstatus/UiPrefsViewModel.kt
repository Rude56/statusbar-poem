package io.github.rude56.poemstatus

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.theme.ThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiPrefsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val prefs: SharedPreferences =
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in observedKeys) {
            _state.value = readState()
        }
    }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<UiPrefsState> = _state.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun readState(): UiPrefsState = UiPrefsState(
        appSettings = ThemeController.getAppSettings(app),
        uiMode = UiMode.fromValue(prefs.getString("ui_mode", UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE),
        enableBlur = prefs.getBoolean("enable_blur", true),
        enableFloatingBottomBar = prefs.getBoolean("enable_floating_bottom_bar", true),
        enableFloatingBottomBarBlur = prefs.getBoolean("enable_floating_bottom_bar_blur", true),
        enableSwipeDismiss = prefs.getBoolean("enable_swipe_dismiss", true),
        pageScale = prefs.getFloat("page_scale", 1.0f),
    )

    private companion object {
        val observedKeys = setOf(
            "color_mode",
            "key_color",
            "color_style",
            "color_spec",
            "ui_mode",
            "miuix_monet",
            "enable_blur",
            "enable_floating_bottom_bar",
            "enable_floating_bottom_bar_blur",
            "enable_swipe_dismiss",
            "page_scale",
        )
    }
}

lateinit var app: PoemApplication
