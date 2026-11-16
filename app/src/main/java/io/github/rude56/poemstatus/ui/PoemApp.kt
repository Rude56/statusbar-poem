package io.github.rude56.poemstatus.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rude56.poemstatus.AppState
import io.github.rude56.poemstatus.HoleRects
import io.github.rude56.poemstatus.R
import kotlinx.coroutines.launch

/**
 * 页面导航骨架：一级页是底部三个 tab（平级、不压栈），二级页压栈、带返回箭头。
 * 不引 navigation-compose：只有两层、参数只有一个诗句 id，自己维护返回栈依赖更少。
 */

/** 一级页（底部 tab）。 */
enum class Tab(val labelRes: Int) {
    OVERVIEW(R.string.tab_overview),
    LIBRARY(R.string.tab_library),
    SETTINGS(R.string.tab_settings),
}

/** 二级页（压栈）。 */
sealed interface Detail {
    data object Holes : Detail
    data object Whitelist : Detail
    data object About : Detail

    /** 编辑单个洞；slot 是洞的颜色槽位（稳定标识）。 */
    data class HoleEdit(val slot: Int) : Detail

    /** poemId 为 null 表示新增。 */
    data class PoemEdit(val poemId: String?) : Detail

    /**
     * 序列化成一个短字符串，供 {@code rememberSaveable} 存进 Bundle。
     *
     * 只做「类型 + 参数」的最小编码，不引 kotlinx.serialization：
     * 整个导航栈只有 5 种页面、最多两个参数。诗句 id 是内容哈希（十六进制），不含分隔符，可安全拼接。
     */
    fun encode(): String = when (this) {
        Holes -> "holes"
        Whitelist -> "whitelist"
        About -> "about"
        is HoleEdit -> "hole:$slot"
        is PoemEdit -> "poem:${poemId ?: ""}"
    }

    companion object {
        /** 反序列化；遇到不认识的编码返回 null（不崩）。 */
        fun decode(s: String): Detail? = when {
            s == "holes" -> Holes
            s == "whitelist" -> Whitelist
            s == "about" -> About
            s.startsWith("hole:") -> s.removePrefix("hole:").toIntOrNull()?.let(::HoleEdit)
            s.startsWith("poem:") -> PoemEdit(s.removePrefix("poem:").ifEmpty { null })
            else -> null
        }
    }
}

@Composable
private fun Detail.title(): String = when (this) {
    Detail.Holes -> stringResource(R.string.holes_title)
    Detail.Whitelist -> stringResource(R.string.whitelist_title)
    Detail.About -> stringResource(R.string.about_title)
    is Detail.HoleEdit -> stringResource(R.string.hole_edit_title)
    is Detail.PoemEdit -> stringResource(
        if (poemId == null) R.string.poem_add_title else R.string.poem_edit_title
    )
}

/** 全局提示条：页面里用 `LocalSnackbar.current("已保存")`。 */
val LocalSnackbar = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * 带「撤销」按钮的提示条。
 *
 * 删除诗句不可逆，但每次弹确认框会让常用操作变慢，所以「先删，再给 4 秒撤销窗口」：
 * {@code LocalUndoable.current("已删除") { 恢复原列表 }}。
 * 撤销回调捕获的是删除前的整份列表而不是单条诗，连续删两首再撤销也能逐级还原，不需要撤销栈。
 */
val LocalUndoable = staticCompositionLocalOf<(String, () -> Unit) -> Unit> { { _, _ -> } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoemApp(context: Context) {
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    // 导航栈也要能扛住进程被杀：把 Detail 编码成字符串存进 Bundle（rememberSaveable + listSaver）。
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { it.encode() } },
            restore = { saved ->
                mutableStateListOf<Detail>().apply {
                    saved.forEach { encoded -> Detail.decode(encoded)?.let(::add) }
                }
            },
        )
    ) { mutableStateListOf<Detail>() }
    val current: Detail? = stack.lastOrNull()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarUndoLabel = stringResource(R.string.undo)
    val showMessage: (String) -> Unit = remember(snackbarHostState) {
        { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
    }
    // 撤销用的提示条：挂一个动作按钮，点击即执行回调。
    // 新提示会顶掉旧提示，所以连续删除时只有最后一次的撤销入口可点 ——
    // 撤销的目标永远是「刚刚那一下」。
    val showUndoable: (String, () -> Unit) -> Unit =
        remember(snackbarHostState, snackbarUndoLabel) {
            { msg, undo ->
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = snackbarUndoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) undo()
                }
            }
        }

    // 系统返回键：先退二级页；已经在一级页时交还系统（回到桌面）。
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (current == null) {
                // 一级页用普通 TopAppBar：LargeTopAppBar 在只有 3 个 tab 的场景里
                // 会吃掉近 1/5 屏幕，却只换来一个标题。
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (tab) {
                                    Tab.OVERVIEW -> R.string.app_name
                                    Tab.LIBRARY -> R.string.library_title
                                    Tab.SETTINGS -> R.string.tab_settings
                                }
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text(current.title()) },
                    navigationIcon = {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        bottomBar = {
            if (current == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        Tab.OVERVIEW -> Icons.Filled.Home
                                        Tab.LIBRARY -> Icons.AutoMirrored.Filled.List
                                        Tab.SETTINGS -> Icons.Filled.Settings
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (current == null && tab == Tab.LIBRARY) {
                ExtendedFloatingActionButton(
                    onClick = { stack.add(Detail.PoemEdit(null)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_poem)) },
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalSnackbar provides showMessage,
            LocalUndoable provides showUndoable,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(180)))
                                .togetherWith(fadeOut(tween(120)))
                        } else {
                            fadeIn(tween(180)).togetherWith(
                                slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160))
                            )
                        }
                    },
                    label = "page",
                ) { detail ->
                    if (detail == null) {
                        when (tab) {
                            Tab.OVERVIEW -> OverviewScreen(context = context)
                            Tab.LIBRARY -> LibraryScreen(
                                onEdit = { stack.add(Detail.PoemEdit(it)) },
                                onAdd = { stack.add(Detail.PoemEdit(null)) },
                            )
                            Tab.SETTINGS -> SettingsScreen(
                                context = context,
                                onOpenHoles = { stack.add(Detail.Holes) },
                                onOpenWhitelist = { stack.add(Detail.Whitelist) },
                                onOpenAbout = { stack.add(Detail.About) },
                            )
                        }
                    } else {
                        when (detail) {
                            Detail.Holes -> CutoutScreen(
                                onEdit = { slot -> stack.add(Detail.HoleEdit(slot)) },
                            )
                            Detail.Whitelist -> WhitelistScreen(context)
                            Detail.About -> AboutScreen(context)
                            is Detail.HoleEdit -> HoleEditScreen(
                                slot = detail.slot,
                                onDone = { stack.removeAt(stack.lastIndex) },
                            )
                            is Detail.PoemEdit -> PoemEditScreen(
                                poemId = detail.poemId,
                                onDone = { stack.removeAt(stack.lastIndex) },
                            )
                        }
                    }
                }
            }
        }
    }

    // 洞设置相关页面（列表 + 编辑）里，把彩色框直接画在 App 窗口的状态栏区域上。
    // App 是 edge-to-edge 的，窗口本来就延伸到状态栏下面，所以这一段视觉上就是真实状态栏；
    // 状态栏图标由 SystemUI 在更上层绘制，框不会遮住它们。零跨进程、零延迟。
    if (current == Detail.Holes || current is Detail.HoleEdit) {
        HoleFrameOverlay(
            holes = HoleRects.parse(AppState.config.value.holesJson),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
    }
    }
}
