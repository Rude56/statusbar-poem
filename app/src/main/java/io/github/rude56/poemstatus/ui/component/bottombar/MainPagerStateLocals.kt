package io.github.rude56.poemstatus.ui.component.bottombar

import androidx.compose.runtime.staticCompositionLocalOf

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> {
    error("LocalMainPagerState not provided")
}

object MainPagerConfig {
    const val PAGE_COUNT = 3
    const val LAST_PAGE_INDEX = PAGE_COUNT - 1

    fun coercePage(page: Int): Int = page.coerceIn(0, LAST_PAGE_INDEX)
}
