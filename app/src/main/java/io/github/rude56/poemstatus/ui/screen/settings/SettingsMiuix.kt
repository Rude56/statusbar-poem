package io.github.rude56.poemstatus.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.NumberInputDialog
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsMiuix(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp = 0.dp,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val sectionTitleMargin = PaddingValues(start = 28.dp, top = 20.dp, end = 28.dp)

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.tab_settings),
                    scrollBehavior = scrollBehavior,
                )
            }
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

                    SmallTitle(
                        text = stringResource(R.string.settings_section_appearance),
                        insideMargin = sectionTitleMargin,
                    )
                    Card(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.appearance_title),
                            summary = stringResource(R.string.settings_ui_mode_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = actions.onOpenAppearance,
                        )
                    }

                    SmallTitle(
                        text = stringResource(R.string.settings_section_playback),
                        insideMargin = sectionTitleMargin,
                    )
                    Card(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    ) {
                        // 滑条负责连续调节，点标题弹出输入框做精确输入 ——
                        // miuix 没有可输入数字的组件，只靠滑条没法输入精确值。
                        var showSpeedInput by remember { mutableStateOf(false) }
                        SliderPreference(
                            title = stringResource(R.string.speed_label),
                            summary = stringResource(R.string.speed_desc),
                            value = state.speedPx.toFloat(),
                            onValueChange = { actions.onSetSpeed(it.toInt()) },
                            onClick = { showSpeedInput = true },
                            holdDownState = showSpeedInput,
                            valueRange = 40f..480f,
                            valueText = stringResource(R.string.summary_speed_value, state.speedPx),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Speed,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                        )
                        NumberInputDialog(
                            show = showSpeedInput,
                            title = stringResource(R.string.speed_label),
                            value = state.speedPx,
                            range = 40..480,
                            unit = stringResource(R.string.px_per_second),
                            onDismissRequest = { showSpeedInput = false },
                            onConfirm = actions.onSetSpeed,
                        )
                        var showClockInput by remember { mutableStateOf(false) }
                        SliderPreference(
                            title = stringResource(R.string.clock_label),
                            summary = stringResource(R.string.clock_desc),
                            value = state.clockSeconds.toFloat(),
                            onValueChange = { actions.onSetClock(it.toInt()) },
                            onClick = { showClockInput = true },
                            holdDownState = showClockInput,
                            valueRange = 0f..600f,
                            valueText = if (state.clockSeconds == 0) {
                                stringResource(R.string.summary_clock_zero)
                            } else {
                                stringResource(R.string.summary_seconds_value, state.clockSeconds)
                            },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Lock,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                        )
                        NumberInputDialog(
                            show = showClockInput,
                            title = stringResource(R.string.clock_label),
                            value = state.clockSeconds,
                            range = 0..600,
                            unit = stringResource(R.string.seconds),
                            onDismissRequest = { showClockInput = false },
                            onConfirm = actions.onSetClock,
                        )
                    }

                    SmallTitle(
                        text = stringResource(R.string.order_label),
                        insideMargin = sectionTitleMargin,
                    )

                    TabRow(
                        tabs = listOf(
                            stringResource(R.string.order_sequential),
                            stringResource(R.string.order_random),
                        ),
                        selectedTabIndex = if (state.randomOrder) 1 else 0,
                        onTabSelected = { index ->
                            actions.onSetRandomOrder(index == 1)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    SmallTitle(
                        text = stringResource(R.string.settings_section_display),
                        insideMargin = sectionTitleMargin,
                    )
                    Card(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.show_author),
                            checked = state.showAuthor,
                            onCheckedChange = actions.onSetShowAuthor,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.show_source),
                            checked = state.showSource,
                            onCheckedChange = actions.onSetShowSource,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.bob_enabled),
                            summary = stringResource(R.string.bob_desc),
                            checked = state.bobEnabled,
                            onCheckedChange = actions.onSetBob,
                        )
                    }

                    SmallTitle(
                        text = stringResource(R.string.holes_title),
                        insideMargin = sectionTitleMargin,
                    )
                    Card(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.holes_title),
                            summary = stringResource(R.string.holes_desc),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Warning,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                Text(
                                    text = if (state.holeCount == 0) {
                                        stringResource(R.string.overview_not_set)
                                    } else {
                                        stringResource(R.string.settings_hole_count, state.holeCount)
                                    },
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            },
                            onClick = actions.onOpenHoles,
                        )
                    }

                    SmallTitle(
                        text = stringResource(R.string.settings_section_pause),
                        insideMargin = sectionTitleMargin,
                    )
                    Card(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.landscape_pause),
                            summary = stringResource(R.string.landscape_pause_desc),
                            checked = state.landscapePause,
                            onCheckedChange = actions.onSetLandscapePause,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.lockscreen_pause),
                            summary = stringResource(R.string.lockscreen_pause_desc),
                            checked = state.lockscreenPause,
                            onCheckedChange = actions.onSetLockscreenPause,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.whitelist_title),
                            summary = stringResource(R.string.whitelist_entry_desc),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Smartphone,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                Text(
                                    text = if (state.whitelistCount == 0) {
                                        stringResource(R.string.overview_none)
                                    } else {
                                        stringResource(R.string.overview_app_count, state.whitelistCount)
                                    },
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            },
                            onClick = actions.onOpenWhitelist,
                        )
                    }

                    Text(
                        text = stringResource(R.string.footer_offline),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp),
                    )

                    Spacer(Modifier.height(24.dp + bottomInnerPadding))
                }
            }
        }
    }
}
