package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.BackButton
import io.github.rude56.poemstatus.ui.component.miuix.SuperEditArrow
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.theme.holeColor
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HoleEditMiuix(
    state: HoleEditUiState,
    actions: HoleEditActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    color = barColor,
                    title = stringResource(
                        if (state.isNew) R.string.hole_add else R.string.hole_edit_title,
                    ),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onClick = actions.onCancel) },
                )
            }
        },
        popupHost = { },

        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .add(WindowInsets.ime),
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
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(holeColor(state.slot)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.hole_color_hint),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Card(modifier = Modifier.weight(1f)) {
                            SuperEditArrow(
                                title = stringResource(R.string.hole_left),
                                defaultValue = state.left.toIntOrNull() ?: -1,

                                onValueChange = { actions.onLeftChange(if (it < 0) "" else it.toString()) },
                            )
                        }
                        Card(modifier = Modifier.weight(1f)) {
                            SuperEditArrow(
                                title = stringResource(R.string.hole_right),
                                defaultValue = state.right.toIntOrNull() ?: -1,
                                onValueChange = { actions.onRightChange(if (it < 0) "" else it.toString()) },
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.hole_screen_width, state.screenWidth),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )

                    if (state.error != null) {
                        Text(
                            text = state.error,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }

                    TabRow(
                        tabs = listOf(
                            stringResource(R.string.hole_mode_wormhole),
                            stringResource(R.string.hole_mode_tunnel),
                        ),
                        selectedTabIndex = if (state.wormhole) 0 else 1,
                        onTabSelected = { index -> actions.onModeChange(index == 0) },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                        if (!state.isNew) {
                            TextButton(
                                text = stringResource(R.string.delete),
                                onClick = actions.onDelete,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(
                                    color = colorScheme.errorContainer,
                                    textColor = colorScheme.onErrorContainer,
                                ),
                            )
                        }
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = actions.onCancel,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.save),
                            onClick = actions.onSave,
                            modifier = Modifier.weight(1.4f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
