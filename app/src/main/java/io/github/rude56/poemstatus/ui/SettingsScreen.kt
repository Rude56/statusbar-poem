package io.github.rude56.poemstatus.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PoemConfig
import io.github.rude56.poemstatus.PoemLibrary
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.RestartHelper
import io.github.rude56.poemstatus.ui.components.NavRow
import io.github.rude56.poemstatus.ui.components.RowDivider
import io.github.rude56.poemstatus.ui.components.SectionHeader
import io.github.rude56.poemstatus.ui.components.SegmentedRow
import io.github.rude56.poemstatus.ui.components.SettingsCard
import io.github.rude56.poemstatus.ui.components.SliderRow
import io.github.rude56.poemstatus.ui.components.SwitchRow
import kotlinx.coroutines.launch

/**
 * 设置页（一级页）。
 *
 * <p>分组顺序按「用户改动的可能性」排：播放 → 显示 → 暂停条件 → 高级 → 维护。
 * 每一项都是「标题 + 一句人话解释」，不需要看文档。
 */
@Composable
fun SettingsScreen(
    context: Context,
    onOpenHoles: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val cfg = AppState.config.value
    val holeCount = remember(cfg.holesJson) { HoleRects.parse(cfg.holesJson).size }
    val snackbar = LocalSnackbar.current
    val restartFailed = stringResource(R.string.restart_failed)
    val scope = rememberCoroutineScope()
    var restarting by remember { mutableStateOf(false) }

    fun persist(block: (PoemConfig) -> Unit) {
        val next = cfg.copy()
        block(next)
        AppState.persist(context, next)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // ---------- 播放 ----------
        SectionHeader(stringResource(R.string.section_playback))
        SettingsCard {
            SliderRow(
                title = stringResource(R.string.speed_label),
                subtitle = stringResource(R.string.speed_desc),
                value = cfg.speedPx,
                range = PoemConfig.SPEED_MIN..PoemConfig.SPEED_MAX,
                unit = stringResource(R.string.px_per_second),
            ) { v -> persist { it.speedPx = v } }
            RowDivider()
            SliderRow(
                title = stringResource(R.string.clock_label),
                subtitle = stringResource(R.string.clock_desc),
                value = cfg.clockSeconds,
                range = PoemConfig.CLOCK_MIN..PoemConfig.CLOCK_MAX,
                unit = stringResource(R.string.seconds),
            ) { v -> persist { it.clockSeconds = v } }
            RowDivider()
            SegmentedRow(
                title = stringResource(R.string.order_label),
                options = listOf(
                    stringResource(R.string.order_sequential),
                    stringResource(R.string.order_random),
                ),
                selectedIndex = if (cfg.orderMode == PoemLibrary.ORDER_RANDOM) 1 else 0,
            ) { index ->
                persist {
                    it.orderMode = if (index == 1) PoemLibrary.ORDER_RANDOM
                    else PoemLibrary.ORDER_SEQUENTIAL
                }
            }
        }

        // ---------- 显示 ----------
        SectionHeader(stringResource(R.string.section_display))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.show_author),
                subtitle = stringResource(R.string.show_author_desc),
                checked = cfg.showAuthor,
            ) { on -> persist { it.showAuthor = on } }
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.show_source),
                subtitle = stringResource(R.string.show_source_desc),
                checked = cfg.showSource,
            ) { on -> persist { it.showSource = on } }
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.bob_enabled),
                subtitle = stringResource(R.string.bob_desc),
                checked = cfg.bobEnabled,
            ) { on -> persist { it.bobEnabled = on } }
        }

        // ---------- 洞 ----------
        SectionHeader(stringResource(R.string.section_holes))
        SettingsCard {
            NavRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.holes_title),
                subtitle = stringResource(R.string.holes_desc),
                value = if (holeCount == 0) stringResource(R.string.overview_not_set)
                else stringResource(R.string.overview_hole_count, holeCount),
                onClick = onOpenHoles,
            )
        }

        // ---------- 暂停条件 ----------
        SectionHeader(stringResource(R.string.section_pause))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.landscape_pause),
                subtitle = stringResource(R.string.landscape_pause_desc),
                checked = cfg.landscapePause,
            ) { on -> persist { it.landscapePause = on } }
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.lockscreen_pause),
                subtitle = stringResource(R.string.lockscreen_pause_desc),
                checked = cfg.lockscreenPause,
            ) { on -> persist { it.lockscreenPause = on } }
            RowDivider()
            NavRow(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.whitelist_title),
                subtitle = stringResource(R.string.whitelist_entry_desc),
                value = if (cfg.whitelist.isEmpty()) stringResource(R.string.overview_none)
                else stringResource(R.string.overview_app_count, cfg.whitelist.size),
                onClick = onOpenWhitelist,
            )
        }

        // ---------- 维护 ----------
        SectionHeader(stringResource(R.string.section_maintenance))
        SettingsCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.restart_systemui_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (restarting) return@OutlinedButton
                        restarting = true
                        // root 授权框可能弹好几秒：绝不能同步等在主线程上（会 ANR）。
                        scope.launch {
                            val ok = RestartHelper.restartSystemUi(context)
                            restarting = false
                            if (!ok) snackbar(restartFailed)
                        }
                    },
                    enabled = !restarting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restart_systemui))
                }
            }
            RowDivider()
            NavRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_desc),
                onClick = onOpenAbout,
            )
        }

        // 底部一行小字：说明数据全部在本机。
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.footer_offline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
