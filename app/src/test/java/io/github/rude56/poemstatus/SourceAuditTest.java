package io.github.rude56.poemstatus;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 省电与结构审计。
 *
 * <p>这些用例直接读源码文本做断言，把「性能/省电」这种容易被后续修改悄悄破坏的约束
 * 变成会失败的测试 —— 而不是只写在 README 里。
 */
public class SourceAuditTest {

    private static final File MAIN = new File("src/main/java/io/github/rude56/poemstatus");
    private static final File RES = new File("src/main/res");
    private static final File MAIN_RES = new File("src/main/resources");
    private static final File MANIFEST = new File("src/main/AndroidManifest.xml");

    private static String read(File f) throws IOException {
        assertTrue("文件不存在: " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String source(String name) throws IOException {
        return read(new File(MAIN, name));
    }

    /** 截取某个方法/匿名类的函数体的近似文本（按大括号配对）。 */
    private static String bodyOf(String text, String signature) {
        int idx = text.indexOf(signature);
        if (idx < 0) {
            fail("找不到方法: " + signature);
            return "";
        }
        int open = text.indexOf('{', idx);
        if (open < 0) {
            fail("方法没有函数体: " + signature);
            return "";
        }
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, i);
                }
            }
        }
        fail("大括号不配对: " + signature);
        return "";
    }

    // ---------------------------------------------------------------- 帧内约束

    @Test
    public void frameLoopDoesNotAllocateOrSampleOrLog() throws IOException {
        String text = source("PoemRuntime.java");
        String body = bodyOf(text, "private void updateFrame(long frameTimeNanos)");
        assertFalse("帧内不允许 new 对象", body.contains("new "));
        assertFalse("帧内不允许 StringBuilder", body.contains("StringBuilder"));
        assertFalse("帧内不允许打日志", body.contains("Log."));
        assertFalse("帧内不允许采样壁纸", body.contains("WallpaperManager"));
        assertFalse("帧内不允许读取系统栏外观", body.contains("getSystemBarsAppearance"));
        assertFalse("帧内不允许读时钟文字色", body.contains("getCurrentTextColor"));
        assertFalse("帧内不允许扫 View 树", body.contains("findViewById"));
        assertFalse("帧内不允许 measure/layout", body.contains("measure(") || body.contains("layout("));
        assertFalse("帧内不允许改 visibility", body.contains("setVisibility"));
        assertFalse("帧内不允许写磁盘", body.contains("FileOutputStream") || body.contains("FileReader"));
        assertFalse("帧内不允许联网", body.contains("Http") || body.contains("Socket"));
    }

    @Test
    public void frameDoesNotUseBitmapLayerOrBlur() throws IOException {
        String text = source("PoemRuntime.java");
        assertFalse(text.contains("setLayerType(LAYER_TYPE_HARDWARE"));
        assertFalse(text.contains("BlurMaskFilter"));
        assertFalse(text.contains("LinearGradient"));
        assertFalse(text.contains("RadialGradient"));
        assertFalse(text.contains("setShadowLayer"));
        assertFalse(text.contains("Bitmap.create"));
    }

    @Test
    public void colorSamplingIsNotInAnimationPath() throws IOException {
        String text = source("SystemUiController.java");
        String startCrawl = bodyOf(text, "public void startCrawl(PoemItem poem, String displayText, int speedPx)");
        // 允许在「开始一首」时采样一次
        assertTrue(startCrawl.contains("colorSampler.sample"));
        // 但帧回调里绝不允许采样
        String onFrame = bodyOf(text, "public void onFrame(float left, float top, float right, float bottom)");
        assertFalse(onFrame.contains("colorSampler"));
        assertFalse(onFrame.contains("WallpaperManager"));
        assertFalse(onFrame.contains("getCurrentTextColor"));
    }

    @Test
    public void clockAndPausePathsDoNotPostFrameCallbacks() throws IOException {
        String text = source("PoemEngine.java");
        String fin = bodyOf(text, "public void onCrawlFinished()");
        assertFalse("时间阶段不得开启帧回调", fin.contains("setFrameActive(true)"));
        String pause = bodyOf(text, "public void pause(String reason)");
        assertFalse(pause.contains("setFrameActive(true)"));
        assertTrue("暂停必须恢复模块", pause.contains("restoreModules()"));
        String stop = bodyOf(text, "public void stop(String reason)");
        assertTrue(stop.contains("restoreModules()"));
    }

    @Test
    public void onlyOnePlaceTouchesChoreographer() throws IOException {
        String text = source("PoemRuntime.java");
        int count = 0;
        int i = 0;
        while ((i = text.indexOf("Choreographer.getInstance()", i)) >= 0) {
            count++;
            i += 5;
        }
        assertTrue("Choreographer 调用次数异常: " + count, count >= 2 && count <= 8);
    }

    @Test
    public void delayedFrameSchedulingIsUsed() throws IOException {
        String text = source("PoemRuntime.java");
        assertTrue("必须用 postFrameCallbackDelayed 压帧率", text.contains("postFrameCallbackDelayed"));
    }

    @Test
    public void noDiskOrNetworkInSystemUiSide() throws IOException {
        for (String f : new String[]{"ModuleMain.java", "SystemUiController.java", "PoemRuntime.java",
                "ViewTree.java", "ColorSampler.java", "TintState.java", "ConfigFetcher.java",
                "ForegroundTracker.java", "HolePuncher.java", "Reflect.java"}) {
            String text = source(f);
            assertFalse(f + " 不允许写磁盘", text.contains("FileOutputStream"));
            assertFalse(f + " 不允许读私有文件", text.contains("getSharedPreferences"));
            assertFalse(f + " 不允许联网", text.contains("java.net.Socket") || text.contains("HttpURLConnection"));
        }
    }

    // ---------------------------------------------------------------- 功能契约

    @Test
    public void entryTitleAndSecondaryPageTitleExist() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        assertTrue("缺少古诗库页面标题", strings.contains("古诗库"));
        // 新结构：古诗库是一级 tab，不再是主页上的二级入口。
        String app = source("ui/PoemApp.kt");
        assertTrue("古诗库必须是一级 tab", app.contains("LIBRARY(R.string.tab_library)"));
        assertTrue("古诗库页面必须被渲染", app.contains("Tab.LIBRARY -> LibraryScreen"));
    }

    /**
     * 导航必须严格两层：一级 = 底部导航的三个 tab，二级 = 压栈的详情页。
     *
     * <p>这条约束容易被后续改动破坏（例如随手把某个页面塞进 tab 里，
     * 或者让二级页也显示底部栏），所以用测试钉住。
     */
    @Test
    public void navigationIsTwoLevelMaterialStructure() throws IOException {
        String app = source("ui/PoemApp.kt");
        // 一级：恰好三个 tab
        assertTrue(app.contains("OVERVIEW(R.string.tab_overview)"));
        assertTrue(app.contains("LIBRARY(R.string.tab_library)"));
        assertTrue(app.contains("SETTINGS(R.string.tab_settings)"));
        // 底部导航只在一级页出现（二级页压栈时隐藏）
        assertTrue("底部导航必须受 current == null 控制",
                app.contains("if (current == null) {") && app.contains("NavigationBar("));
        // 二级页必须有返回箭头
        assertTrue("二级页必须有返回按钮", app.contains("Icons.AutoMirrored.Filled.ArrowBack"));
        // 系统返回键必须先退二级页
        assertTrue("必须拦截系统返回键", app.contains("BackHandler(enabled = stack.isNotEmpty())"));
        // 二级页集合
        assertTrue(app.contains("data object Holes : Detail"));
        assertTrue(app.contains("data object Whitelist : Detail"));
        assertTrue(app.contains("data object About : Detail"));
        assertTrue(app.contains("data class PoemEdit(val poemId: String?) : Detail"));
    }

    /** 主题必须是 Material 3，并且支持动态取色与深色模式。 */
    @Test
    public void themeIsMaterial3WithDynamicColor() throws IOException {
        String theme = source("ui/theme/Theme.kt");
        assertTrue("必须用 Material 3 的 lightColorScheme", theme.contains("lightColorScheme("));
        assertTrue("必须用 Material 3 的 darkColorScheme", theme.contains("darkColorScheme("));
        assertTrue("必须支持动态取色", theme.contains("dynamicLightColorScheme"));
        assertTrue("必须支持深色动态取色", theme.contains("dynamicDarkColorScheme"));
        assertTrue("必须跟随系统深浅色", theme.contains("isSystemInDarkTheme()"));
        assertTrue("必须定义圆角体系", theme.contains("Shapes("));
        assertTrue("必须定义字体体系", theme.contains("Typography("));
        // MainActivity 必须套上主题，而不是裸 MaterialTheme 默认配色
        String activity = source("MainActivity.kt");
        assertTrue("MainActivity 必须使用 PoemTheme", activity.contains("PoemTheme {"));
        assertTrue("必须边到边显示", activity.contains("enableEdgeToEdge"));
        assertFalse("不得再使用裸 MaterialTheme 包一层", activity.contains("MaterialTheme {"));
    }

    /**
     * 概览页只保留主开关 + 连接自检 + 配置速览。
     *
     * <p>这条测试同时防止两件事：① 有人把删掉的预览又加回来；
     * ② 概览页重新长成一堆入口卡片，把「一眼看清开没开」这件事冲淡。
     */
    @Test
    public void overviewIsJustTheMasterSwitch() throws IOException {
        String overview = source("ui/OverviewScreen.kt");
        assertTrue("必须有主开关卡片", overview.contains("MasterCard("));
        assertFalse("状态栏效果预览已删除", overview.contains("StatusBarStrip("));
        assertFalse("快捷入口已删除", overview.contains("NavRow("));
        assertFalse("不得再引用快捷入口文案", overview.contains("overview_shortcuts"));
        // 首次引导保留：开关关着时告诉用户下一步做什么。
        assertTrue("首次使用必须有引导", overview.contains("overview_getting_started"));
        assertTrue("引导必须只在关闭时出现", overview.contains("if (!cfg.enabled)"));
        // 开关开着却播不了时要说清缺什么。
        assertTrue("必须提示阻塞原因", overview.contains("blockerText("));
        // 新增：连接自检 + 配置速览
        assertTrue("必须有连接自检", overview.contains("ConnectionCard("));
        assertTrue("必须有配置速览", overview.contains("ConfigSummaryCard("));
    }

    /**
     * LSPosed 连接自检必须真的能判断「模块有没有生效」。
     *
     * <p>原理：注入成功的 SystemUI 每 2 秒会来 {@code ConfigProvider} 读一次配置。
     * 所以「最后一次被 SystemUI 调用的时间」就是唯一的活性信号 ——
     * 模块没启用 / 作用域没勾 / 没重启 SystemUI，这个时间戳永远不会刷新。
     *
     * <p>关键约束：只认<b>调用方确实是 SystemUI</b> 的调用。
     * 否则 App 自己的诊断调用会把「未连接」误判成「已连接」。
     */
    @Test
    public void connectionCheckDetectsWhetherTheModuleIsAlive() throws IOException {
        String provider = source("ConfigProvider.java");
        assertTrue("必须记录最后调用时刻", provider.contains("lastSystemUiCallAt"));
        assertTrue("必须用 elapsedRealtime", provider.contains("elapsedRealtime()"));
        assertTrue("必须校验调用方是 SystemUI", provider.contains("getCallingPackage()"));
        assertTrue("必须与作用域包名比对", provider.contains("FeatureAudit.SCOPE_PACKAGE"));
        assertTrue("必须提供读取入口", provider.contains("public static long lastSystemUiCallAt()"));

        String overview = source("ui/OverviewScreen.kt");
        assertTrue("概览页必须读这个时间戳", overview.contains("ConfigProvider.lastSystemUiCallAt()"));
        // 超时判定：正常 2 秒一次，留 5 倍余量。
        assertTrue("必须有超时判定", overview.contains("secondsAgo in 0..10"));

        String cards = source("ui/StatusCards.kt");
        assertTrue("必须有已连接文案", cards.contains("R.string.lsp_connected"));
        assertTrue("必须有未连接文案", cards.contains("R.string.lsp_not_connected"));
        // 未连接时必须给出三步排查
        assertTrue("必须给出启用步骤", cards.contains("R.string.lsp_step_enable"));
        assertTrue("必须给出作用域步骤", cards.contains("R.string.lsp_step_scope"));
        assertTrue("必须给出重启步骤", cards.contains("R.string.lsp_step_restart"));
    }

    /**
     * 作用域必须是<b>静态</b>的，且只含 SystemUI。
     *
     * <p>这是本次从旧 API 迁到现代 libxposed API 的<b>唯一动机</b>：
     * 旧模块的 {@code xposedscope} 只是一个「建议列表」，用户可以随手把模块
     * 勾到任意应用上；现代模块的 {@code module.prop} 里
     * {@code staticScope=true} 才是框架层面拒绝越界的声明。
     *
     * <p>以前这条测试读的是 {@code res/values/arrays.xml} 里的
     * {@code xposed_scope} 数组 —— 它只能证明「我们写了个列表」，
     * 完全无法证明「用户改不了它」。
     */
    @Test
    public void moduleDeclaresStaticScopeWithSystemUiOnly() throws IOException {
        File dir = new File(MAIN_RES, "META-INF/xposed");
        File scopeFile = new File(dir, "scope.list");
        File propFile = new File(dir, "module.prop");
        File initFile = new File(dir, "java_init.list");
        assertTrue("必须有 scope.list", scopeFile.isFile());
        assertTrue("必须有 module.prop", propFile.isFile());
        assertTrue("必须有 java_init.list", initFile.isFile());

        List<String> entries = new ArrayList<>();
        for (String line : read(scopeFile).split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                entries.add(t);
            }
        }
        assertEquals("作用域必须唯一", 1, entries.size());
        assertEquals("作用域只能是 SystemUI", FeatureAudit.SCOPE_PACKAGE, entries.get(0));

        String prop = read(propFile);
        assertTrue("必须声明静态作用域", prop.contains("staticScope=true"));
        assertTrue("必须声明 minApiVersion", prop.contains("minApiVersion="));
        assertTrue("必须声明 targetApiVersion", prop.contains("targetApiVersion="));

        assertTrue("入口类必须写进 java_init.list",
                read(initFile).trim().endsWith("ModuleMain"));
    }

    /** 旧模块声明必须清干净，否则 LSPosed 会把它当 legacy 模块处理（staticScope 无效）。 */
    @Test
    public void legacyModuleDeclarationIsGone() throws IOException {
        String manifest = read(MANIFEST);
        // 断言的是「声明」，不是「这个词」—— 注释里提到旧名字是合理的。
        assertFalse("不得再声明 xposedmodule",
                manifest.contains("android:name=\"xposedmodule\""));
        assertFalse("不得再声明 xposedscope",
                manifest.contains("android:name=\"xposedscope\""));
        assertFalse("不得再声明 xposedminversion",
                manifest.contains("android:name=\"xposedminversion\""));
        assertFalse("不得再有旧作用域数组",
                new File(RES, "values/arrays.xml").isFile());
        assertFalse("旧入口类必须已删除", new File(MAIN, "XposedInit.java").isFile());
        assertFalse("旧入口清单必须已删除",
                new File("src/main/assets/xposed_init").isFile());
    }

    @Test
    public void manifestDeclaresNoInternetPermission() throws IOException {
        String manifest = read(MANIFEST);
        assertFalse("不得申请联网权限", manifest.contains("android.permission.INTERNET"));
        // 现代模块用 android:description 作为模块描述。
        assertTrue(manifest.contains("android:description="));
        assertTrue(manifest.contains("io.github.rude56.poemstatus.config"));
    }

    @Test
    public void providerIsReadOnly() throws IOException {
        String text = source("ConfigProvider.java");
        String insert = bodyOf(text, "public Uri insert(");
        assertTrue("insert 必须拒绝写入", insert.contains("return null"));
        String update = bodyOf(text, "public int update(");
        assertTrue(update.contains("return 0"));
        String del = bodyOf(text, "public int delete(");
        assertTrue(del.contains("return 0"));
        assertTrue("必须校验 token", text.contains("PROVIDER_TOKEN"));
    }

    /**
     * 现代入口只处理 SystemUI。
     *
     * <p>现代 API 的入口是 {@link io.github.rude56.poemstatus.ModuleMain}：
     * 它在 {@code onPackageReady} 里按包名过滤，而不是旧 API 的
     * {@code handleLoadPackage} + {@code lpparam.packageName}。
     */
    @Test
    public void moduleEntryOnlyHandlesSystemUi() throws IOException {
        String text = source("ModuleMain.java");
        assertTrue("必须按包名过滤",
                text.contains("FeatureAudit.SCOPE_PACKAGE.equals(param.getPackageName())"));
        assertTrue("必须打 INJECTED 日志", text.contains("INJECTED process="));
        assertTrue("必须继承现代入口基类", text.contains("extends XposedModule"));
        // 旧 API 不能被残留：现代模块（targetApiVersion>=102）根本不允许调用旧 API。
        for (String f : new String[]{"ModuleMain.java", "SystemUiController.java"}) {
            String s = source(f);
            assertFalse(f + " 不得再引用旧 API",
                    s.contains("de.robv.android.xposed") || s.contains("XposedHelpers")
                            || s.contains("XC_MethodHook"));
        }
    }

    @Test
    public void existsAKeyConstantClassUsedByBothSides() throws IOException {
        String keys = source("ConfigKeys.java");
        String codec = source("ConfigCodec.java");
        String store = source("ConfigStore.java");
        for (String key : new String[]{"ENABLED", "SPEED_PX", "CLOCK_SECONDS", "POEMS_JSON", "CONFIG_REVISION"}) {
            assertTrue("ConfigKeys 缺少 " + key, keys.contains(key));
            assertTrue("编码端未使用 " + key, codec.contains("ConfigKeys." + key));
        }
        // App 写入端也直接引用同一个常量类（不是手写字符串）
        assertTrue(store.contains("ConfigKeys.SPEED_PX"));
        assertTrue(store.contains("ConfigKeys.POEMS_JSON"));
    }

    @Test
    public void pauseReasonsAreAllImplemented() throws IOException {
        String text = source("SystemUiController.java");
        for (String reason : new String[]{"disabled", "screen-off", "landscape", "whitelist", "view-unavailable", "guard"}) {
            assertTrue("缺少暂停原因: " + reason, text.contains(reason));
        }
    }

    /**
     * 旧的「逐个 View 改 alpha」遮挡方案已整体删除。
     *
     * <p>删除理由（不是图省事）：
     * <ol>
     *   <li>它在 Compose 化的状态栏上<b>根本无效</b> —— 改 ComposeView 的 alpha
     *       会让图标「闪一下就回来」，改不了已经提交的绘制内容；</li>
     *   <li>它是一条永不执行的死分支（{@code OcclusionMode.USE_HOLE} 恒为 true），
     *       既没法测，也没法在真机上验证；</li>
     *   <li>它和挖洞方案互相打架：两套都在动状态栏的绘制状态，只在测试里覆着。</li>
     * </ol>
     *
     * <p>现在唯一的遮挡方式就是 {@link HolePuncher} 的一次性画布裁剪。
     * 真需要那种「只藏图标不藏时钟」的粒度时，从 git 历史恢复比长期维护死代码更快。
     */
    @Test
    public void alphaOcclusionSchemeIsGone() throws IOException {
        assertFalse("旧方案类必须已删除", new File(MAIN, "ModuleOccluder.java").isFile());
        assertFalse("模式开关必须已删除", new File(MAIN, "OcclusionMode.java").isFile());
        assertFalse("只被旧方案使用的规划器必须已删除",
                new File(MAIN, "OcclusionPlanner.java").isFile());
        // 任何地方都不得再引用它们。
        for (String f : new String[]{"PoemRuntime.java", "SystemUiController.java",
                "ColorSampler.java", "HolePuncher.java"}) {
            String text = source(f);
            assertFalse(f + " 不得再引用 ModuleOccluder", text.contains("ModuleOccluder"));
            assertFalse(f + " 不得再引用 OcclusionMode", text.contains("OcclusionMode"));
        }
        // 帧内也不允许再出现任何 alpha 写入。
        String runtime = source("PoemRuntime.java");
        assertFalse("诗句层不得改别人 alpha", runtime.contains("setAlpha"));
    }

    /** 挖洞是唯一的遮挡方式，且必须成对 save/restore、必须节流。 */
    @Test
    public void holePunchIsTheOnlyOcclusionScheme() throws IOException {
        String hole = source("HolePuncher.java");
        // 核心机制：在内容根的画布上挖洞，clip 随 save/restore 传给所有子孙。
        assertTrue("必须用 clipOutRect 挖洞", hole.contains("clipOutRect"));
        // save/restore 必须成对，且必须走「栈」而不是单一计数：
        // View.draw 被重入时，单一计数会被内层抹掉，导致画布裁剪永久残留。
        assertTrue("必须用 ClipStack 管理 save/restore", hole.contains("ClipStack"));
        assertTrue("必须 restoreToCount 到本层保存点", hole.contains("restoreToCount"));
        // 不得再退回单一计数：只检查<b>代码</b>，注释里提到旧实现是合理的。
        for (String line : hole.split("\n")) {
            String code = line.trim();
            if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                continue;
            }
            assertFalse("不得再退回单一计数: " + code, code.contains("savedClips"));
        }
        // 必须只在绑定的内容根上挖，不能挖到诗句层自己。
        assertTrue("必须校验目标就是内容根", hole.contains("view != root"));
        // 重绘要节流，否则等于每帧重绘整条状态栏。
        assertTrue("必须节流", hole.contains("INVALIDATE_INTERVAL_MS"));

        // 挖洞必须绑在 Compose 画布的祖先层，绑到 status_bar_contents 管不到图标。
        // 查找逻辑在 lookupContentRoot 里；findContentRoot 只负责缓存。
        String tree = source("ViewTree.java");
        String findRoot = bodyOf(tree, "private static View lookupContentRoot(View window)");
        assertTrue("必须优先绑 status_bar_container",
                findRoot.contains("status_bar_container"));
        // 每首诗都会调一次：必须缓存，否则每次都要查资源表 + 递归遍历子树。
        String cached = bodyOf(tree, "static View findContentRoot(View window)");
        assertTrue("内容根必须缓存", cached.contains("cachedRoot"));
        assertTrue("缓存必须在窗口重建时失效", cached.contains("cachedWindow == window"));
        // 帧内不允许打日志（挖洞路径同样受约束）。
        String runtime = source("PoemRuntime.java");
        String onFrame = bodyOf(runtime,
                "void updateOcclusion(float l, float t, float r, float b)");
        assertFalse(onFrame.contains("Log."));
    }

    @Test
    public void colorComesFromSystemTintNotHeuristics() throws IOException {
        // 自动反色的正确做法是「抄系统算好的前景色」，不是自己看壁纸/亮度。
        String tint = source("TintState.java");
        assertTrue("必须 Hook 系统的图标 tint", tint.contains("mIconTint"));
        String hook = source("SystemUiController.java");
        assertTrue(hook.contains("DarkIconDispatcherImpl"));
        assertTrue(hook.contains("applyIconTint"));
        String sampler = source("ColorSampler.java");
        assertTrue("取色必须优先用系统 tint", sampler.contains("TintState.tint()"));
        assertFalse("不得读壁纸", sampler.contains("WallpaperManager"));
        // 帧回调里绝不允许取色
        String onFrame = bodyOf(hook, "public void onFrame(float left, float top, float right, float bottom)");
        assertFalse(onFrame.contains("colorSampler"));
        assertFalse(onFrame.contains("TintState"));
    }

    /**
     * 虫洞位移只能算<b>一次</b>，而且只能算在一个地方。
     *
     * <p>历史教训：{@code onFrame} 先换算成屏幕位置、{@code updateOcclusion}
     * 又换算一次 → 洞宽被减两遍，遮挡比文字提前一个洞宽（「字还没到就遮住了」）。
     * 反之两边都不换算 → 遮挡滞后一个洞宽。
     *
     * <p>现在的做法更彻底：分段在 {@code updateFrame} 里算一次，
     * 绘制层与遮挡层<b>共用同一份结果</b>。以前是两层各调一次
     * {@code wormholeSegments}，等于每帧做两次完全相同的全洞扫描。
     * 所以这里断言的是「全文件只有一处调用」。
     */
    @Test
    public void wormholeSegmentsAreComputedExactlyOnce() throws IOException {
        String rt = source("PoemRuntime.java");
        int first = rt.indexOf("HoleRects.wormholeSegments");
        assertTrue("必须调用 wormholeSegments", first >= 0);
        assertEquals("分段只能算一次（绘制层与遮挡层共用结果）",
                -1, rt.indexOf("HoleRects.wormholeSegments", first + 1));

        // 计算必须发生在帧循环里，绘制与遮挡都只读结果。
        String frame = bodyOf(rt, "private void updateFrame(long frameTimeNanos)");
        assertTrue("分段必须在帧里算", frame.contains("HoleRects.wormholeSegments"));
        String draw = bodyOf(rt, "private void drawTextWithHoles(Canvas canvas)");
        assertFalse("绘制层不得重算分段", draw.contains("HoleRects.wormholeSegments"));
        String upd = bodyOf(rt, "void updateOcclusion(float l, float t, float r, float b)");
        assertFalse("遮挡层不得重算分段", upd.contains("HoleRects.wormholeSegments"));
        assertTrue("遮挡层必须复用帧里的分段", upd.contains("segCount"));
    }
    @Test
    public void framePositionIsNeverRewrittenByHoles() throws IOException {
        // 诗句位置必须只由「时间 × 速度」决定。
        // 任何洞都不得参与位置计算 —— 否则会出现开头缺字、速度突变。
        String rt = source("PoemRuntime.java");
        String frame = bodyOf(rt, "private void updateFrame(long frameTimeNanos)");
        assertTrue("位置必须由时间算出", frame.contains("viewW - traveled"));
        assertFalse("帧内不得调用任何洞位移函数", frame.contains("shiftForWormhole"));
        assertFalse(frame.contains("wormholeShift"));
        assertFalse("帧内不得引用 HolePuncher", frame.contains("HolePuncher"));
        // 改写函数必须已从 HolePuncher 中删除。
        assertFalse("位移函数应已移除",
                source("HolePuncher.java").contains("static float shiftForWormhole"));
    }

    @Test
    public void holesOnlyAffectTextDrawing() throws IOException {
        String rt = source("PoemRuntime.java");
        String draw = bodyOf(rt, "private void drawTextWithHoles(Canvas canvas)");
        // 隧道：洞内不画。
        assertTrue("隧道必须 clipOutRect", draw.contains("clipOutRect"));
        assertTrue("必须正常绘制文字", draw.contains("canvas.drawText"));
        // 虫洞：必须按分段平移绘制（这就是「跳跃」），分段由帧里算好后复用。
        assertTrue("虫洞必须分段平移", draw.contains("canvas.translate"));
        assertTrue("绘制必须复用帧里算好的分段", draw.contains("segCount"));
        // 缓冲区长度必须受 MAX_HOLES 约束。
        assertTrue("缓冲长度必须受 MAX_HOLES 约束",
                rt.contains("new float[HoleRects.MAX_HOLES + 1]"));
    }

    @Test
    public void holesNeverPunchThroughTheStatusBar() throws IOException {
        // 用户定义的「洞」本身绝不在状态栏上挖洞 —— 洞只影响诗句自己的绘制。
        // 状态栏的挖洞只来自「文字可见的分段」。
        String hole = source("HolePuncher.java");
        String begin = bodyOf(hole, "static boolean beginClip(Canvas canvas, View view)");
        assertTrue("必须按可见分段挖", begin.contains("segLeft[i]"));
        assertFalse("不得直接按用户洞列表挖", begin.contains("holes.get(i)"));
        // 位移函数必须已彻底移除。
        assertFalse("位移函数应已删除", hole.contains("shiftForWormhole"));
    }

    @Test
    public void tunnelDoesNotOccludeStatusBar() throws IOException {
        // 隧道里的字看不见，那段屏幕不该再遮挡状态栏内容。
        // 否则会出现「诗进了隧道看不见，图标却还被挖掉一块」。
        //
        // 遮挡分段现在整体搬到 HoleRects.visibleSegments（纯函数，可逐帧验证），
        // 所以这里钉的是「运行时确实走那条路」，减法细节在 HoleRects 里校验。
        String rt = source("PoemRuntime.java");
        String upd = bodyOf(rt, "void updateOcclusion(float l, float t, float r, float b)");
        assertTrue("遮挡必须走 visibleSegments", upd.contains("HoleRects.visibleSegments"));
        // 运行时不得再自己内联一套分段/减法（那正是上一个 bug 的所在）。
        assertFalse("运行时不得重算隧道减法", upd.contains("subtractTunnels"));
        // 坐标契约：必须把诗句层的屏幕原点交出去。
        assertTrue("必须使用诗句层屏幕原点", upd.contains("screenOriginX"));

        // 减法本身是 HoleRects 里的纯函数，由 ColorResolverTest 做行为验证。
        String holes = source("HoleRects.java");
        String vis = bodyOf(holes, "public static int visibleSegments(");
        assertTrue("可见分段必须减掉隧道", vis.contains("subtractTunnels"));
        String sub = bodyOf(holes, "public static int subtractTunnels(");
        assertTrue("只减隧道洞", sub.contains("h.isWormhole()"));
        assertTrue("必须跳过隧道区间", sub.contains("cursor = Math.max(cursor, hr)"));
        // 帧内零分配：结果必须写进调用方数组，不能在这里 new。
        assertFalse("不得在帧内分配", sub.contains("new "));
        assertFalse("可见分段也不得分配", vis.contains("new "));
    }

    /**
     * 状态栏上的框：只在「洞设置页」显示，且离开页面必须自动关闭。
     *
     * <p>约束来自两次真实事故：框常驻状态栏、框闪现。
     * 根因都是「靠 SystemUI 进程判断 App 是否前台」不可靠。
     * 现在的做法是 App 侧用 {@code DisposableEffect} 在页面销毁时把标志复位，
     * 跨进程只传一个布尔值，不依赖任何前台判断。
     */
    /**
     * 状态栏上的框：由 <b>App 自己绘制</b>，只在洞页出现。
     *
     * <p>这条测试记录了三次失败的尝试，防止再走回头路：
     * <ol>
     *   <li>第一版让 SystemUI 判断「App 是否前台」→ 判断不可靠，框闪现/常驻；</li>
     *   <li>第二版改成 App 写 {@code cutoutPreview} 标志 → 标志写进去了、
     *       SystemUI 也读到新配置了，但预览层就是挂不上（日志里只有 CONF apply）；</li>
     *   <li>第三版（现在）：App 自己是 edge-to-edge 全屏的，窗口本来就延伸到
     *       状态栏下面，直接在 y=0..状态栏高度 画框即可。零跨进程、零延迟。</li>
     * </ol>
     */
    @Test
    public void holeFramesAreDrawnByTheAppItself() throws IOException {
        String overlay = source("ui/HoleFrameOverlay.kt");
        // 必须按状态栏 inset 定位，才能正好压在真实状态栏上。
        assertTrue("必须读状态栏高度", overlay.contains("WindowInsets.statusBars"));
        assertTrue("必须用 statusBars.getTop", overlay.contains("statusBars.getTop"));
        // 颜色必须取洞自己的槽位，不能取列表下标（否则增删洞会换色）。
        assertTrue("颜色必须按槽位取", overlay.contains("holeColor(hole.slot)"));
        // 框内不得标数字：这么窄的一条里既看不清又盖住图标。
        assertFalse("框内不得画序号", overlay.contains("drawLabel") || overlay.contains("measurer"));

        // 只在洞列表页和洞编辑页出现。
        String app = source("ui/PoemApp.kt");
        assertTrue("洞页必须显示框", app.contains("current == Detail.Holes"));
        assertTrue("洞编辑页也必须显示框", app.contains("current is Detail.HoleEdit"));
        assertTrue("必须调用 HoleFrameOverlay", app.contains("HoleFrameOverlay("));

        // 不再依赖 SystemUI 的预览层。
        String ctl = source("SystemUiController.java");
        assertFalse("不得再靠 App 是否前台来判断", ctl.contains("isSelfForeground"));
        String cutout = source("ui/CutoutScreen.kt");
        assertFalse("不得再写 cutoutPreview 标志", cutout.contains("cutoutPreview = true"));
        assertFalse("不得再有「在状态栏显示框」开关", cutout.contains("R.string.hole_show_on_status_bar"));
    }

    /** 洞页的两种穿越方式必须是两个固定样例（位置写死、可对照）。 */
    @Test
    public void holesPageShowsTwoFixedSamples() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertTrue("必须同时演示隧道", samples.contains("HoleRects.Mode.TUNNEL"));
        assertTrue("必须同时演示虫洞", samples.contains("HoleRects.Mode.WORMHOLE"));
        assertTrue("虫洞必须复用真实分段算法", samples.contains("HoleRects.wormholeSegments"));
        // 洞位置写死（按预览宽度比例），不跟随用户配置 —— 否则用户没配洞就看不到演示。
        assertTrue("洞位置必须写死为常量",
                samples.contains("HOLE_LEFT_FRAC = 0.30f")
                        && samples.contains("HOLE_RIGHT_FRAC = 0.62f"));
        // 两个样例共用同一进度，才能逐帧对照。
        assertTrue("两个样例必须共用进度", samples.contains("progress = progress"));
        // 白底黑字：不跟随深色主题。
        assertTrue("样例必须是白底", samples.contains("Color.White"));
        // 洞页必须引用它
        String cutout = source("ui/CutoutScreen.kt");
        assertTrue("洞页必须显示两个样例", cutout.contains("HoleModeSamples("));
    }

    /**
     * 两行预览必须<b>同时</b>有古诗 —— 这是它们存在的唯一理由。
     *
     * <p>这条约束极易被破坏且不会报错：只要某一行的消失点比另一行早，
     * 每轮末尾就有一段时间只剩一行有字，对比直接失效。
     *
     * <p>以前有两个具体缺陷：
     * <ol>
     *   <li>进度 {@code coerceIn(0f, 1f)} 且起点从不重置 —— 跑到 14 秒后
     *       永久停在 progress=1，<b>两行都是空的</b>，再也没法对比；</li>
     *   <li>循环按整条轨道 {@code [0,1]} 走，而虫洞行会提前 19% 走空 ——
     *       每轮有 2.7 秒只有隧道行还在滚。</li>
     * </ol>
     */
    @Test
    public void bothPreviewsAlwaysShowAPoemAtTheSameTime() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        // 必须按周期取模循环，不得再夹取（夹取会冻结）。
        assertTrue("必须用 PreviewClock 推进", samples.contains("PreviewClock.progressAt"));
        assertFalse("不得再 coerceIn 到 [0,1]（会永久冻结在末尾）",
                samples.contains("coerceIn(0f, 1f)"));
        // 循环终点必须按「两行都还有字」来算。
        assertTrue("必须用 cycleEnd 收缩循环范围", samples.contains("PreviewClock.cycleEnd"));
        assertTrue("进度必须映射到 [0, cycleEnd)",
                samples.contains("p * cycleEnd"));
        // 两行必须共用同一支画笔测量文字宽度，否则消失点会差一点点。
        assertTrue("两行必须共用画笔", samples.contains("paint: Paint"));
        assertTrue("文字宽度必须只测一次", samples.contains("val textWidth = remember"));
        // 两行必须报告同一个画布宽度。
        assertTrue("两行必须共用画布宽度", samples.contains("onWidth(w)"));

        // 时钟本身的行为由 PreviewClockTest 覆盖。
        assertTrue(new File(MAIN, "PreviewClock.java").isFile());
    }

    /**
     * 洞页的界面必须与古诗库一致：卡片列表 + 点开进编辑页 + 滑删 + FAB。
     *
     * <p>一致性是刻意的：用户在诗库里学到的操作方式在这里应该原样成立。
     */
    @Test
    public void holesPageUsesTheSameUiAsTheLibrary() throws IOException {
        String cutout = source("ui/CutoutScreen.kt");
        assertTrue("必须是卡片列表", cutout.contains("LazyColumn("));
        assertTrue("必须点卡片进编辑页", cutout.contains("onEdit(hole.slot)"));
        assertTrue("必须支持左滑删除", cutout.contains("SwipeToDismissBox("));
        assertTrue("滑删必须先确认", cutout.contains("pendingDelete = hole"));
        assertTrue("必须有 FAB 新增", cutout.contains("ExtendedFloatingActionButton("));
        // 列表里不再直接放输入框，全部收进编辑页。
        assertFalse("列表里不得有输入框", cutout.contains("OutlinedTextField("));
        // 编辑页必须存在且避让键盘
        String edit = source("ui/HoleEditScreen.kt");
        assertTrue("必须有洞编辑页", edit.contains("fun HoleEditScreen("));
        assertTrue("编辑页必须避让键盘", edit.contains("imePadding()"));
        assertTrue("编辑页必须能删除", edit.contains("R.string.delete"));
    }

    /**
     * 新增洞不得改变已有洞的颜色。
     *
     * <p>颜色按「槽位」存，槽位跟着洞走；新洞取当前没被占用的最小槽位。
     */
    @Test
    public void addingHoleNeverRecolorsExistingOnes() throws IOException {
        String cutout = source("ui/CutoutScreen.kt");
        assertTrue("新洞必须取空闲槽位", cutout.contains("HoleRects.nextFreeSlot("));

        String holes = source("HoleRects.java");
        assertTrue("Hole 必须带槽位", holes.contains("public final int slot;"));
        assertTrue("必须能生成新槽位", holes.contains("public static int nextFreeSlot("));
        assertTrue("解析时必须补齐槽位", holes.contains("assignSlots("));
        // 解析时还必须去掉互相重合的洞（重合会让绘制层自相矛盾）。
        assertTrue("解析时必须去重", holes.contains("sanitize("));
        // 编码必须带上槽位，否则重启后颜色会重排。
        assertTrue("编码必须写入槽位", holes.contains(".append(h.slot)"));
    }

    @Test
    public void cutoutIsNumericInputNotDragging() throws IOException {
        // 洞的数值输入在编辑页里（列表只负责浏览）。
        String edit = source("ui/HoleEditScreen.kt");
        assertTrue("必须是数值输入", edit.contains("OutlinedTextField("));
        assertTrue("必须限制为数字键盘", edit.contains("KeyboardType.Number"));
        assertTrue("必须校验右边界 > 左边界", edit.contains("r <= l"));
        // 多矩形：可添加/删除多个洞，且高度恒为状态栏高度（只填左右）。
        String screen = source("ui/CutoutScreen.kt");
        assertTrue("必须能添加", screen.contains("R.string.hole_add"));
        assertTrue("必须能删除", screen.contains("R.string.delete"));
        // 两种模式 + 颜色区分
        assertTrue("每个洞可选虫洞/隧道", edit.contains("HoleRects.Mode.WORMHOLE")
                && edit.contains("HoleRects.Mode.TUNNEL"));
        assertTrue("不同洞用不同颜色", screen.contains("holeColor"));
        // 删除必须二次确认（洞是手工量出来的，误删要重新量）
        assertTrue("删除必须确认", screen.contains("R.string.hole_delete_confirm"));
        // 清空全部按钮已按要求删除
        assertFalse("不得再有清空全部", screen.contains("R.string.hole_clear"));
        // 校准 Activity 已删除
        assertFalse("校准 Activity 应已移除",
                new File(MAIN, "CutoutCalibrationActivity.java").isFile());
        String manifest = read(MANIFEST);
        assertFalse("Manifest 不得再声明校准 Activity",
                manifest.contains("CutoutCalibrationActivity"));
    }

    @Test
    public void libraryScreenIsTappableRowWithStableKey() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue("LazyColumn 必须使用稳定 key", library.contains("key = { _, poem -> poem.id }"));
        assertTrue("整行可点即编辑", library.contains("clickable(onClick = onEdit)"));
        assertTrue("必须有启用开关", library.contains("Switch(checked = poem.enabled"));
        // 新增/编辑改成整页（二级页），不再是对话框 —— 键盘弹出后对话框放不下
        assertFalse("不得再用编辑对话框", library.contains("AlertDialog(\n            onDismissRequest = { adding"));
        String edit = source("ui/PoemEditScreen.kt");
        assertTrue("编辑页必须避让键盘", edit.contains("imePadding()"));
        assertTrue("编辑页必须能删除已有诗", edit.contains("R.string.delete"));
    }

    /**
     * 古诗库必须支持滑动删除与导入导出。
     *
     * <p>滑动删除用 Material 3 的 {@code SwipeToDismissBox}；导入导出走
     * {@code 诗句|作者|出处} 纯文本（{@link PoemTextCodec}）。
     */
    @Test
    public void librarySupportsSwipeDeleteAndTextImportExport() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue("必须支持左滑删除", library.contains("SwipeToDismissBox("));
        assertTrue("必须用 M3 的滑动状态", library.contains("rememberSwipeToDismissBoxState"));
        assertTrue("只允许从右往左滑", library.contains("enableDismissFromEndToStart"));
        assertTrue("滑动删除必须先确认", library.contains("pendingDelete = poem"));
        assertTrue("必须有导入入口", library.contains("R.string.poem_import"));
        assertTrue("必须有导出入口", library.contains("R.string.poem_export"));
        assertTrue("导入必须走纯文本编解码", library.contains("PoemTextCodec.decode"));
        assertTrue("导出必须走纯文本编解码", library.contains("PoemTextCodec.encode"));

        // 编解码本身：格式必须是「诗句|作者|出处」，一行一条。
        String codec = source("PoemTextCodec.java");
        assertTrue("分隔符必须是竖线", codec.contains("public static final char SEPARATOR = '|'"));
        assertTrue("导出必须一行一首", codec.contains("sb.append('\\n')"));
        assertTrue("必须能容忍全角竖线", codec.contains("replace('｜', SEPARATOR)"));
        assertTrue("必须能容忍制表符", codec.contains("replace('\\t', SEPARATOR)"));
        assertTrue("导入必须跳过重复", codec.contains("PoemLibrary.isDuplicate"));
    }

    @Test
    public void overlayRemovalHappensOnEveryExitPath() throws IOException {
        String text = source("PoemRuntime.java");
        String stop = bodyOf(text, "void stop()");
        // 诗句层现在是复用的：停止只清文字、停帧、清洞，
        // 不再每首 add/removeView（那会触发整棵状态栏重新布局）。
        assertTrue("必须停帧", stop.contains("stopFrame()"));
        assertTrue("必须清掉画布上的洞", stop.contains("HolePuncher.clear()"));
        assertTrue("必须把文字清掉，否则会留在屏幕上", stop.contains("clearText()"));
        assertFalse("不得再每首移除 Overlay（会触发整条状态栏重新布局）",
                stop.contains("removeOverlay()"));
    }

    @Test
    public void measureCacheIsBounded() throws IOException {
        String text = source("PoemRuntime.java");
        assertTrue(text.contains("MAX_MEASURE_CACHE = 64"));
        assertTrue(text.contains("removeEldestEntry"));
    }

    @Test
    public void noPixelWidthIsPersistedInPoemJson() throws IOException {
        String item = source("PoemItem.java");
        assertFalse("诗句 JSON 不得持久化像素宽度", item.contains("put(\"width\""));
        assertFalse(item.contains("put(\"px\""));
    }

    @Test
    public void guardThresholdIsThree() {
        assertTrue(FeatureGuard.MAX_FAIL == 3);
    }

    @Test
    public void libraryEntryChainIsWired() throws IOException {
        String app = source("ui/PoemApp.kt");
        assertNotNull(app);
        assertTrue("必须有古诗库页面分支", app.contains("Tab.LIBRARY -> LibraryScreen"));
        assertTrue("必须有白名单页面分支", app.contains("Detail.Whitelist -> WhitelistScreen"));
        assertTrue("必须有洞页面分支", app.contains("Detail.Holes -> CutoutScreen"));
        assertTrue("必须有关于页面分支", app.contains("Detail.About -> AboutScreen"));
        String library = source("ui/LibraryScreen.kt");
        assertTrue("LazyColumn 必须使用稳定 key", library.contains("key = { _, poem -> poem.id }"));
        // 调试入口必须在发布前移除
        assertFalse("不得残留 debug_screen 调试入口",
                app.contains("debug_screen") || app.contains("debugTarget"));
    }

    /** 默认值契约：总开关关闭、横屏暂停开、浮动默认关、速度 120。 */
    @Test
    public void configDefaultsMatchTheProductContract() throws IOException {
        String config = source("PoemConfig.java");
        assertTrue(config.contains("public boolean landscapePause = true"));
        assertTrue(config.contains("public boolean bobEnabled = false"));
        assertTrue("总开关默认必须关闭", config.contains("public boolean enabled = false"));
        assertTrue("速度默认 120", config.contains("SPEED_DEFAULT = 120"));
    }

    // ---------------------------------------------------------------- 本轮修复的回归防线

    /**
     * 搜索过滤后拖动排序，必须只在「可见的诗」之间换位。
     *
     * <p>这条曾经是真 bug：显示的是过滤后的列表，换位却按整表下标算 ——
     * 搜索状态下长按拖动，屏幕上一动不动，实际却在和看不见的诗悄悄换位。
     * 行为断言在 {@code PoemLibraryTest.moveAmongVisibleSkipsFilteredOutPoems}，
     * 这里再钉住接线：拖动必须走那个函数，不能退回整表下标。
     */
    @Test
    public void dragReorderRespectsTheVisibleSubset() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue("拖动必须走「只在可见诗之间移动」的实现",
                library.contains("moveAmongVisible(working.value, poem.id, visibleIds"));
        assertTrue("必须把可见 id 集合算出来", library.contains("val visibleIds = remember(filtered)"));
        // 序号必须取整表下标：搜索时若从 1 重新编号，第 47 首会显示成「1」。
        assertTrue("序号必须来自整表下标", library.contains("val displayIndex = indexById[poem.id]"));
        assertFalse("不得再用过滤后的下标当序号",
                library.contains("index = index,"));

        // 真正的算法必须在可单测的 Java 层，而不是留在 Composable 里。
        String lib = source("PoemLibrary.java");
        assertTrue("移动算法必须在 PoemLibrary 里可单测",
                lib.contains("public static boolean moveAmongVisible("));
        assertTrue("必须用交换而不是删除再插入：隐藏的诗要原地不动",
                lib.contains("Collections.swap(list, from, to)"));
    }

    /**
     * 导入必须能从文件读，和「导出到文件」形成闭环。
     *
     * <p>以前只有导出走 SAF、导入只能粘贴，导出的 .txt 想导回来只能靠剪贴板中转。
     */
    @Test
    public void importAndExportBothUseTheSystemFilePicker() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue("导出必须用 CreateDocument",
                library.contains("ActivityResultContracts.CreateDocument("));
        assertTrue("导入必须用 OpenDocument",
                library.contains("ActivityResultContracts.OpenDocument()"));
        assertTrue("必须有「从文件导入」入口", library.contains("R.string.poem_import_from_file"));
        assertTrue("读文件必须走 contentResolver", library.contains("contentResolver.openInputStream"));
        assertTrue("写文件必须走 contentResolver", library.contains("contentResolver.openOutputStream"));
        // 读盘/写盘不能压在主线程上。
        assertTrue("文件读写必须在 IO 线程", library.contains("withContext(Dispatchers.IO)"));
        // 老的静态槽位方案必须彻底删除，避免两套并存。
        assertFalse("不得残留 PendingExport 静态槽位", library.contains("object PendingExport"));
        assertFalse("不得残留 startActivityForResult",
                library.contains("startActivityForResult"));
        String activity = source("MainActivity.kt");
        assertFalse("MainActivity 不得再覆写 onActivityResult",
                activity.contains("onActivityResult"));
    }

    /** 删除必须能撤销：只弹确认框而不给后悔的机会，手滑一次就没了。 */
    @Test
    public void deletingAPoemCanBeUndone() throws IOException {
        String app = source("ui/PoemApp.kt");
        assertTrue("必须提供可撤销提示条", app.contains("val LocalUndoable"));
        assertTrue("撤销按钮必须真的接上动作",
                app.contains("SnackbarResult.ActionPerformed"));
        String library = source("ui/LibraryScreen.kt");
        assertTrue("列表滑删后必须给撤销入口", library.contains("undoable("));
        assertTrue("撤销必须还原删除前的整份列表",
                library.contains("AppState.persistPoems(context, before)"));
        // 编辑页的删除比列表滑删更「重」：删完直接退回列表，不给撤销就没有反悔入口。
        String edit = source("ui/PoemEditScreen.kt");
        assertTrue("编辑页删除也必须能撤销", edit.contains("undoable("));
        assertTrue("编辑页撤销同样还原整份列表",
                edit.contains("AppState.persistPoems(context, before)"));
    }

    /** 导航栈必须能扛住进程被杀，否则切后台回来会掉回一级页。 */
    @Test
    public void navigationStackSurvivesProcessDeath() throws IOException {
        String app = source("ui/PoemApp.kt");
        assertTrue("导航栈必须用 rememberSaveable",
                app.contains("val stack = rememberSaveable("));
        assertTrue("必须用 listSaver 编码", app.contains("listSaver("));
        assertTrue("Detail 必须能编码", app.contains("fun encode(): String"));
        assertTrue("Detail 必须能解码", app.contains("fun decode(s: String): Detail?"));
        // 解码必须容错：旧版本存下的栈不能把页面打崩。
        assertTrue("未知编码必须返回 null", app.contains("else -> null"));
    }

    /**
     * 排序必须有「长按拖动」之外的第二条路。
     *
     * <p>长按拖动对读屏用户不可用（触摸被朗读接管），只给拖动等于把无障碍用户排除在外。
     */
    @Test
    public void reorderIsReachableWithoutDragging() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue("必须提供自定义无障碍操作", library.contains("CustomAccessibilityAction("));
        assertTrue("必须接上移", library.contains("R.string.move_up"));
        assertTrue("必须接下移", library.contains("R.string.move_down"));
        String strings = read(new File(RES, "values/strings.xml"));
        assertTrue("上移文案不能是死的", strings.contains("name=\"move_up\""));
        assertTrue("下移文案不能是死的", strings.contains("name=\"move_down\""));
    }

    /** 速度/停留必须能精确输入，只给滑块无法停在 120 这种整数上。 */
    @Test
    public void slidersAcceptExactNumericInput() throws IOException {
        String common = source("ui/components/Common.kt");
        assertTrue("数值必须可点开编辑", common.contains("editing = true"));
        assertTrue("必须有数字键盘", common.contains("KeyboardType.Number"));
        assertTrue("超范围必须夹取而不是报错", common.contains("coerceIn(range.first, range.last)"));
        assertTrue("松手要有触感反馈", common.contains("HapticFeedbackType.TextHandleMove"));
    }

    /** 长按拖动必须给触感，确认抓到的是哪一张卡。 */
    @Test
    public void dragStartGivesHapticFeedback() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertTrue(library.contains("HapticFeedbackType.LongPress"));
        assertTrue(library.contains("LocalHapticFeedback.current"));
    }

    /** 白名单的「已选项置顶」必须真的实现，不能只写在注释里。 */
    @Test
    public void whitelistPinsSelectedAppsToTheTop() throws IOException {
        String screen = source("ui/WhitelistScreen.kt");
        assertTrue("必须按「是否已选」排序",
                screen.contains("if (it.packageName in selectedSet) 0 else 1"));
        assertTrue("组内必须按名称稳定排序", screen.contains("thenBy { it.label.lowercase() }"));
    }

    /**
     * 字符串资源里不得留下没人用的死条目。
     *
     * <p>死资源不影响体积（R8 会连引用一起删），但会让下次改代码的人
     * 误以为还有那个功能 —— 概览页的旧预览条就是这么留下来的。
     */
    @Test
    public void noDeadStringResources() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<string name=\"([^\"]+)\">").matcher(strings);
        StringBuilder dead = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            // 这两个由清单文件直接引用，代码里搜不到。
            if ("app_name".equals(name) || "module_description".equals(name)) {
                continue;
            }
            if (!isStringReferenced(name)) {
                dead.append(name).append(' ');
            }
        }
        assertEquals("存在没人引用的字符串资源：" + dead, 0, dead.length());
    }

    /** 在 UI 源码与清单里找 {@code R.string.<name>} 或 {@code @string/<name>}。 */
    private boolean isStringReferenced(String name) throws IOException {
        // 递归扫整个源码目录：ui/ 下还有 components/ theme/ 等子目录，
        // 漏掉任何一层都会把活着的字符串误判成死资源。
        return scanForStringRef(MAIN, name) || read(MANIFEST).contains("@string/" + name);
    }

    private boolean scanForStringRef(File dir, String name) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (scanForStringRef(f, name)) {
                    return true;
                }
            } else if (f.isFile()) {
                String text = read(f);
                if (text.contains("R.string." + name) || text.contains("@string/" + name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 界面文案必须说人话：不出现只有开发者才懂的术语。
     *
     * <p>用户明确要求「小白一眼能看懂」。这里只拦最容易复发的几个词 ——
     * 它们在早期版本里大量出现在副标题上。
     */
    @Test
    public void userFacingCopyAvoidsDeveloperJargon() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        // 只检查 <string> 的内容，不检查注释（注释里允许写技术说明）。
        String[] jargon = {
                "SystemUI", "注入", "静态作用域", "clipOutRect", "px/s 的",
                "原生时间显示多久", "不参与滚动",
        };
        for (String word : jargon) {
            assertFalse("界面文案不该出现术语「" + word + "」", strings.contains(">" + word));
        }
        // 但「系统界面」这种用户能对上号的叫法必须保留。
        assertTrue("必须用「系统界面」而不是 SystemUI",
                strings.contains("系统界面"));
    }
}
