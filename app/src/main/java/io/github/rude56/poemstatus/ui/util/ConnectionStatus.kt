package io.github.rude56.poemstatus.ui.util

import android.os.SystemClock
import io.github.rude56.poemstatus.ConfigProvider

fun readSecondsAgo(): Long {
    val at = ConfigProvider.lastSystemUiCallAt()
    return if (at <= 0L) -1L else (SystemClock.elapsedRealtime() - at) / 1000L
}
fun isConnected(secondsAgo: Long): Boolean = secondsAgo in 0..10
