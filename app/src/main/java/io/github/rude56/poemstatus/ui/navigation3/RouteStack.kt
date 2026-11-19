package io.github.rude56.poemstatus.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

@Composable
fun rememberRouteStack(): NavBackStack {
    val saver = listSaver<SnapshotStateList<NavKey>, String>(
        save = { stack -> stack.map { (it as Route).encode() } },
        restore = { saved ->
            val routes = saved.mapNotNull(Route::decode).toMutableStateList<NavKey>()
            if (routes.isEmpty()) routes.add(Route.Main)
            routes
        },
    )
    return rememberSaveable(saver = saver) {
        listOf<NavKey>(Route.Main).toMutableStateList()
    }
}
