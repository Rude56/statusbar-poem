package io.github.rude56.poemstatus.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(modifier: Modifier = Modifier) {
    val mainPagerState = LocalMainPagerState.current
    NavigationRail(
        modifier = modifier.windowInsetsPadding(
            WindowInsets.displayCutout.only(WindowInsetsSides.Start),
        ),
        color = MiuixTheme.colorScheme.surface,
    ) {
        BottomBarDestination.entries.forEachIndexed { index, destination ->
            NavigationRailItem(
                selected = mainPagerState.selectedPage == index,
                onClick = {
                    if (mainPagerState.selectedPage != index) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = destination.miuixIcon,
                label = stringResource(destination.label),
            )
        }
    }
}
