package io.github.rude56.poemstatus.ui.screen.holes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import io.github.rude56.poemstatus.HoleRects
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.extended.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.BackButton
import io.github.rude56.poemstatus.ui.HoleModeSamples
import io.github.rude56.poemstatus.ui.component.miuix.WarningCard
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.theme.holeColor
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun CutoutMiuix(
    state: CutoutUiState,
    actions: CutoutActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val toast = ToastHolder.message
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            ToastHolder.clear()
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.holes_title),
                    navigationIcon = { BackButton(onClick = actions.onBack) },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = actions.onAdd,
                modifier = Modifier.padding(end = 12.dp, bottom = 24.dp),
            ) {
                Icon(
                    MiuixIcons.Add,
                    contentDescription = stringResource(R.string.hole_add),
                    tint = colorScheme.onPrimary,
                )
            }
        },
        popupHost = { },

        snackbarHost = {
            if (toast != null) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    WarningCard(message = toast)
                }
            }
        },

        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {

                item {
                    SmallTitle(text = stringResource(R.string.hole_samples_title))
                    HoleModeSamples(
                        modifier = Modifier.fillMaxWidth(),
                        titleStyle = TextStyle(
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = FontWeight.Medium,
                        ),
                        titleColor = colorScheme.onSurface,
                        hintStyle = TextStyle(fontSize = MiuixTheme.textStyles.body2.fontSize),
                        hintColor = colorScheme.onSurfaceVariantSummary,
                        holeColor = colorScheme.secondary,
                    )
                    Text(
                        text = stringResource(R.string.hole_legend_hint),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    )
                }

                item {
                    SmallTitle(text = stringResource(R.string.holes_title))
                    if (state.isEmpty) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = stringResource(R.string.holes_empty_title),
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.holes_empty_desc),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.holes.forEachIndexed { index, hole ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = if (hole.enabled) 1f else 0.45f },
                                ) {
                                    HoleRow(
                                        hole = hole,
                                        index = index,
                                        onClick = { actions.onEdit(hole.slot) },
                                        onToggle = { on -> actions.onToggle(hole, on) },
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun HoleRow(
    hole: HoleRects.Hole,
    index: Int,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    BasicComponent(
        startAction = {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        holeColor(hole.slot).copy(alpha = if (hole.enabled) 0.22f else 0.10f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    fontSize = 12.sp,
                    color = if (hole.enabled) {
                        holeColor(hole.slot)
                    } else {
                        colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        },

        endActions = {
            Switch(checked = hole.enabled, onCheckedChange = onToggle)
        },
        onClick = onClick,
    ) {
        Text(
            text = stringResource(R.string.hole_summary, hole.left, hole.right),
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = if (hole.enabled) colorScheme.onSurface else colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(
                if (hole.isWormhole()) R.string.hole_mode_wormhole
                else R.string.hole_mode_tunnel
            ),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}
