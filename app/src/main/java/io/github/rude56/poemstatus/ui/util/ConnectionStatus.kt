package io.github.rude56.poemstatus.ui.util

import android.os.SystemClock
import io.github.rude56.poemstatus.ConfigProvider

/** SystemUI 最近一次调用 provider 距今多少秒；从未调用过返回 -1。 */
fun readSecondsAgo(): Long {
    val at = ConfigProvider.lastSystemUiCallAt()
    return if (at <= 0L) -1L else (SystemClock.elapsedRealtime() - at) / 1000L
}

/**
 * 是否已连接。
 *
 * <p>只是「最近 N 秒内有过调用」这一个判据会让 App 刚启动时闪一下「未激活」：
 * lastSystemUiCallAt 是进程级静态值，App 冷启时要么是 0，要么是上一次会话留下的旧时间，
 * 于是首帧必然判成断开，等下一次轮询才变回已连接。
 *
 * <p>所以这里改成「只有明确过期才算断开」：
 * - 从未调用过（-1）：返回 null，表示还不知道，UI 不应显示「未激活」
 * - 在窗口内有过调用：已连接
 * - 超过窗口：才认为断开
 *
 * <p>窗口必须明显大于模块的空闲轮询间隔（SystemUiController.IDLE_POLL_MS = 30s），
 * 否则模块明明活着、只是刚好在两次轮询之间，就会被误判成断开。
 * 取 30s 的 4 倍，留足余量。
 */
fun connectionState(secondsAgo: Long): Boolean? = when {
    secondsAgo < 0L -> null
    secondsAgo <= CONNECTED_WINDOW_SECONDS -> true
    else -> false
}

/** 兼容旧调用点：只知道「是不是明确断开」时用这个。 */
fun isConnected(secondsAgo: Long): Boolean = connectionState(secondsAgo) != false

/** 模块空闲时每 30 秒才问一次 provider，窗口必须比它大得多。 */
private const val CONNECTED_WINDOW_SECONDS = 120L
