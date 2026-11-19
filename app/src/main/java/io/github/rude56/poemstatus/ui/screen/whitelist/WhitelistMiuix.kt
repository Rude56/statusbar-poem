package io.github.rude56.poemstatus.ui.screen.whitelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.BackButton
import io.github.rude56.poemstatus.ui.util.AppIconCache
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun WhitelistMiuix(
    state: WhitelistUiState,
    actions: WhitelistActions,
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
                    title = stringResource(R.string.whitelist_title),
                    navigationIcon = { BackButton(onClick = actions.onBack) },
                    scrollBehavior = scrollBehavior,
                )
            }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            InputField(
                                query = state.query,
                                onQueryChange = actions.onQueryChange,
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = { },
                                label = stringResource(R.string.whitelist_search),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (state.selected.isEmpty()) {
                                        stringResource(R.string.whitelist_empty_short)
                                    } else {
                                        stringResource(
                                            R.string.whitelist_selected_count,
                                            state.selected.size,
                                        )
                                    },
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (state.loading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InfiniteProgressIndicator(color = colorScheme.onBackground)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.whitelist_loading),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else if (state.noMatch) {
                    item {
                        Text(
                            text = stringResource(R.string.whitelist_no_match),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp, top = 24.dp),
                        )
                    }
                } else if (!state.hasApps) {

                    item {
                        Text(
                            text = stringResource(R.string.whitelist_no_apps),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp, top = 24.dp),
                        )
                    }
                } else {
                    items(items = state.apps, key = { it.packageName }) { app ->
                        val checked = app.packageName in state.selected
                        AppRow(
                            app = app,
                            checked = checked,
                            onToggle = { on -> actions.onToggle(app.packageName, on) },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppState.AppEntry, checked: Boolean) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(
        initialValue = AppIconCache.peek(app.packageName),
        app.packageName,
    ) {
        value = AppIconCache.load(context, app.packageName)
    }
    val loaded = icon
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(32.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (checked) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: AppState.AppEntry,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    BasicComponent(
        startAction = { AppIcon(app = app, checked = checked) },
        endActions = {
            Checkbox(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                onClick = { onToggle(!checked) },
            )
        },
        onClick = { onToggle(!checked) },
    ) {
        Text(
            text = app.label,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = app.packageName,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
