package io.github.rude56.poemstatus.ui.screen.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.component.material.ExpressiveScaffold
import io.github.rude56.poemstatus.ui.component.material.SegmentedColumn
import io.github.rude56.poemstatus.ui.component.material.SegmentedTextField
import io.github.rude56.poemstatus.ui.component.material.TopBarBackButton
import io.github.rude56.poemstatus.ui.component.material.expressiveTopAppBarColors

@Composable
fun PoemEditMaterial(
    state: PoemEditUiState,
    actions: PoemEditActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val title = if (state.isNew) {
        stringResource(R.string.poem_add_title)
    } else {
        stringResource(R.string.poem_edit_title)
    }

    ExpressiveScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { TopBarBackButton(onClick = actions.onCancel) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },

        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = actions.onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.poem_text)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.poem_text_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                minLines = 3,
                isError = state.error != null,
            )

            Text(
                text = state.error ?: stringResource(R.string.poem_text_counter, state.text.length),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SegmentedColumn(
                content = listOf(
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.poem_author),
                            value = state.author,
                            onValueChange = actions.onAuthorChange,
                            singleLine = true,
                        )
                    },
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.poem_source),
                            value = state.source,
                            onValueChange = actions.onSourceChange,
                            singleLine = true,
                        )
                    },
                ),
            )

            Text(
                text = stringResource(R.string.poem_author_source_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                if (!state.isNew) {
                    OutlinedButton(
                        onClick = actions.onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
                OutlinedButton(
                    onClick = actions.onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = actions.onSave,
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
