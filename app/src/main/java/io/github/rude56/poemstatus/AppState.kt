package io.github.rude56.poemstatus

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 极简的全局状态桥：把 SharedPreferences 里的配置映射成 Compose 可观察状态。
 * 只有设置界面用它；SystemUI 进程完全不加载本类（本类只在 MainActivity 里被引用，Hook 侧不引用）。
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
     * 保存诗库：诗库的唯一写入口，所有增删改排序都必须走这里。
     *
     * 必须 copy()：{@code config.value} 里放的是同一个对象实例，原地改字段再赋回去时
     * {@code mutableStateOf} 的默认策略 {@code structuralEqualityPolicy} 认为新旧值相等，不会触发重组。
     */
    fun persistPoems(context: Context, list: List<PoemItem>) {
        val next = config.value.copy()
        next.poemsJson = PoemItem.listToJson(list)
        next.poemsSeeded = true
        poems.value = list
        persist(context, next)
    }
}
