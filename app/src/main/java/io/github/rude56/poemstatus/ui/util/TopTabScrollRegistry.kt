package io.github.rude56.poemstatus.ui.util

import kotlinx.coroutines.launch

object TopTabScrollRegistry {
    private val scrollers = mutableMapOf<Int, suspend () -> Unit>()
    fun register(page: Int, scrollToTop: suspend () -> Unit) {
        scrollers[page] = scrollToTop
    }
    fun unregister(page: Int) {
        scrollers.remove(page)
    }

    fun rescroll(page: Int, scope: kotlinx.coroutines.CoroutineScope) {
        val action = scrollers[page] ?: return
        scope.launch { action() }
    }
}
