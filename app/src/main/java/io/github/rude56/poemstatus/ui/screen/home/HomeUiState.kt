package io.github.rude56.poemstatus.ui.screen.home

import androidx.compose.runtime.Immutable

@Immutable
data class HomeUiState(
    val connected: Boolean = false,
    val secondsAgo: Long = -1L,
    val enabled: Boolean = false,
    val totalPoems: Int = 0,

    val enabledPoems: Int = 0,
    val speedPx: Int = 120,
    val clockSeconds: Int = 30,
    val randomOrder: Boolean = false,
    val holeCount: Int = 0,
    val holeEnabledCount: Int = 0,
    val whitelistCount: Int = 0,
) {

    val tone: HomeTone
        get() = when {
            !enabled -> HomeTone.OFF
            !connected -> HomeTone.NOT_CONNECTED
            totalPoems == 0 || enabledPoems == 0 -> HomeTone.NO_POEMS
            else -> HomeTone.OK
        }
}

enum class HomeTone {
    NOT_CONNECTED,
    OFF,
    NO_POEMS,
    OK,
}

@Immutable
data class HomeActions(

    val onOpenStatus: () -> Unit,

    val onOpenAbout: () -> Unit,
)
