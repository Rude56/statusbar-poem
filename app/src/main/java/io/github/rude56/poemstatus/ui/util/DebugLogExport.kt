package io.github.rude56.poemstatus.ui.util

import android.content.Context
import android.net.Uri
import io.github.rude56.poemstatus.ConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 读 SystemUI 推回来的调试日志。
 *
 * SystemUI 写不了外部可读目录，日志由 provider 落在模块自己的 filesDir，
 * 这里直接读同一进程的文件，不需要 root，也不需要用户做任何操作。
 */
suspend fun readDebugLog(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val f = ConfigProvider.logFile(context)
        if (!f.exists() || f.length() == 0L) {
            return@withContext ""
        }
        f.readText()
    } catch (t: Throwable) {
        ""
    }
}

suspend fun writeTextTo(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(text.toByteArray())
                out.flush()
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

fun debugLogFileName(): String {
    val fmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
    return "poem-debug-${fmt.format(java.util.Date())}.txt"
}
