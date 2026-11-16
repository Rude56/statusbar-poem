package io.github.rude56.poemstatus

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 极简的全局状态桥：把 SharedPreferences 里的配置映射成 Compose 可观察状态。
 *
 * <p>只有设置界面用它；SystemUI 进程完全不加载本类（LSPosed 只会加载被 Hook 的
 * 那个包的 APK 里的类，而本类只在 MainActivity 里被引用，且 Hook 侧代码不引用它）。
 */
object AppState {

    val config = mutableStateOf(PoemConfig())
    val poems = mutableStateOf<List<PoemItem>>(emptyList())
    val installedApps = mutableStateOf<List<AppEntry>>(emptyList())
    val bootstrapped = mutableStateOf(false)

    data class AppEntry(val packageName: String, val label: String)

    fun reload(context: Context) {
        val loaded = ConfigStore.load(context)
        config.value = loaded
        poems.value = PoemLibrary.load(loaded.poemsSeeded, loaded.poemsJson)
        bootstrapped.value = true
    }

    /** 统一保存入口：写 prefs → 递增 revision → 通知 SystemUI 立即重读。 */
    fun persist(context: Context, next: PoemConfig) {
        ConfigStore.save(context, next, true)
        ConfigStore.notifyConfigChanged(context)
        config.value = next
    }

    /**
     * 保存诗库。
     *
     * <p><b>必须 copy()</b>：{@code config.value} 里放的是同一个对象实例，
     * 如果原地改字段再赋回去，{@code mutableStateOf} 的默认策略是
     * {@code structuralEqualityPolicy} —— 新旧值 {@code equals} 为真，
     * <b>不会触发任何重组</b>。以前这里没有读 {@code config.poemsJson} 的地方，
     * 所以症状是隐性的；一旦将来有页面读它，就会变成「改了不刷新」这种极难查的 bug。
     */
    fun persistPoems(context: Context, list: List<PoemItem>) {
        val next = config.value.copy()
        next.poemsJson = PoemItem.listToJson(list)
        next.poemsSeeded = true
        poems.value = list
        persist(context, next)
    }
}
