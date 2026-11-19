package io.github.rude56.poemstatus.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.material.ExpressiveSwitch
import io.github.rude56.poemstatus.ui.component.material.SegmentedColumn
import io.github.rude56.poemstatus.ui.component.material.SegmentedListItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedSwitchItem
import io.github.rude56.poemstatus.ui.component.material.TonalCard

@Composable
fun HomeMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            StatusCard(
                state = state,
                onClick = actions.onOpenStatus,
            )

            AnimatedVisibility(
                visible = !state.connected,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorialColumn()

                    NoticeCard(
                        message = stringResource(R.string.overview_scope_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.connected) {
                if (state.enabled && state.tone == HomeTone.NO_POEMS) {
                    BlockerCard(stringResource(R.string.overview_empty))
                }

                SectionTitle(stringResource(R.string.summary_title))
                ConfigSummaryColumn(state)
            }

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAbout,
                            leadingContent = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            },
                            headlineContent = { Text(stringResource(R.string.about_title)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    },
                ),
            )

            Spacer(Modifier.height(16.dp + bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),

        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    onClick: () -> Unit,
) {
    val container = when (state.tone) {
        HomeTone.OK -> MaterialTheme.colorScheme.secondaryContainer
        HomeTone.OFF -> MaterialTheme.colorScheme.surfaceContainerHigh
        HomeTone.NOT_CONNECTED, HomeTone.NO_POEMS -> MaterialTheme.colorScheme.errorContainer
    }
    val content = MaterialTheme.colorScheme.contentColorFor(container)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.7f),
                    )
                    Text(
                        text = statusTitle(state),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
                Text(
                    text = statusSubtitle(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = content.copy(alpha = 0.7f),
            )
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
private fun TutorialColumn() {
    SectionTitle(stringResource(R.string.overview_getting_started))
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf(
            {
                StepItem(
                    1,
                    stringResource(R.string.step_enable_title),
                    stringResource(R.string.step_enable_desc),
                )
            },
            {
                StepItem(
                    2,
                    stringResource(R.string.step_library_title),
                    stringResource(R.string.step_library_desc),
                )
            },
            {
                StepItem(
                    3,
                    stringResource(R.string.step_holes_title),
                    stringResource(R.string.step_holes_desc),
                )
            },
        ),
    )
}

@Composable
private fun StepItem(index: Int, title: String, desc: String) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        ),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(desc) },
    )
}

@Composable
private fun ConfigSummaryColumn(state: HomeUiState) {
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf(
            {
                SummaryItem(
                    label = stringResource(R.string.speed_label),
                    value = stringResource(R.string.summary_speed_value, state.speedPx),
                )
            },
            {
                SummaryItem(
                    label = stringResource(R.string.clock_label),
                    value = if (state.clockSeconds == 0) {
                        stringResource(R.string.summary_clock_zero)
                    } else {
                        stringResource(R.string.summary_seconds_value, state.clockSeconds)
                    },
                )
            },
            {
                SummaryItem(
                    label = stringResource(R.string.order_label),
                    value = if (state.randomOrder) {
                        stringResource(R.string.order_random)
                    } else {
                        stringResource(R.string.order_sequential)
                    },
                )
            },
            {

                SummaryItem(
                    label = stringResource(R.string.summary_poems),
                    value = stringResource(
                        R.string.library_summary,
                        state.totalPoems,
                        state.enabledPoems,
                    ),
                )
            },
            {

                SummaryItem(
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
            },
            {

                SummaryItem(
                    label = stringResource(R.string.whitelist_title),
                    value = if (state.whitelistCount == 0) {
                        stringResource(R.string.overview_none)
                    } else {
                        stringResource(R.string.overview_app_count, state.whitelistCount)
                    },
                )
            },
        ),
    )
}

@Composable
private fun SummaryItem(label: String, value: String) {
    SegmentedListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                value,
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
private fun BlockerCard(message: String) {
    TonalCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun NoticeCard(message: String, modifier: Modifier = Modifier) {
    TonalCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun HintRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
