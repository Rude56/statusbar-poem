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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.PreviewClock
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.theme.isInDarkTheme

@Composable
fun HoleModeSamples(
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    hintStyle: TextStyle = MaterialTheme.typography.bodySmall,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    holeColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val text = stringResource(R.string.hole_preview_text)
    val isDark = isInDarkTheme()
    val bg = if (isDark) Color(0xFF141218) else Color.White
    val fg = if (isDark) Color(0xFFE3E2E6) else Color(0xFF1A1C1E)

    val density = LocalDensity.current

    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = with(density) { 13.sp.toPx() }
        }
    }
    val textWidth = remember(text, paint) { paint.measureText(text) }

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    val wormholeWidth = canvasWidth * (HOLE_RIGHT_FRAC - HOLE_LEFT_FRAC)

    val cycleEnd = if (canvasWidth > 0f) {
        PreviewClock.cycleEnd(canvasWidth, textWidth, wormholeWidth)
    } else {
        1f
    }

    var progress by remember { mutableFloatStateOf(0.40f) }
    LaunchedEffect(cycleEnd) {

        var lastDraw = 0L
        var start = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (start == 0L) {

                start = now - (progress * PreviewClock.CYCLE_NANOS).toLong()
            }
            if (now - lastDraw < 33_000_000L) continue
            lastDraw = now
            val p = PreviewClock.progressAt(now - start, PreviewClock.CYCLE_NANOS)
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
            titleStyle = titleStyle,
            titleColor = titleColor,
            hintStyle = hintStyle,
            hintColor = hintColor,
            holeColor = holeColor,
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
            titleStyle = titleStyle,
            titleColor = titleColor,
            hintStyle = hintStyle,
            hintColor = hintColor,
            holeColor = holeColor,
            onWidth = { w -> if (canvasWidth == 0f || w < canvasWidth) canvasWidth = w },
        )
    }
}

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
    titleStyle: TextStyle,
    titleColor: Color,
    hintStyle: TextStyle,
    hintColor: Color,
    holeColor: Color,
    onWidth: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = titleStyle,
            color = titleColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = hint,
            style = hintStyle,
            color = hintColor,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()

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
                holeColor = holeColor,
                onWidth = onWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SampleCanvas(
    text: String,
    paint: Paint,
    progress: Float,
    mode: HoleRects.Mode,
    fg: Color,
    holeColor: Color,
    onWidth: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    paint.color = fg.toArgb()

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
        onWidth(w)
        val textW = paint.measureText(text)
        val left = PreviewClock.trackLeft(w, textW, progress)
        val right = left + textW

        val holeLeft = w * HOLE_LEFT_FRAC
        val holeRight = w * HOLE_RIGHT_FRAC

        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f

            if (mode == HoleRects.Mode.TUNNEL) {
                val save = canvas.save()
                canvas.clipOutRect(holeLeft, 0f, holeRight, h)
                canvas.drawText(text, left, baseline, paint)
                canvas.restoreToCount(save)
            } else {

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

        drawRect(
            color = holeColor.copy(alpha = 0.28f),
            topLeft = Offset(holeLeft, 0f),
            size = Size(holeRight - holeLeft, h),
        )
        drawRect(
            color = holeColor,
            topLeft = Offset(holeLeft, 0f),
            size = Size(1.5f, h),
        )
        drawRect(
            color = holeColor,
            topLeft = Offset(holeRight - 1.5f, 0f),
            size = Size(1.5f, h),
        )
    }
}
