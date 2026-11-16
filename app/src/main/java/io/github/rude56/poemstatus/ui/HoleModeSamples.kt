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
 * 洞的两种穿越方式 —— 两个固定样例。
 *
 * <p>为什么固定而不跟随用户配置：这两种模式的差别只发生在「一个字跨过洞口的一瞬」，
 * 用户配的洞可能只有 30px 宽、或者压根没配，那样根本看不清区别。
 * 所以这里用两个写死的、位置相同的洞，各演示一种模式，让用户先看懂「隧道」和「虫洞」
 * 分别是什么，再决定自己要用哪个。
 *
 * <p>配色：<b>白底黑字</b>。不跟随深色主题 —— 状态栏在浅色壁纸下就是白底黑字，
 * 这样样例看起来才像真的状态栏，而不是一块黑色演示条。
 *
 * <p><b>两行必须严格同步</b>，否则对比没有意义。全部时间与位置都来自
 * {@link PreviewClock}：同一个进度、同一条轨道、同一个洞矩形。
 * 任何一行自己算时间或自己取整，都会让两块画面对不上，而且不会报错。
 */
@Composable
fun HoleModeSamples(modifier: Modifier = Modifier) {
    val text = stringResource(R.string.hole_preview_text)
    // 白底黑字：固定色，不随主题变。
    val bg = Color.White
    val fg = Color(0xFF1A1C1E)

    val density = LocalDensity.current
    // 两行必须用<b>同一支画笔</b>测量文字宽度，否则文字宽度可能差一点点，
    // 两行的消失点就不一致了。
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            // 必须用 sp，不能用 dp：运行时画笔是 13 * scaledDensity（即 13sp）。
            // 用 dp 时，只要系统字体缩放 ≠ 1，预览就比真实偏小 —— 所见非所得。
            textSize = with(density) { 13.sp.toPx() }
        }
    }
    val textWidth = remember(text, paint) { paint.measureText(text) }

    // 画布宽度由两行共同报告（取最小值，保证两边都不越界）。
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    // 洞宽（像素）：由画布宽度按 30%–62% 算出。
    val wormholeWidth = canvasWidth * (HOLE_RIGHT_FRAC - HOLE_LEFT_FRAC)

    // 一轮循环的终点：必须在<b>两行都还有字</b>的范围内。
    //
    // 以前进度按整条轨道 [0,1] 循环，而虫洞行会提前 19% 就走空 ——
    // 每轮有 2.7 秒只有隧道行还在滚，两行对不上。见 PreviewClock.cycleEnd。
    val cycleEnd = if (canvasWidth > 0f) {
        PreviewClock.cycleEnd(canvasWidth, textWidth, wormholeWidth)
    } else {
        1f
    }

    // 两行共用的进度。用 PreviewClock 按周期取模，永续循环。
    //
    // 以前进度被夹取到 1 且起点从不重置：跑到 14 秒后永久停在末尾，
    // 两行都是空的，再也没法对比。
    var progress by remember { mutableFloatStateOf(0.40f) }
    LaunchedEffect(cycleEnd) {
        // 按「时间」推进，且节流到约 30fps。
        //
        // 旧实现是「每个 vsync 加一个固定小量」：120Hz 屏上每秒写 120 次 state、
        // 两块 Canvas 各重绘一次；而且那个固定增量是按 120Hz 推算的「一轮 14 秒」，
        // 在 60Hz 设备上会变成 28 秒 —— 速度跟着刷新率变，这本身也不对。
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
 * 样例画布。
 *
 * <p>洞的位置按预览宽度的比例写死（左 30%、右 62%），比原来更宽：
 * 洞太窄时「字被吞掉」和「字被挡住」看起来几乎一样，加宽才能看出差别。
 *
 * <p><b>两行的洞矩形必须逐像素相同</b>。以前隧道分支直接用 float 边界，
 * 虫洞分支却把它们 {@code toInt()} 之后再交给分段算法 ——
 * 两条边界各差最多 1px，于是「洞的左沿」在两行里不在同一列，
 * 对比时看到的差异有一部分其实是这个取整误差。
 * 现在两边都用同一对边界。
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
    // 以前每次重组都 listOf(hole) + FloatArray(2) × 3。
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
        // 文字宽度用<b>共用画笔</b>测量，与时钟算 cycleEnd 时是同一个值。
        val textW = paint.measureText(text)
        // 轨道位置与运行时同一个公式（PreviewClock.trackLeft）。
        val left = PreviewClock.trackLeft(w, textW, progress)
        val right = left + textW

        // 固定洞：横向 30%–62%（更宽，便于看出吞字/遮挡的差别），纵向满高。
        // 两行用<b>同一对</b>边界，且不在这里取整（虫洞分支内部需要的整数
        // 由分段算法自己处理，不能让它反过来影响隧道的裁剪边界）。
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
