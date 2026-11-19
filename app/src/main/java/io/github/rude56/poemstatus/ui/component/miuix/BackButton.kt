package io.github.rude56.poemstatus.ui.component.miuix

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import io.github.rude56.poemstatus.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            MiuixIcons.Back,
            contentDescription = stringResource(R.string.back),
            tint = colorScheme.onBackground,
            modifier = Modifier.graphicsLayer { if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f },
        )
    }
}
