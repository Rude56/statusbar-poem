package io.github.rude56.poemstatus.ui.screen.status

import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.BuildConfig
import io.github.rude56.poemstatus.R
import io.github.rude56.poemstatus.RestartHelper
import io.github.rude56.poemstatus.RestartResult
import io.github.rude56.poemstatus.ui.LocalUiMode
import io.github.rude56.poemstatus.ui.UiMode
import io.github.rude56.poemstatus.ui.navigation3.LocalNavigator
import io.github.rude56.poemstatus.ui.util.isConnected
import io.github.rude56.poemstatus.ui.util.readSecondsAgo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StatusScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var secondsAgo by remember { mutableLongStateOf(readSecondsAgo()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                secondsAgo = readSecondsAgo()
                delay(2000)
            }
        }
    }
    val connected = isConnected(secondsAgo)

    var restartInProgress by remember { mutableStateOf(false) }

    val metrics = context.resources.displayMetrics
    val screenWidthDp = String.format(
        java.util.Locale.US,
        "%.0f",
        metrics.widthPixels / metrics.density,
    )
    val state = StatusUiState(
        connected = connected,
        secondsAgo = secondsAgo,
        moduleEnabled = AppState.config.value.enabled,

        versionName = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),

        systemVersion = run {
            val miui = systemProp("ro.miui.ui.version.name")
            val hyperOs = systemProp("ro.mi.os.version.name")
            val romVersion = miui.ifEmpty { hyperOs }
            val base = "Android ${Build.VERSION.RELEASE}"
            if (romVersion.isEmpty()) base else "$base ($romVersion)"
        },
        androidVersion = "安全补丁 ${systemProp("ro.build.version.security_patch")}",
        screenWidthPx = metrics.widthPixels,
        screenWidthDp = screenWidthDp,
    )
    val actions = StatusActions(
        onBack = { navigator.pop() },

        onToggleEnabled = { on ->
            val next = AppState.config.value.copy()
            next.enabled = on
            AppState.persist(context, next)
        },
        onRestartSystemUi = {
            if (!restartInProgress) {
                restartInProgress = true
                scope.launch {
                    try {
                        val messageRes = when (RestartHelper.restartSystemUi(context)) {
                            RestartResult.SUCCESS -> null
                            RestartResult.ROOT_UNAVAILABLE -> R.string.restart_root_unavailable
                            RestartResult.ROOT_DENIED -> R.string.restart_root_denied
                            RestartResult.COMMAND_FAILED -> R.string.restart_failed
                        }
                        if (messageRes != null) {

                            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        restartInProgress = false
                    }
                }
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> StatusMiuix(state, actions)
        UiMode.Material -> StatusMaterial(state, actions)
    }
}

@Immutable
data class StatusUiState(

    val connected: Boolean,
    val secondsAgo: Long,
    val moduleEnabled: Boolean,
    val versionName: String,
    val deviceModel: String,
    val androidVersion: String,

    val systemVersion: String = "",
    val screenWidthPx: Int = 0,
    val screenWidthDp: String = "",
)

@Immutable
data class StatusActions(
    val onBack: () -> Unit,
    val onToggleEnabled: (Boolean) -> Unit,
    val onRestartSystemUi: () -> Unit,
)

private fun systemProp(name: String): String = try {
    val clazz = Class.forName("android.os.SystemProperties")
    clazz.getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
} catch (_: Throwable) {
    ""
}
