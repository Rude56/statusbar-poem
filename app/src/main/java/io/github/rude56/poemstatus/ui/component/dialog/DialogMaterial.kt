package io.github.rude56.poemstatus.ui.component.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource

@Composable
fun ConfirmDialogMaterial(
    visuals: ConfirmDialogVisuals,
    showDialog: MutableState<Boolean>,
    onResult: (ConfirmResult) -> Unit,
) {
    if (!showDialog.value) return

    AlertDialog(
        onDismissRequest = { onResult(ConfirmResult.Canceled) },
        title = { Text(visuals.title) },
        text = visuals.content?.let { { Text(it) } },
        shape = MaterialTheme.shapes.extraLarge,
        confirmButton = {
            TextButton(onClick = { onResult(ConfirmResult.Confirmed) }) {
                Text(visuals.confirm ?: stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(ConfirmResult.Canceled) }) {
                Text(visuals.dismiss ?: stringResource(android.R.string.cancel))
            }
        },
    )
}
