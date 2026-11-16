package io.github.rude56.poemstatus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.ui.theme.holeColor

/**
 * 真实状态栏上的彩色框，由 App 自己绘制（零跨进程、零权限、零延迟）。App 是 edge-to-edge 全屏，
 * 窗口本就延伸到状态栏下面，直接在 y = 0..状态栏高度 画即可；框内不标数字，颜色与编辑卡色点一一对应。
 */
@Composable
fun HoleFrameOverlay(
    holes: List<HoleRects.Hole>,
    modifier: Modifier = Modifier,
) {
    if (holes.isEmpty()) return

    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
    if (statusBarPx <= 0f) return

    val barHeight = with(density) { statusBarPx.toDp() }

    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        Canvas(Modifier.fillMaxWidth().height(barHeight)) {
            val h = size.height
            val strokeW = 2.dp.toPx()

            holes.forEach { hole ->
                if (!hole.valid()) return@forEach
                val left = hole.left.toFloat()
                val right = hole.right.toFloat()
                if (right <= left) return@forEach
                // 颜色取自洞自己的槽位，不取列表下标 —— 删掉别的洞或改边界导致重排，这个洞的颜色都不会变。
                val color = holeColor(hole.slot)

                drawRect(
                    color = color.copy(alpha = 0.28f),
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, h),
                )
                drawRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, h),
                    style = Stroke(width = strokeW),
                )
            }
        }
    }
}
