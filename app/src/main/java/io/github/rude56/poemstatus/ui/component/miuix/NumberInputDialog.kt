package io.github.rude56.poemstatus.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 数值输入对话框：miuix 没有可输入数字的组件，用 OverlayDialog + TextField 自己拼。
 *
 * 输入过程中只保留数字，确认时按 [range] 收敛，取消则原样返回。
 */
@Composable
fun NumberInputDialog(
    show: Boolean,
    title: String,
    value: Int,
    range: IntRange,
    unit: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = "${range.first} - ${range.last}",
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) { mutableStateOf(value.toString()) }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = if (unit == null) {
                    null
                } else {
                    {
                        Text(
                            text = unit,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colorScheme.onSurfaceVariantActions,
                        )
                    }
                },
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        text = ""
                    } else if (newValue.all { it.isDigit() } && newValue.length <= 6) {
                        text = newValue
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val parsed = text.toIntOrNull() ?: value
                        onConfirm(parsed.coerceIn(range.first, range.last))
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}
