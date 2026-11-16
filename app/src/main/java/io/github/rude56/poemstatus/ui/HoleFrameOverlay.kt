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
 * 真实状态栏上的彩色框 —— <b>由 App 自己绘制</b>。
 *
 * <p><b>为什么不再走 SystemUI</b>：之前让 SystemUI 进程挂一个预览层，
 * 靠配置里的 {@code cutoutPreview} 标志跨进程通知。实测下来这条路不可靠：
 * 标志写进去了、SystemUI 也读到了新配置，但预览层就是挂不上
 * （日志里只有 {@code CONF apply}，没有 {@code POEM preview attach}）。
 * 跨进程的时序、前台判断、窗口层级每一个环节都可能出问题，而且很难排查。
 *
 * <p><b>现在的做法</b>：App 是 edge-to-edge 全屏的，它的窗口本来就延伸到状态栏下面。
 * 所以直接在 App 窗口的 <b>y = 0..状态栏高度</b> 这一段画彩色框，
 * 视觉上就正好压在真实状态栏上。状态栏图标由 SystemUI 在更上层绘制，
 * 因此框不会遮住图标 —— 这正是「标注」而不是「遮挡」。
 *
 * <p>优点：零跨进程、零权限、零延迟。改数值时框立刻跟着变。
 *
 * <p>框内<b>不标数字</b>：只用颜色区分。数字在这么窄的一条里既看不清、
 * 又会盖住状态栏图标；颜色和下方编辑卡里的色点一一对应，足够认出来。
 *
 * @param holes 洞列表（屏幕坐标，与 App 窗口坐标一致，因为都是全屏）
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
                // 颜色取自洞自己的槽位，不取列表下标 ——
                // 删掉别的洞、或者改边界导致重排，这个洞的颜色都不会变。
                val color = holeColor(hole.slot)

                // 淡填充：能看出范围，又不至于把底下的图标糊掉。
                drawRect(
                    color = color.copy(alpha = 0.28f),
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, h),
                )
                // 描边：边界要一眼看出，所以用实色。
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
