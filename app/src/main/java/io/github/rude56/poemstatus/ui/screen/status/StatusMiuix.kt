package io.github.rude56.poemstatus.ui.screen.status

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.BackButton
import io.github.rude56.poemstatus.ui.component.miuix.WarningCard
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun StatusMiuix(
    state: StatusUiState,
    actions: StatusActions,
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
                    title = stringResource(R.string.status_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onClick = actions.onBack) },
                )            }
        },
        popupHost = { },

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
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                        Card(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(R.string.enable_module_title),
                                summary = stringResource(R.string.enable_module_summary),
                                checked = state.moduleEnabled,
                                onCheckedChange = actions.onToggleEnabled,
                            )
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.status_section_check),
                                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                                CheckRow(
                                    label = stringResource(R.string.status_framework),
                                    value = if (state.connected) {
                                        stringResource(R.string.status_connected)
                                    } else {
                                        stringResource(R.string.status_disconnected)
                                    },
                                    ok = state.connected,
                                )
                                CheckRow(
                                    label = stringResource(R.string.status_scope),
                                    value = stringResource(R.string.status_scope_value),
                                    ok = state.connected,
                                )
                                CheckRow(
                                    label = stringResource(R.string.status_module),
                                    value = if (state.moduleEnabled) {
                                        stringResource(R.string.status_enabled)
                                    } else {
                                        stringResource(R.string.status_disabled)
                                    },
                                    ok = state.moduleEnabled,
                                )

                                CheckRow(
                                    label = stringResource(R.string.status_last_read),
                                    value = when {
                                        state.secondsAgo < 0 -> stringResource(R.string.status_never)
                                        else -> stringResource(R.string.status_seconds_ago, state.secondsAgo)
                                    },
                                    ok = state.connected,
                                    bottomPadding = 0.dp,
                                )
                            }
                        }

                        if (!state.connected) {
                            WarningCard(
                                message = stringResource(R.string.status_hint),
                                level = io.github.rude56.poemstatus.ui.component.WarningLevel.Notice,
                            )
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.status_section_about),
                                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                                InfoRow(
                                    stringResource(R.string.status_version),
                                    state.versionName,
                                )
                                InfoRow(
                                    stringResource(R.string.status_device),
                                    state.deviceModel,
                                )
                                InfoRow(
                                    stringResource(R.string.status_system),
                                    state.systemVersion,
                                )
                                InfoRow(
                                    stringResource(R.string.status_android),
                                    state.androidVersion,
                                )
                                InfoRow(
                                    stringResource(R.string.status_screen_width),
                                    stringResource(
                                        R.string.status_screen_value,
                                        state.screenWidthPx,
                                        state.screenWidthDp,
                                    ),
                                    bottomPadding = 0.dp,
                                )
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(
                                title = stringResource(R.string.restart_systemui),
                                summary = stringResource(R.string.restart_systemui_desc),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = actions.onRestartSystemUi,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    value: String,
    ok: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
            color = if (ok) colorScheme.primary else colorScheme.error,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
