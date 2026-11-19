package io.github.rude56.poemstatus.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.rude56.poemstatus.R

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val miuixIcon: ImageVector,
) {
    Home(
        R.string.tab_overview,
        Icons.Filled.Home,
        Icons.Outlined.Home,
        Icons.Rounded.Cottage,
    ),
    Library(
        R.string.tab_library,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.Rounded.MenuBook,
    ),
    Setting(
        R.string.tab_settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        Icons.Rounded.Settings,
    ),
}
