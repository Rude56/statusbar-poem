package io.github.rude56.poemstatus.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.compose.material.icons.rounded.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.WarningCard
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.theme.isInDarkTheme
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomeMiuix(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp = 0.dp,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
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
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                        StatusCardMiuix(
                            state = state,
                            onClick = actions.onOpenStatus,
                        )

                        AnimatedVisibility(
                            visible = !state.connected,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TutorialCard()
                                WarningCard(
                                    message = stringResource(R.string.overview_scope_hint),
                                    level = io.github.rude56.poemstatus.ui.component.WarningLevel.Notice,
                                )
                            }
                        }

                        if (state.connected) {
                            if (state.enabled && state.tone == HomeTone.NO_POEMS) {
                                WarningCard(message = stringResource(R.string.overview_empty))
                            }

                            ConfigSummaryCard(state)
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.about_title),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Info,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = actions.onOpenAbout,
                            )
                        }

                        Spacer(Modifier.height(8.dp + bottomInnerPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),

            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun StatusCardMiuix(
    state: HomeUiState,
    onClick: () -> Unit,
) {

    val isDark = isInDarkTheme()
    val container = when (state.tone) {
        HomeTone.OK -> if (isDark) Color(0xFF1E3A2E) else Color(0xFFDFFAE4)
        HomeTone.OFF -> colorScheme.surfaceContainer
        HomeTone.NOT_CONNECTED, HomeTone.NO_POEMS ->
            if (isDark) Color(0xFF3A1E1E) else Color(0xFFF8E2E2)
    }
    val content = when (state.tone) {
        HomeTone.OK -> if (isDark) Color(0xFFB8F0C8) else Color(0xFF111111)
        HomeTone.OFF -> colorScheme.onSurface
        HomeTone.NOT_CONNECTED, HomeTone.NO_POEMS ->
            if (isDark) Color(0xFFF7C0C0) else Color(0xFF111111)
    }

    val iconColor = when (state.tone) {
        HomeTone.OK -> Color(0xFF36D167)
        HomeTone.OFF -> colorScheme.onSurfaceVariantSummary
        HomeTone.NOT_CONNECTED, HomeTone.NO_POEMS -> if (isDark) {
            Color(0xFFFF5C5C)
        } else {
            Color(0xFFF72727)
        }
    }
    val icon = when (state.tone) {
        HomeTone.OK -> Icons.Rounded.CheckCircleOutline
        HomeTone.OFF -> Icons.Rounded.Cancel
        HomeTone.NOT_CONNECTED, HomeTone.NO_POEMS -> Icons.Rounded.Cancel
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container, contentColor = content),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 96.dp)
                .squircleClip(CardDefaults.CornerRadius),
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    imageVector = icon,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 14.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = statusTitle(state),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                        )
                        Spacer(Modifier.height(1.dp))
                    }
                    Text(
                        text = statusSubtitle(state),
                        fontSize = 15.sp,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }

        }
    }
}

@Composable
private fun statusTitle(state: HomeUiState): String = when (state.tone) {
    HomeTone.NOT_CONNECTED -> stringResource(R.string.status_not_connected)
    HomeTone.OFF -> stringResource(R.string.status_off)
    HomeTone.NO_POEMS -> stringResource(R.string.status_no_poems)
    HomeTone.OK -> stringResource(R.string.status_ok)
}

@Composable
private fun statusSubtitle(state: HomeUiState): String = when (state.tone) {
    HomeTone.NOT_CONNECTED -> stringResource(R.string.status_not_connected_desc)
    HomeTone.OFF -> stringResource(R.string.status_off_desc)
    HomeTone.NO_POEMS -> stringResource(R.string.status_no_poems_desc)

    HomeTone.OK -> stringResource(R.string.status_ok_desc, state.enabledPoems)
}

@Composable
private fun TutorialCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_getting_started),
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            StepText(
                1,
                stringResource(R.string.step_enable_title),
                stringResource(R.string.step_enable_desc),
            )
            StepText(
                2,
                stringResource(R.string.step_library_title),
                stringResource(R.string.step_library_desc),
            )
            StepText(
                3,
                stringResource(R.string.step_holes_title),
                stringResource(R.string.step_holes_desc),
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun StepText(index: Int, title: String, desc: String, bottomPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = desc,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ConfigSummaryCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SummaryText(
                label = stringResource(R.string.speed_label),
                value = stringResource(R.string.summary_speed_value, state.speedPx),
            )
            SummaryText(
                label = stringResource(R.string.clock_label),
                value = if (state.clockSeconds == 0) {
                    stringResource(R.string.summary_clock_zero)
                } else {
                    stringResource(R.string.summary_seconds_value, state.clockSeconds)
                },
            )
            SummaryText(
                label = stringResource(R.string.order_label),
                value = if (state.randomOrder) {
                    stringResource(R.string.order_random)
                } else {
                    stringResource(R.string.order_sequential)
                },
            )

            SummaryText(
                label = stringResource(R.string.summary_poems),
                value = stringResource(
                    R.string.library_summary,
                    state.totalPoems,
                    state.enabledPoems,
                ),
            )

            SummaryText(
                label = stringResource(R.string.holes_title),
                value = if (state.holeCount == 0) {
                    stringResource(R.string.overview_not_set)
                } else {
                    stringResource(
                        R.string.overview_hole_count,
                        state.holeCount,
                        state.holeEnabledCount,
                    )
                },
            )

            SummaryText(
                label = stringResource(R.string.whitelist_title),
                value = if (state.whitelistCount == 0) {
                    stringResource(R.string.overview_none)
                } else {
                    stringResource(R.string.overview_app_count, state.whitelistCount)
                },
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun SummaryText(label: String, value: String, bottomPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}
