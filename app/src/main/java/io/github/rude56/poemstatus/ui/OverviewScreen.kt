package io.github.rude56.poemstatus.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.ConfigProvider
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.components.RowDivider
import io.github.rude56.poemstatus.ui.components.SectionHeader
import io.github.rude56.poemstatus.ui.components.SettingsCard
import kotlinx.coroutines.delay

/**
 * 概览页（一级页，打开应用看到的第一屏）：让用户看清现在开没开、开不了是缺什么。
 */
@Composable
fun OverviewScreen(context: Context) {
    val cfg = AppState.config.value
    val poems = AppState.poems.value
    val enabledPoems = poems.count { it.enabled }

    // LSPosed 连接状态：SystemUI 每 2 秒来读一次配置，这里也按 2 秒刷新即可。
    // 必须绑到 STARTED 生命周期：`LaunchedEffect { while(true) }` 只在组合销毁时才停，
    // 而 Activity 退到后台时组合还活着 —— 会变成「在后台每 2 秒醒一次、写 state、触发不可见的重组」。
    var secondsAgo by remember { mutableLongStateOf(-1L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val at = ConfigProvider.lastSystemUiCallAt()
                secondsAgo = if (at <= 0L) {
                    -1L
                } else {
                    (SystemClock.elapsedRealtime() - at) / 1000L
                }
                delay(2000)
            }
        }
    }
    val connected = secondsAgo in 0..10

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        MasterCard(
            enabled = cfg.enabled,
            subtitle = statusLine(cfg.enabled, poems.size, enabledPoems),
            onToggle = { on ->
                val next = cfg.copy()
                next.enabled = on
                AppState.persist(context, next)
            },
        )

        Spacer(Modifier.height(12.dp))
        ConnectionCard(
            connected = connected,
            secondsAgo = if (secondsAgo < 0) 0 else secondsAgo,
        )

        val blocker = blockerText(cfg.enabled, poems.size, enabledPoems)
        if (blocker != null) {
            Spacer(Modifier.height(12.dp))
            HintRow(blocker)
        }

        SectionHeader(stringResource(R.string.summary_title))
        ConfigSummaryCard(
            speedPx = cfg.speedPx,
            clockSeconds = cfg.clockSeconds,
            randomOrder = cfg.orderMode == PoemLibrary.ORDER_RANDOM,
            enabledPoems = enabledPoems,
            totalPoems = poems.size,
            holeCount = HoleRects.parse(cfg.holesJson).size,
            whitelistCount = cfg.whitelist.size,
        )

        if (!cfg.enabled) {
            SectionHeader(stringResource(R.string.overview_getting_started))
            SettingsCard {
                StepRow(
                    1,
                    stringResource(R.string.step_enable_title),
                    stringResource(R.string.step_enable_desc),
                )
                RowDivider()
                StepRow(
                    2,
                    stringResource(R.string.step_library_title),
                    stringResource(R.string.step_library_desc),
                )
                RowDivider()
                StepRow(
                    3,
                    stringResource(R.string.step_holes_title),
                    stringResource(R.string.step_holes_desc),
                )
            }
            Spacer(Modifier.height(12.dp))
            HintRow(stringResource(R.string.overview_scope_hint))
        }
    }
}

/**
 * 顶部大卡片：一眼看到「开没开」，整张卡都能点。
 * 开关放在标题行右侧、说明文字单独占满整行 —— 状态文案再长也不会被开关挤成两行。
 */
@Composable
private fun MasterCard(enabled: Boolean, subtitle: String, onToggle: (Boolean) -> Unit) {
    val accent = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = { onToggle(!enabled) },
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) Color(0xFF30A46C)
                            else MaterialTheme.colorScheme.outline
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (enabled) R.string.status_running else R.string.status_off
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.enable_module),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = accent.copy(alpha = 0.75f),
            )
        }
    }
}

/** 首次引导的一步。 */
@Composable
private fun StepRow(index: Int, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行带图标的说明文字。 */
@Composable
private fun HintRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


private fun statusLine(enabled: Boolean, total: Int, enabledCount: Int): String = when {
    !enabled -> "打开后，诗句会从状态栏右侧滚入"
    total == 0 -> "诗库是空的，先去添加一首"
    enabledCount == 0 -> "所有诗都被关掉了，去诗库开启几首"
    else -> "正在播放 $enabledCount 首诗，点击卡片可暂停"
}

/** 开关开着却播不了时，返回一句「缺什么」；正常时返回 null。 */
private fun blockerText(enabled: Boolean, total: Int, enabledCount: Int): String? = when {
    !enabled -> null
    total == 0 -> "诗库是空的，到「古诗库」添加或导入诗句后才会开始播放。"
    enabledCount == 0 -> "诗库里的诗都被关掉了，到「古诗库」打开至少一首。"
    else -> null
}
