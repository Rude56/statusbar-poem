package io.github.rude56.poemstatus.data.repository

interface SettingsRepository {
    var uiMode: String
    var themeMode: Int

    var miuixMonet: Boolean

    var keyColor: Int

    var colorStyle: String

    var colorSpec: String

    var enablePredictiveBack: Boolean

    var enableSwipeDismiss: Boolean

    var enableBlur: Boolean

    var enableFloatingBottomBar: Boolean

    var enableFloatingBottomBarBlur: Boolean

    var pageScale: Float

    fun setUiModeAndTheme(uiMode: String, themeMode: Int)
}
