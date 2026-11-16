package io.github.rude56.poemstatus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 维护动作：重启 SystemUI（需要 root；无 root 时给出提示）。
 *
 * 必须异步：{@code ProcessBuilder(...).waitFor()} 会阻塞调用线程，而 root 管理器
 * （Magisk / KernelSU / APatch）默认会弹授权框等用户点「允许」，同步调用在主线程上会 ANR。
 * 整段跑在 IO 线程，主线程只等一个挂起结果。
 */
object RestartHelper {

    /**
     * 请求重启 SystemUI。
     *
     * @return true 表示命令已发出（SystemUI 马上会被系统拉起）
     */
    suspend fun restartSystemUi(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cmds = arrayOf(
            arrayOf("su", "-c", "killall com.android.systemui"),
            arrayOf("su", "-c", "pkill -f com.android.systemui"),
        )
        for (cmd in cmds) {
            try {
                val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                // 读掉输出，避免子进程因管道写满而卡住。
                p.inputStream.bufferedReader().use { it.readText() }
                if (p.waitFor() == 0) {
                    Log.i("SystemUI restart requested")
                    return@withContext true
                }
            } catch (t: Throwable) {
                // 尝试下一个
            }
        }
        Log.w("SystemUI restart failed (no root?)")
        false
    }
}
