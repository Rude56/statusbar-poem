package io.github.rude56.poemstatus.ui.screen.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.material.SegmentedColumn
import io.github.rude56.poemstatus.ui.component.material.SegmentedSwitchItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedListItem
import io.github.rude56.poemstatus.ui.component.material.TopBarBackButton

@Composable
fun StatusMaterial(
    state: StatusUiState,
    actions: StatusActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },

        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle(stringResource(R.string.status_section_switch))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Refresh,
                        title = stringResource(R.string.enable_module_title),
                        summary = stringResource(R.string.enable_module_summary),
                        checked = state.moduleEnabled,
                        onCheckedChange = actions.onToggleEnabled,
                    )
                },
            )

            SectionTitle(stringResource(R.string.status_section_check))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf(
                    {
                        CheckItem(
                            label = stringResource(R.string.status_framework),
                            value = if (state.connected) {
                                stringResource(R.string.status_connected)
                            } else {
                                stringResource(R.string.status_disconnected)
                            },
                            ok = state.connected,
                        )
                    },
                    {
                        CheckItem(
                            label = stringResource(R.string.status_scope),
                            value = stringResource(R.string.status_scope_value),
                            ok = state.connected,
                        )
                    },
                    {
                        CheckItem(
                            label = stringResource(R.string.status_module),
                            value = if (state.moduleEnabled) {
                                stringResource(R.string.status_enabled)
                            } else {
                                stringResource(R.string.status_disabled)
                            },
                            ok = state.moduleEnabled,
                        )
                    },
                    {

                        CheckItem(
                            label = stringResource(R.string.status_last_read),
                            value = when {
                                state.secondsAgo < 0 -> stringResource(R.string.status_never)
                                else -> stringResource(R.string.status_seconds_ago, state.secondsAgo)
                            },
                            ok = state.connected,
                        )
                    },
                ),
            )

            if (!state.connected) {
                HintCard(stringResource(R.string.status_hint))
            }

            SectionTitle(stringResource(R.string.status_section_about))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf(
                    { InfoItem(stringResource(R.string.status_version), state.versionName) },
                    { InfoItem(stringResource(R.string.status_device), state.deviceModel) },
                    { InfoItem(stringResource(R.string.status_system), state.systemVersion) },
                    {
                        InfoItem(
                            stringResource(R.string.status_android),
                            state.androidVersion,
                        )
                    },
                    {

                        InfoItem(
                            stringResource(R.string.status_screen_width),
                            stringResource(
                                R.string.status_screen_value,
                                state.screenWidthPx,
                                state.screenWidthDp,
                            ),
                        )
                    },
                ),
            )
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = actions.onRestartSystemUi,
                        leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.restart_systemui)) },
                        supportingContent = { Text(stringResource(R.string.restart_systemui_desc)) },
                    )
                },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CheckItem(label: String, value: String, ok: Boolean) {
    SegmentedListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
    )
}

@Composable
private fun InfoItem(label: String, value: String) {
    SegmentedListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    io.github.rude56.poemstatus.ui.component.material.SectionTitle(text = text)
}
@Composable
private fun HintCard(text: String) {
    io.github.rude56.poemstatus.ui.component.material.TonalCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
