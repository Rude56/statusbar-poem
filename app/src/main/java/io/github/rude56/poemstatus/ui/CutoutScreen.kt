package io.github.rude56.poemstatus.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.components.EmptyState
import io.github.rude56.poemstatus.ui.components.SectionHeader
import io.github.rude56.poemstatus.ui.theme.holeColor
import kotlinx.coroutines.delay

/**
 * 洞（二级页）：卡片列表、点一下进编辑页、左右滑动删除、右下角加号新增，操作方式与「古诗库」一致。
 * 彩色框由 {@code PoemApp} 里的 {@link HoleFrameOverlay} 直接画在 App 窗口的状态栏区域上，进出本页即显示/消失。
 * 每个洞带一个槽位，槽位跟着洞走：删掉别的洞、改边界导致重排，这个洞的颜色都不会变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoutScreen(
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val cfg = AppState.config.value
    val screenW = context.resources.displayMetrics.widthPixels
    val holes = remember(cfg.holesJson) { HoleRects.parse(cfg.holesJson) }
    val snackbar = LocalSnackbar.current
    val maxReachedMsg = stringResource(R.string.hole_max_reached, HoleRects.MAX_HOLES)
    val noRoomMsg = stringResource(R.string.hole_no_room)

    var pendingDelete by remember { mutableStateOf<HoleRects.Hole?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 两个固定样例：先把「隧道 / 虫洞」讲清楚，再让用户配自己的洞。
            item(key = "samples") {
                Column {
                    SectionHeader(stringResource(R.string.hole_samples_title))
                    HoleModeSamples(Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.hole_legend_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            item(key = "header") {
                SectionHeader(stringResource(R.string.holes_title))
            }

            if (holes.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.holes_empty_title),
                        description = stringResource(R.string.holes_empty_desc),
                    )
                }
            } else {
                items(items = holes, key = { it.slot }) { hole ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                // 不直接删：先弹确认框，用户取消时行滑回去。
                                pendingDelete = hole
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    // 行高和诗库的卡片保持一致：两处都是「左滑删除」，
                                    // 高度不同会让同一次滑动的手感忽大忽小。
                                    .heightIn(min = 88.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.delete),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                    ) {
                        HoleCard(
                            hole = hole,
                            onClick = { onEdit(hole.slot) },
                        )
                    }
                }
            }
        }
    }

    // 新增按钮：与诗库的 FAB 同一个位置与样式。
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        ExtendedFloatingActionButton(
            onClick = {
                // 上限与「没地方放」都必须给用户反馈：否则点一下毫无反应，用户只会以为按钮坏了。
                if (holes.size >= HoleRects.MAX_HOLES) {
                    snackbar(maxReachedMsg)
                    return@ExtendedFloatingActionButton
                }
                val spot = HoleRects.findFreeSpot(holes, screenW, 100)
                if (spot == null) {
                    snackbar(noRoomMsg)
                    return@ExtendedFloatingActionButton
                }
                // 双保险：findFreeSpot 保证不重叠，这里也不允许把重叠的洞写进配置（重叠会让绘制层自相矛盾）。
                if (HoleRects.overlapsAny(holes, spot[0], spot[1], -1)) {
                    snackbar(noRoomMsg)
                    return@ExtendedFloatingActionButton
                }
                // 新洞取「当前没被占用的最小槽位」：已有洞的颜色一个都不会变。
                val slot = HoleRects.nextFreeSlot(holes)
                val next = cfg.copy()
                next.holesJson = HoleRects.encode(
                    holes + HoleRects.Hole(
                        spot[0], spot[1], HoleRects.Mode.WORMHOLE, slot,
                    )
                )
                AppState.persist(context, next)
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.hole_add)) },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.hole_delete_confirm)) },
            text = {
                Text(
                    stringResource(
                        R.string.hole_delete_confirm_desc,
                        target.left, target.right, target.width(),
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = cfg.copy()
                    next.holesJson = HoleRects.encode(
                        holes.filterNot { it.slot == target.slot }
                    )
                    AppState.persist(context, next)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** 单个洞的卡片：结构与诗库的卡片完全一致（色块 + 标题 + 副标题 + 箭头）。 */
@Composable
private fun HoleCard(
    hole: HoleRects.Hole,
    onClick: () -> Unit,
) {
    val color = holeColor(hole.slot)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 色块：与状态栏上真实框的颜色一一对应。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.hole_summary, hole.left, hole.right, hole.width()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(
                        if (hole.isWormhole()) R.string.hole_mode_wormhole
                        else R.string.hole_mode_tunnel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
