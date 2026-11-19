package io.github.rude56.poemstatus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.ui.HoleDraftHolder
import io.github.rude56.poemstatus.ui.HoleFrameOverlay
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.component.bottombar.BottomBar
import io.github.rude56.poemstatus.ui.component.bottombar.LocalMainPagerState
import io.github.rude56.poemstatus.ui.component.bottombar.MainPagerConfig
import io.github.rude56.poemstatus.ui.component.bottombar.MainPagerState
import io.github.rude56.poemstatus.ui.component.bottombar.SideRail
import io.github.rude56.poemstatus.ui.component.bottombar.rememberMainPagerState
import io.github.rude56.poemstatus.ui.component.bottombar.useNavigationRail
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.navigation3.Navigator
import io.github.rude56.poemstatus.ui.navigation3.Route
import io.github.rude56.poemstatus.ui.navigation3.rememberNavigator
import io.github.rude56.poemstatus.ui.screen.about.AboutScreen
import io.github.rude56.poemstatus.ui.screen.appearance.AppearanceScreen
import io.github.rude56.poemstatus.ui.screen.edit.HoleEditScreen
import io.github.rude56.poemstatus.ui.screen.edit.PoemEditScreen
import io.github.rude56.poemstatus.ui.screen.holes.CutoutScreen
import io.github.rude56.poemstatus.ui.screen.home.HomeScreen
import io.github.rude56.poemstatus.ui.screen.library.LibraryScreen
import io.github.rude56.poemstatus.ui.screen.settings.SettingsScreen
import io.github.rude56.poemstatus.ui.screen.status.StatusScreen
import io.github.rude56.poemstatus.ui.screen.whitelist.WhitelistScreen
import io.github.rude56.poemstatus.ui.theme.LocalColorMode
import io.github.rude56.poemstatus.ui.theme.LocalEnableBlur
import io.github.rude56.poemstatus.ui.theme.LocalEnableFloatingBottomBar
import io.github.rude56.poemstatus.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.rude56.poemstatus.ui.theme.TemplateTheme
import io.github.rude56.poemstatus.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowBackground()
        setContent {
            val viewModel = viewModel<UiPrefsViewModel>()
            val prefs by viewModel.state.collectAsStateWithLifecycle()
            val uiMode = prefs.uiMode
            val darkMode = prefs.appSettings.colorMode.isDark ||
                (prefs.appSettings.colorMode.isSystem && isSystemInDarkTheme())
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator()
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, prefs.pageScale) {
                Density(systemDensity.density * prefs.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalColorMode provides prefs.appSettings.colorMode.value,
                LocalEnableBlur provides prefs.enableBlur,
                LocalEnableFloatingBottomBar provides prefs.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides prefs.enableFloatingBottomBarBlur,
                LocalUiMode provides uiMode,
            ) {
                TemplateTheme(appSettings = prefs.appSettings, uiMode = uiMode) {
                    val swipeDismiss = if (prefs.enableSwipeDismiss) {
                        NavSwipeDirection.LeftToRight
                    } else {
                        NavSwipeDirection.None
                    }

                    val mainScreenEntry = @Composable {
                        MainScreen()
                    }

                    val navDisplay = @Composable {
                        Box(Modifier.fillMaxSize()) {
                            NavDisplay(
                                backStack = navigator.backStack,
                                effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                                onBack = { navigator.pop() },
                            ) {

                                entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) { mainScreenEntry() }
                                entry<Route.Status>(swipeDismiss = swipeDismiss) { StatusScreen() }
                                entry<Route.Appearance>(swipeDismiss = swipeDismiss) { AppearanceScreen() }
                                entry<Route.Holes>(swipeDismiss = swipeDismiss) { CutoutScreen() }
                                entry<Route.HoleEdit>(swipeDismiss = swipeDismiss) { key ->
                                    HoleEditScreen(slot = (key as Route.HoleEdit).slot)
                                }

                                entry<Route.HoleNew>(swipeDismiss = swipeDismiss) {
                                    HoleEditScreen(slot = null)
                                }
                                entry<Route.Whitelist>(swipeDismiss = swipeDismiss) { WhitelistScreen() }
                                entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
                                entry<Route.PoemEdit>(swipeDismiss = swipeDismiss) { key ->
                                    PoemEditScreen(poemId = (key as Route.PoemEdit).poemId)
                                }
                            }

                            val topRoute = navigator.backStack.lastOrNull()
                            if (topRoute == Route.Holes || topRoute is Route.HoleEdit ||
                                topRoute == Route.HoleNew
                            ) {
                                HoleFrameOverlay(
                                    holes = HoleRects.parse(AppState.config.value.holesJson),

                                    draft = HoleDraftHolder.draft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                )
                            }
                        }
                    }

                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold { navDisplay() }
                        UiMode.Miuix -> Scaffold { navDisplay() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        AppState.reload(this)
    }
}

@Composable
fun MainScreen() {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState, animatePageChanges = !useNavigationRail)
    val uiMode = LocalUiMode.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {

        mainPagerState.syncPage()
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navigator)
    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier.then(
                        if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        },
                    ),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = 1,
                    overscrollEffect = null,
                    flingBehavior = PagerDefaults.flingBehavior(state = mainPagerState.pagerState),
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(bottomInnerPadding = bottomInnerPadding)
                        1 -> LibraryScreen(bottomInnerPadding = bottomInnerPadding)
                        2 -> SettingsScreen(bottomInnerPadding = bottomInnerPadding)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold {
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets),
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold { _ ->
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets),
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main &&
                navController.backStackSize() == 1 &&
                mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        },
    )
}

private fun MainActivity.applyWindowBackground() {
    val prefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    val colorModeValue = prefs.getInt("color_mode", 0)
    val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    val dark = when (colorModeValue) {
        1, 4 -> false
        2, 5, 6 -> true
        else -> systemDark
    }
    window.setBackgroundDrawable(
        android.graphics.drawable.ColorDrawable(
            if (dark) 0xFF141218.toInt() else 0xFFFFFBFE.toInt(),
        ),
    )
}
