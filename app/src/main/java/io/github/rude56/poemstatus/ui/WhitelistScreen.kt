package io.github.rude56.poemstatus.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.ConfigCodec
import io.github.rude56.poemstatus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 白名单（二级页）：这些应用在前台时暂停滚动。
 *
 * <p>交互：
 * <ul>
 *   <li>顶部搜索框即时过滤；</li>
 *   <li>已选应用固定在列表最上方，方便取消；</li>
 *   <li>点整行即勾选，不必精确点中复选框。</li>
 * </ul>
 */
@Composable
fun WhitelistScreen(context: Context) {
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
    // 已选应用置顶：选完一个不用再翻回去找它，取消时也不用滚回上面。
    // 组内各自按名称排序，位置稳定（不会因为勾选而整体跳动）。
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

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.whitelist_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        // 已选摘要
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selected.isEmpty()) stringResource(R.string.whitelist_empty_short)
                else stringResource(R.string.whitelist_selected_count, selected.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty()) {
                TextButton(onClick = {
                    val next = cfg.copy()
                    next.whitelist = emptyList()
                    AppState.persist(context, next)
                }) {
                    Text(stringResource(R.string.whitelist_clear))
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.whitelist_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            items(items = filtered, key = { it.packageName }) { app ->
                val checked = selected.contains(app.packageName)
                AppRow(
                    label = app.label,
                    pkg = app.packageName,
                    checked = checked,
                    onToggle = { toggle(app.packageName, it) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    pkg: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 首字母头像，省去加载真实图标（避免一次读几百个 Drawable）。
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = onToggle)
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
        // 包可见性受限时退回全量列表。
    }
    if (out.isEmpty()) {
        try {
            for (pi in pm.getInstalledApplications(PackageManager.MATCH_ALL)) {
                if (pi.packageName == context.packageName) continue
                out.add(AppState.AppEntry(pi.packageName, pm.getApplicationLabel(pi).toString()))
            }
        } catch (t: Throwable) {
            // 忽略
        }
    }
    return out.sortedBy { it.label.lowercase() }
}
