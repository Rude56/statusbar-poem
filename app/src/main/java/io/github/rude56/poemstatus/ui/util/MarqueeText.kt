package io.github.rude56.poemstatus.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rude56.poemstatus.MarqueeCycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    speedPxPerSecond: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cyclePauseMs: Long = MarqueeCycle.PAUSE_MILLIS,
) {
    val measurer = rememberTextMeasurer()
    val renderStyle = style.copy(textAlign = TextAlign.Left)
    val textWidthPx = remember(measurer, text, renderStyle) {
        measurer.measure(
            text = text,
            style = renderStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        ).size.width
    }
    val cycleText = remember(text) { MarqueeCycle.text(text) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var cycleWidthPx by remember(text, renderStyle, measurer) { mutableIntStateOf(0) }
    var offsetPx by remember(text, renderStyle, measurer) { mutableFloatStateOf(0f) }
    val shouldScroll = enabled && speedPxPerSecond > 0 &&
        containerWidthPx > 0 && textWidthPx > containerWidthPx
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(shouldScroll, text, cycleWidthPx, containerWidthPx, speedPxPerSecond, cyclePauseMs, lifecycleOwner) {
        offsetPx = 0f
        if (!shouldScroll || cycleWidthPx <= 0) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {

                offsetPx = 0f
                delay(cyclePauseMs.coerceAtLeast(0L))
                var travelled = 0f
                var lastFrame = withFrameNanos { it }
                while (isActive && travelled < cycleWidthPx) {
                    val now = withFrameNanos { it }
                    val elapsedMs = (now - lastFrame) / 1_000_000f
                    travelled = MarqueeCycle.advance(travelled, cycleWidthPx.toFloat(), speedPxPerSecond, elapsedMs)
                    offsetPx = -travelled
                    lastFrame = now
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = if (shouldScroll) offsetPx else 0f },
        ) {
            BasicText(
                text = if (shouldScroll) cycleText else text,
                style = renderStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                onTextLayout = { if (shouldScroll) cycleWidthPx = it.size.width },
            )
            if (shouldScroll) {
                BasicText(
                    text = cycleText,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = renderStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}
