package io.github.rude56.poemstatus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 维护动作：重启 SystemUI（需要 root；无 root 时给出提示）。必须异步：root 管理器默认会弹授权框
 * 等用户点「允许」，{@code ProcessBuilder(...).waitFor()} 同步调用在主线程上会 ANR；整段跑在 IO 线程。
 */
object RestartHelper {

        /**
         * 请求重启 SystemUI；true 表示命令已发出（SystemUI 马上会被系统拉起）。
         * 必须异步：root 管理器默认弹授权框等用户点「允许」，同步调用在主线程上会 ANR。
         */
    suspend fun restartSystemUi(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cmds = arrayOf(
            arrayOf("su", "-c", "killall com.android.systemui"),
            arrayOf("su", "-c", "pkill -f com.android.systemui"),
        )
        for (cmd in cmds) {
            try {
                val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                p.inputStream.bufferedReader().use { it.readText() }
                if (p.waitFor() == 0) {
                    Log.i("SystemUI restart requested")
                    return@withContext true
                }
            } catch (t: Throwable) {
            }
        }
        Log.w("SystemUI restart failed (no root?)")
        false
    }
}
