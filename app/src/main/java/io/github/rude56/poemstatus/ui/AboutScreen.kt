package io.github.rude56.poemstatus.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rude56.poemstatus.BuildConfig
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.ui.components.RowDivider
import io.github.rude56.poemstatus.ui.components.SectionHeader
import io.github.rude56.poemstatus.ui.components.SettingsCard

/**
 * 关于（二级页）：版本、作用域、隐私说明、常见问题 —— 「需要重启 SystemUI」「只对 SystemUI 生效」
 * 「不联网」这几条容易困惑的点直接写在这里。
 */
@Composable
fun AboutScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 用 adaptive-icon 的前景矢量图，而不是 @mipmap/ic_launcher：后者是 <adaptive-icon> 容器，
            // Compose 的 painterResource 只接受矢量或位图，直接加载会抛 IllegalArgumentException。
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        R.drawable.ic_launcher_foreground
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(stringResource(R.string.about_usage))
        SettingsCard {
            InfoBlock(
                title = stringResource(R.string.about_scope_title),
                body = stringResource(R.string.about_scope_body),
            )
            RowDivider()
            InfoBlock(
                title = stringResource(R.string.about_restart_title),
                body = stringResource(R.string.about_restart_body),
            )
            RowDivider()
            InfoBlock(
                title = stringResource(R.string.about_holes_title),
                body = stringResource(R.string.about_holes_body),
            )
        }

        SectionHeader(stringResource(R.string.about_privacy))
        SettingsCard {
            InfoBlock(
                title = stringResource(R.string.about_offline_title),
                body = stringResource(R.string.about_offline_body),
                icon = Icons.Filled.CheckCircle,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.about_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun InfoBlock(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Info,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
