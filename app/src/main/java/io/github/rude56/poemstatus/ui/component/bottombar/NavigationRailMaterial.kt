package io.github.rude56.poemstatus.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.ui.component.bottombar.LocalMainPagerState

@Composable
fun NavigationRailMaterial(modifier: Modifier = Modifier) {
    val mainPagerState = LocalMainPagerState.current
    NavigationRail(
        modifier = modifier
            .width(80.dp)
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Start),
            ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BottomBarDestination.entries.forEachIndexed { index, destination ->
            val selected = mainPagerState.selectedPage == index
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) destination.filledIcon else destination.outlinedIcon,
                        contentDescription = stringResource(destination.label),
                    )
                },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}
