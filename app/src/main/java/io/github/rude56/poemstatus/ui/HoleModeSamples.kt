package io.github.rude56.poemstatus.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PreviewClock
import io.github.rude56.poemstatus.R

/**
 * 洞的两种穿越方式样例：两个写死的同位置洞，各演示一种模式，不跟随用户配置。
 * 两行必须严格同步：时间与位置全部来自 [PreviewClock]，任何一行自己算时间或取整都会让画面对不上且不报错。
 * 白底黑字固定配色，不随主题变 —— 状态栏在浅色壁纸下就是白底黑字。
 */
@Composable
fun HoleModeSamples(modifier: Modifier = Modifier) {
    val text = stringResource(R.string.hole_preview_text)
    // 白底黑字：固定色，不随主题变。
    val bg = Color.White
    val fg = Color(0xFF1A1C1E)

    val density = LocalDensity.current
    // 两行必须共用同一支画笔测宽，否则文字宽度有细微差异、两行消失点不一致。
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            // 必须用 sp 不能用 dp：运行时画笔是 13 * scaledDensity（即 13sp），
            // 用 dp 时系统字体缩放 ≠ 1 预览就比真实偏小。
            textSize = with(density) { 13.sp.toPx() }
        }
    }
    val textWidth = remember(text, paint) { paint.measureText(text) }

    // 画布宽度由两行共同报告（取最小值，保证两边都不越界）。
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    // 洞宽（像素）：由画布宽度按 30%–62% 算出。
    val wormholeWidth = canvasWidth * (HOLE_RIGHT_FRAC - HOLE_LEFT_FRAC)

    // 一轮循环的终点：必须在两行都还有字的范围内，见 PreviewClock.cycleEnd。
    val cycleEnd = if (canvasWidth > 0f) {
        PreviewClock.cycleEnd(canvasWidth, textWidth, wormholeWidth)
    } else {
        1f
    }

    // 两行共用的进度。用 PreviewClock 按周期取模，永续循环。
    var progress by remember { mutableFloatStateOf(0.40f) }
    LaunchedEffect(cycleEnd) {
        // 用 withFrameNanos 按时间推进并节流到约 30fps，不能按帧数累加：
        // 每帧加固定增量会让速度跟着屏幕刷新率变（120Hz 与 60Hz 的周期不同）。
        var lastDraw = 0L
        var start = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (start == 0L) {
                // 从初值 progress 处起步，避免进页面第一眼跳一下。
                start = now - (progress * PreviewClock.CYCLE_NANOS).toLong()
            }
            if (now - lastDraw < 33_000_000L) continue   // 约 30fps
            lastDraw = now
            val p = PreviewClock.progressAt(now - start, PreviewClock.CYCLE_NANOS)
            // 把 [0,1) 映射到 [0, cycleEnd)：到点立刻回到 0，两行永远同时在场。
            progress = p * cycleEnd
        }
    }

    Column(modifier.fillMaxWidth()) {
        SampleRow(
            title = stringResource(R.string.hole_mode_tunnel),
            hint = stringResource(R.string.hole_preview_tunnel_hint),
            text = text,
            paint = paint,
            progress = progress,
            mode = HoleRects.Mode.TUNNEL,
            bg = bg,
            fg = fg,
            onWidth = { w -> if (canvasWidth == 0f || w < canvasWidth) canvasWidth = w },
        )
        Spacer(Modifier.height(14.dp))
        SampleRow(
            title = stringResource(R.string.hole_mode_wormhole),
            hint = stringResource(R.string.hole_preview_wormhole_hint),
            text = text,
            paint = paint,
            progress = progress,
            mode = HoleRects.Mode.WORMHOLE,
            bg = bg,
            fg = fg,
            onWidth = { w -> if (canvasWidth == 0f || w < canvasWidth) canvasWidth = w },
        )
    }
}

/** 固定洞的横向范围（占画布宽度的比例）。两行必须完全一致。 */
private const val HOLE_LEFT_FRAC = 0.30f
private const val HOLE_RIGHT_FRAC = 0.62f

@Composable
private fun SampleRow(
    title: String,
    hint: String,
    text: String,
    paint: Paint,
    progress: Float,
    mode: HoleRects.Mode,
    bg: Color,
    fg: Color,
    onWidth: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                // 34dp：比状态栏略高一点，够看清字，又不占太多竖向空间。
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg),
        ) {
            SampleCanvas(
                text = text,
                paint = paint,
                progress = progress,
                mode = mode,
                fg = fg,
                onWidth = onWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 样例画布：洞的位置按预览宽度比例固定（左 30%、右 62%），纵向满高。
 * 两行的洞矩形必须逐像素相同，两侧都用同一对 float 边界，不能各自取整。
 */
@Composable
private fun SampleCanvas(
    text: String,
    paint: Paint,
    progress: Float,
    mode: HoleRects.Mode,
    fg: Color,
    onWidth: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    paint.color = fg.toArgb()

    // 帧内零分配：洞对象与三个输出数组都提到 remember 里复用。
    val hole = remember {
        HoleRects.Hole(0, 0, HoleRects.Mode.WORMHOLE)
    }
    val segScreen = remember { FloatArray(2) }
    val segText = remember { FloatArray(2) }
    val segWidth = remember { FloatArray(2) }
    val holeList = remember { arrayListOf(hole) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 报告宽度：两行取最小值，保证共用同一个坐标系。
        onWidth(w)
        // 文字宽度用共用画笔测量，与时钟算 cycleEnd 时是同一个值。
        val textW = paint.measureText(text)
        // 轨道位置与运行时同一个公式（PreviewClock.trackLeft）。
        val left = PreviewClock.trackLeft(w, textW, progress)
        val right = left + textW

        // 固定洞：横向 30%–62%，纵向满高。两行用同一对边界，且不在这里取整
        // （虫洞分支需要的整数由分段算法自己处理，不能反过来影响隧道的裁剪边界）。
        val holeLeft = w * HOLE_LEFT_FRAC
        val holeRight = w * HOLE_RIGHT_FRAC

        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f

            if (mode == HoleRects.Mode.TUNNEL) {
                // 隧道：位置不动，洞里不画。
                val save = canvas.save()
                canvas.clipOutRect(holeLeft, 0f, holeRight, h)
                canvas.drawText(text, left, baseline, paint)
                canvas.restoreToCount(save)
            } else {
                // 虫洞：洞那段屏幕被抽掉，字到洞口瞬移到另一侧。
                holeList[0] = hole.withRange(holeLeft.toInt(), holeRight.toInt())
                val n = HoleRects.wormholeSegments(
                    holeList, left, right, 0, segScreen, segText, segWidth, 2,
                )
                if (n <= 0) {
                    canvas.drawText(text, left, baseline, paint)
                } else {
                    for (i in 0 until n) {
                        if (segWidth[i] <= 0f) continue
                        val sc = canvas.save()
                        canvas.clipRect(segScreen[i], 0f, segScreen[i] + segWidth[i], h)
                        canvas.translate(segScreen[i] - segText[i], 0f)
                        canvas.drawText(text, left, baseline, paint)
                        canvas.restoreToCount(sc)
                    }
                }
            }
        }

        // 洞口标记画在最上层，两种模式都用同一个样式，便于对齐比较。
        // 洞内用很淡的灰：白底上要能看出「这一段被处理过」，
        // 但不能抢走文字的对比度。
        drawRect(
            color = Color(0xFF1A1C1E).copy(alpha = 0.06f),
            topLeft = Offset(holeLeft, 0f),
            size = Size(holeRight - holeLeft, h),
        )
        drawRect(
            color = Color(0xFF1A1C1E).copy(alpha = 0.30f),
            topLeft = Offset(holeLeft, 0f),
            size = Size(1.5f, h),
        )
        drawRect(
            color = Color(0xFF1A1C1E).copy(alpha = 0.30f),
            topLeft = Offset(holeRight - 1.5f, 0f),
            size = Size(1.5f, h),
        )
    }
}
