package io.github.rude56.poemstatus.ui.screen.whitelist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WhitelistScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val cfg = AppState.config.value
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(AppState.installedApps.value) }
    var loading by remember { mutableStateOf(apps.isEmpty()) }
    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            loading = true
            apps = withContext(Dispatchers.IO) { loadApps(context) }
            AppState.installedApps.value = apps
            loading = false
        }
    }

    fun toggle(pkg: String, on: Boolean) {
        val next = cfg.copy()
        next.whitelist = if (on) (next.whitelist + pkg).distinct()
        else next.whitelist.filterNot { it == pkg }
        AppState.persist(context, next)
    }

    val selected = cfg.whitelist

    val filtered = remember(query, apps, selected) {
        val matched = apps.filter {
            query.isBlank() ||
                it.label.contains(query, true) ||
                it.packageName.contains(query, true)
        }
        val selectedSet = selected.toHashSet()
        matched.sortedWith(
            compareBy<AppState.AppEntry> { if (it.packageName in selectedSet) 0 else 1 }
                .thenBy { it.label.lowercase() }
        )
    }

    val state = WhitelistUiState(
        apps = filtered,
        selected = selected.toSet(),
        query = query,
        loading = loading,
        hasApps = apps.isNotEmpty(),
    )
    val actions = WhitelistActions(
        onBack = { navigator.pop() },
        onQueryChange = { query = it },
        onToggle = ::toggle,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> WhitelistMiuix(state, actions)
        UiMode.Material -> WhitelistMaterial(state, actions)
    }
}

private fun loadApps(context: Context): List<AppState.AppEntry> {
    val pm = context.packageManager
    val out = ArrayList<AppState.AppEntry>()
    try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val seen = HashSet<String>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val appInfo: ApplicationInfo? = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (t: Throwable) {
                null
            }
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
            out.add(AppState.AppEntry(pkg, label))
        }
    } catch (t: Throwable) {

    }
    if (out.isEmpty()) {
        try {
            for (pi in pm.getInstalledApplications(PackageManager.MATCH_ALL)) {
                if (pi.packageName == context.packageName) continue
                out.add(AppState.AppEntry(pi.packageName, pm.getApplicationLabel(pi).toString()))
            }
        } catch (t: Throwable) {

        }
    }
    return out.sortedBy { it.label.lowercase() }
}
