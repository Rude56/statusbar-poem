package io.github.rude56.poemstatus.ui.screen.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.component.material.SegmentedColumn
import io.github.rude56.poemstatus.ui.component.material.SegmentedDropdownItem
import io.github.rude56.poemstatus.ui.component.material.SegmentedSwitchItem
import io.github.rude56.poemstatus.ui.component.material.TonalCard
import io.github.rude56.poemstatus.ui.component.material.TopBarBackButton
import io.github.rude56.poemstatus.ui.theme.ColorMode
import io.github.rude56.poemstatus.ui.theme.keyColorOptions

@Composable
fun AppearanceMaterial(
    state: AppearanceUiState,
    actions: AppearanceActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptic = LocalHapticFeedback.current
    val isDark = state.colorMode.isDark || (state.colorMode.isSystem && isSystemInDarkTheme())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
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
    ) { paddingValues ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            SectionTitle(stringResource(R.string.appearance_section_style))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Dashboard,
                        title = stringResource(R.string.settings_ui_mode),
                        summary = stringResource(R.string.settings_ui_mode_summary),
                        items = UiModeLabels,
                        selectedIndex = if (state.uiMode == UiMode.Material.value) 0 else 1,
                        onItemSelected = actions.onSetUiMode,
                    )
                },
            )

            SectionTitle(stringResource(R.string.appearance_section_color))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ColorButton(
                        color = Color.Unspecified,
                        isSelected = state.keyColor == 0,
                        isDark = isDark,
                        paletteStyle = state.paletteStyle,
                        colorSpec = state.colorSpec,
                        onClick = { actions.onSetKeyColor(0) },
                    )
                }
                items(keyColorOptions) { color ->
                    ColorButton(
                        color = Color(color),
                        isSelected = state.keyColor == color,
                        isDark = isDark,
                        paletteStyle = state.paletteStyle,
                        colorSpec = state.colorSpec,
                        onClick = { actions.onSetKeyColor(color) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(
                    text = stringResource(R.string.appearance_section_theme),

                    padded = false,
                )

                val options = listOf(
                    ColorMode.SYSTEM to stringResource(R.string.settings_theme_mode_system),
                    ColorMode.LIGHT to stringResource(R.string.settings_theme_mode_light),
                    ColorMode.DARK to stringResource(R.string.settings_theme_mode_dark),
                    ColorMode.DARK_AMOLED to stringResource(R.string.settings_theme_mode_amoled),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    options.forEachIndexed { index, (mode, label) ->
                        ToggleButton(
                            checked = state.colorMode == mode ||

                                (mode == ColorMode.DARK && state.colorMode == ColorMode.MONET_DARK),
                            onCheckedChange = {
                                if (it) {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    actions.onSetColorMode(mode)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ColorMode.SYSTEM -> Icons.Filled.Brightness4
                                    ColorMode.LIGHT -> Icons.Filled.Brightness7
                                    ColorMode.DARK -> Icons.Filled.Brightness3
                                    else -> Icons.Filled.Brightness1
                                },
                                contentDescription = label,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = state.keyColor != 0) {
                    SegmentedColumn(
                        modifier = Modifier.padding(top = 4.dp),
                        content = listOf(
                            {
                                val styles = PaletteStyle.entries
                                SegmentedDropdownItem(
                                    icon = Icons.Rounded.Style,
                                    title = stringResource(R.string.settings_color_style),
                                    items = styles.map { it.name },
                                    selectedIndex = styles.indexOf(state.paletteStyle).coerceAtLeast(0),
                                    onItemSelected = { index ->
                                        actions.onSetColorStyle(styles[index].name)
                                    },
                                )
                            },
                            {
                                val specs = ColorSpec.SpecVersion.entries
                                SegmentedDropdownItem(
                                    icon = Icons.Rounded.DesignServices,
                                    title = stringResource(R.string.settings_color_spec),
                                    items = specs.map { it.name },
                                    selectedIndex = specs.indexOf(state.colorSpec).coerceAtLeast(0),
                                    onItemSelected = { index ->
                                        actions.onSetColorSpec(specs[index].name)
                                    },
                                )
                            },
                        ),
                    )
                }

                SectionTitle(
                    text = stringResource(R.string.appearance_section_effect),
                    padded = false,
                )
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.BlurOn,
                                title = stringResource(R.string.settings_enable_blur),
                                summary = stringResource(R.string.settings_enable_blur_summary),
                                checked = state.enableBlur,
                                onCheckedChange = actions.onSetEnableBlur,
                            )
                        },
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Wallpaper,
                                title = stringResource(R.string.settings_enable_predictive_back),
                                summary = stringResource(R.string.settings_enable_predictive_back_summary),
                                checked = state.enablePredictiveBack,
                                onCheckedChange = actions.onSetEnablePredictiveBack,
                            )
                        },
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Style,
                                title = stringResource(R.string.settings_enable_swipe_dismiss),
                                summary = stringResource(R.string.settings_enable_swipe_dismiss_summary),
                                checked = state.enableSwipeDismiss,
                                onCheckedChange = actions.onSetEnableSwipeDismiss,
                            )
                        },
                    ),
                )
            }

            Spacer(
                Modifier.height(
                    16.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding(),
                ),
            )
        }
    }
}

@Composable
private fun PreviewBlock(color: Color, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color))
}

@Composable
private fun ColorButton(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colorScheme = if (color == Color.Unspecified) {
        val baseScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        rememberDynamicColorScheme(
            seedColor = Color.Unspecified,
            isDark = isDark,
            style = paletteStyle,
            specVersion = colorSpec,
            primary = baseScheme.primary,
            secondary = baseScheme.secondary,
            tertiary = baseScheme.tertiary,
            neutral = baseScheme.surface,
            neutralVariant = baseScheme.surfaceVariant,
            error = baseScheme.error,
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = color,
            isDark = isDark,
            style = paletteStyle,
            specVersion = colorSpec,
        )
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = colorScheme.primaryContainer,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                )
                drawArc(
                    color = colorScheme.tertiaryContainer,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                )
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f)
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, padded: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = if (padded) {
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        } else {
            Modifier.padding(top = 8.dp, bottom = 4.dp)
        },
    )
}
