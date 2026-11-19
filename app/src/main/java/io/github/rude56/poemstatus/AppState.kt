package io.github.rude56.poemstatus

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object AppState {
    val config = mutableStateOf(PoemConfig())
    val poems = mutableStateOf<List<PoemItem>>(emptyList())
    val installedApps = mutableStateOf<List<AppEntry>>(emptyList())
    val bootstrapped = mutableStateOf(false)

    data class AppEntry(val packageName: String, val label: String)

    fun reload(context: Context) {
        val loaded = ConfigStore.load(context)
        config.value = loaded
        poems.value = PoemLibrary.load(loaded.poemsSeeded, loaded.poemsJson)
        bootstrapped.value = true
    }

    fun persist(context: Context, next: PoemConfig) {
        ConfigStore.save(context, next, true)
        ConfigStore.notifyConfigChanged(context)
        config.value = next
    }

    fun persistPoems(context: Context, list: List<PoemItem>) {
        val next = config.value.copy()
        next.poemsJson = PoemItem.listToJson(list)
        next.poemsSeeded = true
        poems.value = list
        persist(context, next)
    }
}
