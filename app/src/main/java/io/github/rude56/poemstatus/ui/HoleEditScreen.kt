package io.github.rude56.poemstatus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.theme.holeColor

/**
 * 洞的编辑页（二级页）：全屏、避让键盘、底部「删除 / 取消 / 保存」，与「编辑诗句」结构一致。
 * 洞之间禁止重合：重合会让绘制层的「虫洞分段」和「隧道挖空」互相抵消，所以保存前拦住并指出撞到了哪个洞。
 */
@Composable
fun HoleEditScreen(
    slot: Int,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val cfg = AppState.config.value
    val holes = remember(cfg.holesJson) { HoleRects.parse(cfg.holesJson) }
    val initial = remember(slot, holes) { holes.firstOrNull { it.slot == slot } }

    // 洞可能在别处被删掉（例如返回时数据已变），此时直接退回列表。
    // 必须在 LaunchedEffect 里退，不能在组合期间直接调 onDone()：那会在 composition 过程中
    // 修改导航栈（写 mutableStateListOf），属于 Compose 明确禁止的行为。
    if (initial == null) {
        LaunchedEffect(slot) { onDone() }
        return
    }

    var left by remember { mutableStateOf(initial.left.toString()) }
    var right by remember { mutableStateOf(initial.right.toString()) }
    var wormhole by remember { mutableStateOf(initial.isWormhole()) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidMsg = stringResource(R.string.hole_invalid_range)
    val screenW = context.resources.displayMetrics.widthPixels

    fun save() {
        val l = left.toIntOrNull()
        val r = right.toIntOrNull()
        if (l == null || r == null || r <= l) {
            error = invalidMsg
            return
        }
        val clash = HoleRects.firstOverlap(holes, l, r, slot)
        if (clash != null) {
            error = context.getString(
                R.string.hole_overlap,
                clash.slot + 1,
                clash.left,
                clash.right,
            )
            return
        }
        val updated = holes.map {
            if (it.slot == slot) {
                HoleRects.Hole(
                    l, r,
                    if (wormhole) HoleRects.Mode.WORMHOLE else HoleRects.Mode.TUNNEL,
                    slot,
                )
            } else {
                it
            }
        }
        val next = cfg.copy()
        next.holesJson = HoleRects.encode(updated)
        AppState.persist(context, next)
        onDone()
    }

    fun delete() {
        val next = cfg.copy()
        next.holesJson = HoleRects.encode(holes.filterNot { it.slot == slot })
        AppState.persist(context, next)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(holeColor(slot))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.hole_color_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = left,
                onValueChange = { input ->
                    left = input.filter { it.isDigit() }.take(5)
                    error = null
                },
                label = { Text(stringResource(R.string.hole_left)) },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = right,
                onValueChange = { input ->
                    right = input.filter { it.isDigit() }.take(5)
                    error = null
                },
                label = { Text(stringResource(R.string.hole_right)) },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Text(
            stringResource(R.string.hole_screen_width, screenW),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (error != null) {
            Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column {
            Text(
                stringResource(R.string.hole_mode_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = wormhole,
                    onClick = { wormhole = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.hole_mode_wormhole)) }
                SegmentedButton(
                    selected = !wormhole,
                    onClick = { wormhole = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.hole_mode_tunnel)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    if (wormhole) R.string.hole_mode_wormhole_desc
                    else R.string.hole_mode_tunnel_desc
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { delete() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.delete))
            }
            TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = { save() }, modifier = Modifier.weight(1.4f)) {
                Text(stringResource(R.string.save))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
