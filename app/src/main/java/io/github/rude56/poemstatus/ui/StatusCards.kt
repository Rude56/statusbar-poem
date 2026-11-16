package io.github.rude56.poemstatus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R

/**
 * LSPosed 连接状态。
 *
 * <p>这是全模块<b>最容易被误解</b>的一件事：装好了、开关也开了，但状态栏没反应。
 * 原因几乎总是下面三种之一：
 * <ol>
 *   <li>LSPosed 里没启用这个模块；</li>
 *   <li>启用了但作用域没勾「系统界面」；</li>
 *   <li>都对了，但装完没重启 SystemUI，跑的仍是旧代码。</li>
 * </ol>
 * 以前用户只能靠「有没有反应」来猜，现在这里直接给出结论。
 *
 * @param connected  SystemUI 最近是否来读过配置
 * @param secondsAgo 距离上次读取过了多少秒（connected 为 false 时无意义）
 */
@Composable
fun ConnectionCard(
    connected: Boolean,
    secondsAgo: Long,
    modifier: Modifier = Modifier,
) {
    val accent = if (connected) Color(0xFF30A46C) else MaterialTheme.colorScheme.error
    val container = if (connected) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.errorContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (connected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (connected) R.string.lsp_connected else R.string.lsp_not_connected
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (connected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (connected) {
                        stringResource(R.string.lsp_connected_desc, secondsAgo)
                    } else {
                        stringResource(R.string.lsp_not_connected_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                )
                if (!connected) {
                    Spacer(Modifier.height(8.dp))
                    // 三步排查，按「最常见的先写」排序。
                    listOf(
                        R.string.lsp_step_enable,
                        R.string.lsp_step_scope,
                        R.string.lsp_step_restart,
                    ).forEachIndexed { index, res ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(res),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配置速览：把当前最关键的几项设置压成一行，只读、不跳转。
 *
 * <p>为什么不放快捷入口：底部导航已经有「古诗库」和「设置」，
 * 主页再放一遍只是重复。这里给的是<b>一眼确认「现在是什么配置」</b>，
 * 比如突然觉得诗滚太快，扫一眼就知道速度是 191 还是 600。
 */
@Composable
fun ConfigSummaryCard(
    speedPx: Int,
    clockSeconds: Int,
    randomOrder: Boolean,
    enabledPoems: Int,
    totalPoems: Int,
    holeCount: Int,
    whitelistCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(
                stringResource(R.string.summary_speed),
                stringResource(R.string.summary_speed_value, speedPx),
            )
            SummaryRow(
                stringResource(R.string.summary_clock),
                if (clockSeconds == 0) stringResource(R.string.summary_clock_zero)
                else stringResource(R.string.summary_seconds_value, clockSeconds),
            )
            SummaryRow(
                stringResource(R.string.summary_order),
                stringResource(
                    if (randomOrder) R.string.order_random else R.string.order_sequential
                ),
            )
            SummaryRow(
                stringResource(R.string.summary_poems),
                if (totalPoems == 0) stringResource(R.string.overview_empty)
                else stringResource(R.string.summary_poems_value, enabledPoems, totalPoems),
            )
            SummaryRow(
                stringResource(R.string.summary_holes),
                if (holeCount == 0) stringResource(R.string.overview_not_set)
                else stringResource(R.string.overview_hole_count, holeCount),
            )
            if (whitelistCount > 0) {
                SummaryRow(
                    stringResource(R.string.whitelist_title),
                    stringResource(R.string.overview_app_count, whitelistCount),
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
