package io.github.rude56.poemstatus.ui.component.material

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String, padded: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = if (padded) {
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        } else {
            Modifier
        },
    )
}
