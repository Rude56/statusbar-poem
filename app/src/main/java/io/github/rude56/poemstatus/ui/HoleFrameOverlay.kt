package io.github.rude56.poemstatus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.ui.theme.holeColor

@Composable
fun HoleFrameOverlay(
    holes: List<HoleRects.Hole>,
    modifier: Modifier = Modifier,
    draft: HoleDraft? = null,
) {
    if (draft == null && holes.isEmpty()) return
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

            if (draft != null) {
                holes.forEach { hole ->
                    if (!hole.valid() || hole.slot == draft.slot) return@forEach
                    val l = hole.left.toFloat()
                    val r = hole.right.toFloat()
                    if (r <= l) return@forEach
                    val other = holeColor(hole.slot)
                    drawRect(
                        color = other.copy(alpha = 0.10f),
                        topLeft = Offset(l, 0f),
                        size = Size(r - l, h),
                    )
                    drawRect(
                        color = other.copy(alpha = 0.35f),
                        topLeft = Offset(l, 0f),
                        size = Size(r - l, h),
                        style = Stroke(width = strokeW),
                    )
                }
                val l = draft.left
                val r = draft.right
                val color = holeColor(draft.slot)
                when {

                    l != null && r != null && r > l -> {
                        drawRect(
                            color = color.copy(alpha = 0.28f),
                            topLeft = Offset(l.toFloat(), 0f),
                            size = Size((r - l).toFloat(), h),
                        )
                        drawRect(
                            color = color,
                            topLeft = Offset(l.toFloat(), 0f),
                            size = Size((r - l).toFloat(), h),
                            style = Stroke(width = strokeW),
                        )
                    }

                    l != null && r == null -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(l.toFloat() - strokeW / 2f, 0f),
                            size = Size(strokeW, h),
                        )
                    }

                    r != null && l == null -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(r.toFloat() - strokeW / 2f, 0f),
                            size = Size(strokeW, h),
                        )
                    }

                    l != null && r != null -> {
                        drawRect(
                            color = color,
                            topLeft = Offset(l.toFloat() - strokeW / 2f, 0f),
                            size = Size(strokeW, h),
                        )
                        drawRect(
                            color = color,
                            topLeft = Offset(r.toFloat() - strokeW / 2f, 0f),
                            size = Size(strokeW, h),
                        )
                    }

                    else -> Unit
                }
                return@Canvas
            }

            holes.forEach { hole ->
                if (!hole.valid()) return@forEach
                val left = hole.left.toFloat()
                val right = hole.right.toFloat()
                if (right <= left) return@forEach

                val color = holeColor(hole.slot)

                val fillAlpha = if (hole.enabled) 0.28f else 0.10f
                val strokeAlpha = if (hole.enabled) 1f else 0.35f

                drawRect(
                    color = color.copy(alpha = fillAlpha),
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, h),
                )

                drawRect(
                    color = color.copy(alpha = strokeAlpha),
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, h),
                    style = Stroke(width = strokeW),
                )
            }
        }
    }
}
data class HoleDraft(
    val slot: Int,
    val left: Int?,
    val right: Int?,
)

object HoleDraftHolder {
    var draft by mutableStateOf<HoleDraft?>(null)
}
