package io.github.rude56.poemstatus.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.material.SegmentedColumn
import io.github.rude56.poemstatus.ui.component.material.SegmentedListItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedRadioItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedSliderItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedSwitchItem
import io.github.rude56.poemstatus.ui.component.material.SectionTitle

@Composable
fun SettingsMaterial(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState),

        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle(stringResource(R.string.settings_section_appearance))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAppearance,
                            leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.appearance_title)) },
                            supportingContent = { Text(stringResource(R.string.settings_ui_mode_summary)) },
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

            SectionTitle(stringResource(R.string.settings_section_playback))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SliderItem(
                            icon = Icons.Rounded.Speed,
                            title = stringResource(R.string.speed_label),
                            summary = stringResource(R.string.speed_desc),
                            value = state.speedPx,
                            range = 20..600,
                            unit = stringResource(R.string.px_per_second),
                            onValueChange = actions.onSetSpeed,
                        )
                    },
                    {
                        SliderItem(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.clock_label),
                            summary = stringResource(R.string.clock_desc),
                            value = state.clockSeconds,
                            range = 0..600,
                            unit = stringResource(R.string.seconds),
                            onValueChange = actions.onSetClock,
                        )
                    },
                ),
            )

            SectionTitle(stringResource(R.string.order_label))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween,
                            ),
                        ) {
                            listOf(
                                false to stringResource(R.string.order_sequential),
                                true to stringResource(R.string.order_random),
                            ).forEachIndexed { index, (random, label) ->
                                val selected = state.randomOrder == random
                                ToggleButton(
                                    checked = selected,
                                    onCheckedChange = {
                                        if (it) actions.onSetRandomOrder(random)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { role = Role.RadioButton },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    },
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    },
                ),
            )

            SectionTitle(stringResource(R.string.settings_section_display))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.show_author),
                            checked = state.showAuthor,
                            onCheckedChange = actions.onSetShowAuthor,
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.show_source),
                            checked = state.showSource,
                            onCheckedChange = actions.onSetShowSource,
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.bob_enabled),
                            summary = stringResource(R.string.bob_desc),
                            checked = state.bobEnabled,
                            onCheckedChange = actions.onSetBob,
                        )
                    },
                ),
            )

            SectionTitle(stringResource(R.string.holes_title))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = actions.onOpenHoles,
                        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.holes_title)) },
                        supportingContent = { Text(stringResource(R.string.holes_desc)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (state.holeCount == 0) {
                                        stringResource(R.string.overview_not_set)
                                    } else {
                                        stringResource(R.string.settings_hole_count, state.holeCount)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                },
            )

            SectionTitle(stringResource(R.string.settings_section_pause))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.landscape_pause),
                            summary = stringResource(R.string.landscape_pause_desc),
                            checked = state.landscapePause,
                            onCheckedChange = actions.onSetLandscapePause,
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            title = stringResource(R.string.lockscreen_pause),
                            summary = stringResource(R.string.lockscreen_pause_desc),
                            checked = state.lockscreenPause,
                            onCheckedChange = actions.onSetLockscreenPause,
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenWhitelist,
                            leadingContent = { Icon(Icons.Filled.Smartphone, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.whitelist_title)) },
                            supportingContent = { Text(stringResource(R.string.whitelist_entry_desc)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (state.whitelistCount == 0) {
                                            stringResource(R.string.overview_none)
                                        } else {
                                            stringResource(R.string.overview_app_count, state.whitelistCount)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    },
                ),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.footer_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(16.dp + bottomInnerPadding))
        }
    }
}

@Composable
private fun SliderItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value.toString()) }
    val haptic = LocalHapticFeedback.current
    SegmentedSliderItem(
        icon = icon,
        title = title,
        summary = summary,
        value = value.toFloat(),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        valueText = "$value$unit",
        onClick = {
            draft = value.toString()
            editing = true
        },
        onValueChange = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onValueChange(it.toInt())
        },
    )

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(unit) },
                )
            },
            confirmButton = {
                TextButton(onClick = {

                    draft.toIntOrNull()?.let { onValueChange(it.coerceIn(range)) }
                    editing = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
