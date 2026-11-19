package io.github.rude56.poemstatus.ui.screen.holes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import io.github.rude56.poemstatus.ui.component.material.ExpressiveSwitch
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.HoleModeSamples
import io.github.rude56.poemstatus.ui.component.material.SegmentedItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedListItem
import io.github.rude56.poemstatus.ui.component.material.TonalCard
import io.github.rude56.poemstatus.ui.component.material.TopBarBackButton
import io.github.rude56.poemstatus.ui.screen.home.ToastHolder
import io.github.rude56.poemstatus.ui.theme.holeColor
import kotlinx.coroutines.delay

@Composable
fun CutoutMaterial(
    state: CutoutUiState,
    actions: CutoutActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val toast = ToastHolder.message
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            ToastHolder.clear()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.holes_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.hole_add)) },
            )
        },

        contentWindowInsets = WindowInsets.safeDrawing,

        snackbarHost = {
            if (toast != null) {
                Box(Modifier.padding(16.dp)) {
                    ToastCard(message = toast)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {

            SectionTitle(stringResource(R.string.hole_samples_title))
            HoleModeSamples(
                modifier = Modifier.padding(horizontal = 16.dp),
                titleStyle = MaterialTheme.typography.titleMedium,
                titleColor = MaterialTheme.colorScheme.onSurface,
                hintStyle = MaterialTheme.typography.bodySmall,
                hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                holeColor = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hole_legend_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionTitle(stringResource(R.string.holes_title))

            if (state.isEmpty) {
                EmptyHoles()
            } else {

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.holes.forEachIndexed { index, hole ->
                        HoleRow(
                            hole = hole,
                            index = index,
                            onClick = { actions.onEdit(hole.slot) },
                            onToggle = { on -> actions.onToggle(hole, on) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (hole.enabled) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                .copy(alpha = 0.55f)
        },
    ) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        holeColor(hole.slot).copy(alpha = if (hole.enabled) 0.22f else 0.10f),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hole.enabled) {
                        holeColor(hole.slot)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.hole_summary, hole.left, hole.right),
                color = if (hole.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    if (hole.isWormhole()) R.string.hole_mode_wormhole
                    else R.string.hole_mode_tunnel
                ),
            )
        },

        trailingContent = {
            ExpressiveSwitch(
                checked = hole.enabled,
                onCheckedChange = onToggle,
            )
        },
    )
    }
}

@Composable
private fun EmptyHoles() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.holes_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.holes_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    io.github.rude56.poemstatus.ui.component.material.SectionTitle(text = text)
}

@Composable
private fun ToastCard(message: String, modifier: Modifier = Modifier) {
    TonalCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}
