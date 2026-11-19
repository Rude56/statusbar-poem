package io.github.rude56.poemstatus.ui.screen.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.miuix.BackButton
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.theme.keyColorLabelRes
import io.github.rude56.poemstatus.ui.theme.keyColorOptions
import io.github.rude56.poemstatus.ui.util.BlurredBar
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AppearanceMiuix(
    state: AppearanceUiState,
    actions: AppearanceActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val isDark = state.colorMode.isDark || (state.colorMode.isSystem && isSystemInDarkTheme())
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.appearance_title),
                    navigationIcon = { BackButton(onClick = actions.onBack) },
                    scrollBehavior = scrollBehavior,
                )
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

                    Spacer(Modifier.height(12.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_ui_mode),
                            summary = stringResource(R.string.settings_ui_mode_summary),
                            items = UiModeLabels,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_ui_mode),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            selectedIndex = if (state.uiMode == UiMode.Material.value) 0 else 1,
                            onSelectedIndexChange = actions.onSetUiMode,
                        )
                    }

                    val themeItems = listOf(
                        stringResource(R.string.settings_theme_mode_system),
                        stringResource(R.string.settings_theme_mode_light),
                        stringResource(R.string.settings_theme_mode_dark),
                    )
                    TabRow(
                        tabs = themeItems,
                        selectedTabIndex = (if (state.colorMode.isMonet) {
                            state.colorMode.value - 3
                        } else {
                            state.colorMode.value
                        }).coerceIn(0, 2),
                        onTabSelected = { index ->
                            actions.onSetColorMode(
                                when (index) {
                                    0 -> io.github.rude56.poemstatus.ui.theme.ColorMode.SYSTEM
                                    1 -> io.github.rude56.poemstatus.ui.theme.ColorMode.LIGHT
                                    else -> io.github.rude56.poemstatus.ui.theme.ColorMode.DARK
                                },
                            )
                        },
                    )

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_monet),
                            summary = stringResource(R.string.settings_monet_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Wallpaper,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_monet),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.miuixMonet,
                            onCheckedChange = actions.onSetMiuixMonet,
                        )

                        AnimatedVisibility(visible = state.miuixMonet) {
                            Column {
                                val colorItems = listOf(stringResource(R.string.settings_key_color_default)) +
                                    keyColorLabelRes.map { stringResource(it) }
                                val colorValues = listOf(0) + keyColorOptions

                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_key_color),
                                    items = colorItems,
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Colorize,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_key_color),
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                    selectedIndex = colorValues.indexOf(state.keyColor).takeIf { it >= 0 } ?: 0,
                                    onSelectedIndexChange = { index ->
                                        actions.onSetKeyColor(colorValues[index])
                                    },
                                )

                                AnimatedVisibility(visible = state.keyColor != 0) {
                                    Column {
                                        val styles = PaletteStyle.entries
                                        OverlayDropdownPreference(
                                            title = stringResource(R.string.settings_color_style),
                                            startAction = {
                                                Icon(
                                                    Icons.Rounded.Style,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = stringResource(R.string.settings_color_style),
                                                    tint = colorScheme.onBackground,
                                                )
                                            },
                                            items = styles.map { it.name },
                                            selectedIndex = styles.indexOf(state.paletteStyle).coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                actions.onSetColorStyle(styles[index].name)
                                            },
                                        )

                                        val specs = ColorSpec.SpecVersion.entries
                                        OverlayDropdownPreference(
                                            title = stringResource(R.string.settings_color_spec),
                                            startAction = {
                                                Icon(
                                                    Icons.Rounded.DesignServices,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    contentDescription = stringResource(R.string.settings_color_spec),
                                                    tint = colorScheme.onBackground,
                                                )
                                            },
                                            items = specs.map { it.name },
                                            selectedIndex = specs.indexOf(state.colorSpec).coerceAtLeast(0),
                                            onSelectedIndexChange = { index ->
                                                actions.onSetColorSpec(specs[index].name)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_blur),
                            summary = stringResource(R.string.settings_enable_blur_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_enable_blur),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.enableBlur,
                            onCheckedChange = actions.onSetEnableBlur,
                        )

                        SwitchPreference(
                            title = stringResource(R.string.settings_floating_bottom_bar),
                            summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.CallToAction,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_floating_bottom_bar),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.enableFloatingBottomBar,
                            onCheckedChange = actions.onSetEnableFloatingBottomBar,
                        )

                        AnimatedVisibility(visible = state.enableFloatingBottomBar) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_enable_glass),
                                summary = stringResource(R.string.settings_enable_glass_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.WaterDrop,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(R.string.settings_enable_glass),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = state.enableFloatingBottomBarBlur,
                                onCheckedChange = actions.onSetEnableFloatingBottomBarBlur,
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_predictive_back),
                            summary = stringResource(R.string.settings_enable_predictive_back_summary),
                            checked = state.enablePredictiveBack,
                            onCheckedChange = actions.onSetEnablePredictiveBack,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_swipe_dismiss),
                            summary = stringResource(R.string.settings_enable_swipe_dismiss_summary),
                            checked = state.enableSwipeDismiss,
                            onCheckedChange = actions.onSetEnableSwipeDismiss,
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_page_scale),
                                    fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.headline1.fontSize,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${(state.pageScale * 100).toInt()}%",
                                    fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Slider(
                                value = state.pageScale,
                                onValueChange = actions.onSetPageScale,
                                valueRange = 0.8f..1.1f,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(color: Color, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(color))
}

private data class PreviewColors(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

@Composable
private fun previewColorScheme(keyColor: Int, isDark: Boolean, paletteStyle: PaletteStyle): PreviewColors {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = if (keyColor == 0) {
        val base = if (isDark) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
        com.materialkolor.rememberDynamicColorScheme(
            seedColor = Color.Unspecified,
            isDark = isDark,
            style = paletteStyle,
            primary = base.primary,
            secondary = base.secondary,
            tertiary = base.tertiary,
            neutral = base.surface,
            neutralVariant = base.surfaceVariant,
            error = base.error,
        )
    } else {
        com.materialkolor.rememberDynamicColorScheme(
            seedColor = Color(keyColor),
            isDark = isDark,
            style = paletteStyle,
        )
    }
    return PreviewColors(
        background = scheme.background,
        surface = scheme.surface,
        surfaceContainer = scheme.surfaceContainer,
        primary = scheme.primary,
        primaryContainer = scheme.primaryContainer,
        onSurface = scheme.onSurface,
        onSurfaceVariant = scheme.onSurfaceVariant,
        outline = scheme.outline,
    )
}
