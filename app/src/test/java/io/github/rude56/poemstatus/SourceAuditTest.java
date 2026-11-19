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

    private static boolean avoidsIme(String text) {
        return text.contains("imePadding()")
                || (text.contains("contentWindowInsets") && text.contains("WindowInsets.ime"))
                || text.contains("WindowInsets.safeDrawing");
    }

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

        assertTrue(startCrawl.contains("colorSampler.sample"));

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

    @Test
    public void entryTitleAndSecondaryPageTitleExist() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        assertTrue("缺少古诗库页面标题", strings.contains("古诗库"));

        String dest = source("ui/component/bottombar/BottomBarDestination.kt");
        assertTrue("古诗库必须是一级 tab", dest.contains("R.string.tab_library"));
        assertTrue("概览必须是一级 tab", dest.contains("R.string.tab_overview"));
        assertTrue("设置必须是一级 tab", dest.contains("R.string.tab_settings"));
        String activity = source("MainActivity.kt");
        assertTrue("古诗库页面必须被渲染", activity.contains("1 -> LibraryScreen(bottomInnerPadding = bottomInnerPadding)"));
        assertTrue("概览页面必须被渲染", activity.contains("0 -> HomeScreen(bottomInnerPadding = bottomInnerPadding)"));
        assertTrue("设置页面必须被渲染", activity.contains("2 -> SettingsScreen(bottomInnerPadding = bottomInnerPadding)"));
    }

    @Test
    public void navigationIsTwoLevelMaterialStructure() throws IOException {
        String activity = source("MainActivity.kt");

        assertTrue("一级页必须用 Pager 承载三个 tab", activity.contains("HorizontalPager("));
        assertTrue("tab 数量必须恰好三个", activity.contains("MainPagerConfig.PAGE_COUNT"));
        assertTrue("三个 tab 必须逐个渲染", activity.contains("0 -> HomeScreen(")
                && activity.contains("1 -> LibraryScreen(")
                && activity.contains("2 -> SettingsScreen("));
        assertTrue("三个一级页都必须收到底部内边距",
                activity.contains("HomeScreen(bottomInnerPadding = bottomInnerPadding)")
                        && activity.contains("LibraryScreen(bottomInnerPadding = bottomInnerPadding)")
                        && activity.contains("SettingsScreen(bottomInnerPadding = bottomInnerPadding)"));

        assertTrue("二级页必须由 NavDisplay 承载", activity.contains("NavDisplay("));
        assertTrue("必须交给 NavDisplay 接管返回",
                activity.contains("onBack = { navigator.pop() }"));

        for (String route : new String[]{"Route.Status", "Route.Appearance", "Route.Holes",
                "Route.HoleEdit", "Route.HoleNew", "Route.Whitelist", "Route.About",
                "Route.PoemEdit"}) {
            assertTrue("二级页必须注册: " + route, activity.contains("entry<" + route + ">"));
        }

        assertTrue("根页必须禁用侧滑",
                activity.contains("entry<Route.Main>(swipeDismiss = NavSwipeDirection.None)"));

        assertTrue("底部导航必须只挂在一级页", activity.contains("bottomBar = bottomBar"));
    }

    @Test
    public void themeSupportsBothMaterialAndMiuix() throws IOException {

        String theme = source("ui/theme/Theme.kt");
        assertTrue("必须按 UiMode 分派两套主题",
                theme.contains("UiMode.Material -> MaterialTemplateTheme(")
                        && theme.contains("UiMode.Miuix -> MiuixTemplateTheme("));
        assertTrue("必须跟随系统深浅色", theme.contains("isSystemInDarkTheme()"));

        String material = source("ui/theme/MaterialTheme.kt");
        assertTrue("必须用 Material 3 的 lightColorScheme", material.contains("dynamicLightColorScheme"));
        assertTrue("必须用 Material 3 的 darkColorScheme", material.contains("dynamicDarkColorScheme"));
        assertTrue("必须接入 MaterialExpressiveTheme", material.contains("MaterialExpressiveTheme("));
        assertTrue("必须支持自定义种子色", material.contains("rememberDynamicColorScheme("));
        assertTrue("必须支持纯黑（AMOLED）", material.contains("isAmoled"));
        String miuix = source("ui/theme/MiuixTheme.kt");
        assertTrue("必须接入 MiuixTheme", miuix.contains("MiuixTheme("));
        assertTrue("Miuix 也必须支持壁纸取色", miuix.contains("ColorSchemeMode.MonetSystem"));
        assertTrue("Miuix 也必须跟随系统深浅色", miuix.contains("isSystemInDarkTheme()"));

        String activity = source("MainActivity.kt");
        assertTrue("MainActivity 必须使用 TemplateTheme", activity.contains("TemplateTheme("));
        assertTrue("必须边到边显示", activity.contains("enableEdgeToEdge"));
        assertFalse("不得再使用裸 MaterialTheme 包一层", activity.contains("MaterialTheme {"));
    }

    @Test
    public void overviewIsJustTheMasterSwitch() throws IOException {

        String screen = source("ui/screen/home/HomeScreen.kt");
        String state = source("ui/screen/home/HomeUiState.kt");
        String material = source("ui/screen/home/HomeMaterial.kt");
        String miuix = source("ui/screen/home/HomeMiuix.kt");

        assertTrue("必须区分正常状态", state.contains("OK,") && state.contains("HomeTone.OK"));
        assertTrue("必须区分关闭状态", state.contains("OFF,") && state.contains("HomeTone.OFF"));
        assertTrue("必须区分异常状态",
                state.contains("NOT_CONNECTED") && state.contains("NO_POEMS"));

        for (String skin : new String[]{"Material", "Miuix"}) {
            String text = skin.equals("Material") ? material : miuix;
            assertTrue(skin + " 必须按状态分派标题", text.contains("HomeTone.NOT_CONNECTED"));
            assertTrue(skin + " 必须按状态分派标题", text.contains("HomeTone.OK"));
        }
        assertFalse("状态栏效果预览已删除", material.contains("StatusBarStrip(")
                || miuix.contains("StatusBarStrip("));
        assertFalse("快捷入口已删除", material.contains("NavRow(") || miuix.contains("NavRow("));
        assertTrue("首次使用必须有引导", material.contains("R.string.overview_getting_started")
                && miuix.contains("R.string.overview_getting_started"));

        for (String f : new String[]{"ui/screen/home/HomeMaterial.kt",
                "ui/screen/home/HomeMiuix.kt"}) {
            String text = source(f);
            assertTrue(f + " 引导必须只在未连接时出现", text.contains("visible = !state.connected"));
            assertTrue(f + " 引导出现/消失必须有过渡",
                    text.contains("AnimatedVisibility(")
                            && text.contains("expandVertically()")
                            && text.contains("shrinkVertically()"));
        }

        assertTrue("必须提示阻塞原因", material.contains("R.string.overview_empty")
                && miuix.contains("R.string.overview_empty"));
        assertTrue("必须有连接自检", state.contains("val connected: Boolean"));
        assertTrue("必须有配置速览", material.contains("ConfigSummaryColumn(")
                && miuix.contains("ConfigSummaryCard("));

        int notConnectedBranch = material.indexOf("visible = !state.connected");
        int summary = material.indexOf("ConfigSummaryColumn(state)");
        assertTrue("未连接分支必须存在", notConnectedBranch >= 0);
        assertTrue("配置速览必须挂在「已连接」分支里，不能无条件显示",
                summary > notConnectedBranch);
        assertTrue("状态卡必须可点进状态页", screen.contains("onOpenStatus = { navigator.push(Route.Status) }"));
    }
    @Test
    public void connectionCheckDetectsWhetherTheModuleIsAlive() throws IOException {
        String provider = source("ConfigProvider.java");
        assertTrue("必须记录最后调用时刻", provider.contains("lastSystemUiCallAt"));
        assertTrue("必须用 elapsedRealtime", provider.contains("elapsedRealtime()"));
        assertTrue("必须校验调用方是 SystemUI", provider.contains("getCallingPackage()"));
        assertTrue("必须与作用域包名比对", provider.contains("FeatureAudit.SCOPE_PACKAGE"));
        assertTrue("必须提供读取入口", provider.contains("public static long lastSystemUiCallAt()"));

        String conn = source("ui/util/ConnectionStatus.kt");
        assertTrue("共享文件必须读这个时间戳",
                conn.contains("ConfigProvider.lastSystemUiCallAt()"));
        assertTrue("共享文件必须有超时判定", conn.contains("secondsAgo in 0..10"));

        for (String f : new String[]{"ui/screen/home/HomeScreen.kt", "ui/screen/status/StatusScreen.kt"}) {
            String text = source(f);
            assertTrue(f + " 必须用共享的连接判定", text.contains("isConnected(secondsAgo)"));
            assertTrue(f + " 必须同步读初始值", text.contains("mutableLongStateOf(readSecondsAgo())"));
            assertTrue(f + " 必须按 2 秒轮询", text.contains("delay(2000)"));
        }

        String material = source("ui/screen/home/HomeMaterial.kt");
        String miuix = source("ui/screen/home/HomeMiuix.kt");
        for (String skin : new String[]{"Material", "Miuix"}) {
            String text = skin.equals("Material") ? material : miuix;
            assertTrue(skin + " 必须有已连接文案", text.contains("R.string.status_ok"));
            assertTrue(skin + " 必须有未连接文案", text.contains("R.string.status_not_connected"));
            assertTrue(skin + " 必须给出启用步骤", text.contains("R.string.step_enable_title"));
            assertTrue(skin + " 必须给出诗库步骤", text.contains("R.string.step_library_title"));
            assertTrue(skin + " 必须给出设洞步骤", text.contains("R.string.step_holes_title"));
        }
    }

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

    @Test
    public void legacyModuleDeclarationIsGone() throws IOException {
        String manifest = read(MANIFEST);

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

    @Test
    public void moduleEntryOnlyHandlesSystemUi() throws IOException {
        String text = source("ModuleMain.java");
        assertTrue("必须按包名过滤",
                text.contains("FeatureAudit.SCOPE_PACKAGE.equals(param.getPackageName())"));
        assertTrue("必须打 INJECTED 日志", text.contains("INJECTED process="));
        assertTrue("必须继承现代入口基类", text.contains("extends XposedModule"));

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

    @Test
    public void alphaOcclusionSchemeIsGone() throws IOException {
        assertFalse("旧方案类必须已删除", new File(MAIN, "ModuleOccluder.java").isFile());
        assertFalse("模式开关必须已删除", new File(MAIN, "OcclusionMode.java").isFile());
        assertFalse("只被旧方案使用的规划器必须已删除",
                new File(MAIN, "OcclusionPlanner.java").isFile());

        for (String f : new String[]{"PoemRuntime.java", "SystemUiController.java",
                "ColorSampler.java", "HolePuncher.java"}) {
            String text = source(f);
            assertFalse(f + " 不得再引用 ModuleOccluder", text.contains("ModuleOccluder"));
            assertFalse(f + " 不得再引用 OcclusionMode", text.contains("OcclusionMode"));
        }
        String runtime = source("PoemRuntime.java");
        assertFalse("诗句层不得改别人 alpha", runtime.contains("setAlpha"));
    }

    @Test
    public void holePunchIsTheOnlyOcclusionScheme() throws IOException {
        String hole = source("HolePuncher.java");
        assertTrue("必须用 clipOutRect 挖洞", hole.contains("clipOutRect"));

        assertTrue("必须用 ClipStack 管理 save/restore", hole.contains("ClipStack"));
        assertTrue("必须 restoreToCount 到本层保存点", hole.contains("restoreToCount"));

        for (String line : hole.split("\n")) {
            String code = line.trim();
            if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) {
                continue;
            }
            assertFalse("不得再退回单一计数: " + code, code.contains("savedClips"));
        }

        assertTrue("必须校验目标就是内容根", hole.contains("view != root"));

        assertTrue("必须节流", hole.contains("INVALIDATE_INTERVAL_MS"));

        String tree = source("ViewTree.java");
        String findRoot = bodyOf(tree, "private static View lookupContentRoot(View window)");
        assertTrue("必须优先绑 status_bar_container",
                findRoot.contains("status_bar_container"));

        String cached = bodyOf(tree, "static View findContentRoot(View window)");
        assertTrue("内容根必须缓存", cached.contains("cachedRoot"));
        assertTrue("缓存必须在窗口重建时失效", cached.contains("cachedWindow == window"));

        String runtime = source("PoemRuntime.java");
        String onFrame = bodyOf(runtime,
                "void updateOcclusion(float l, float t, float r, float b)");
        assertFalse(onFrame.contains("Log."));
    }
    @Test
    public void colorComesFromSystemTintNotHeuristics() throws IOException {
        String hook = source("SystemUiController.java");
        assertTrue("必须 Hook 系统的图标 tint", hook.contains("mIconTint"));
        assertTrue(hook.contains("DarkIconDispatcherImpl"));
        assertTrue(hook.contains("applyIconTint"));
        String sampler = source("ColorSampler.java");
        assertTrue("取色必须优先用系统 tint", sampler.contains("TintState.tint()"));
        assertFalse("不得读壁纸", sampler.contains("WallpaperManager"));

        String onFrame = bodyOf(hook, "public void onFrame(float left, float top, float right, float bottom)");
        assertFalse(onFrame.contains("colorSampler"));
        assertFalse(onFrame.contains("TintState"));
    }

    @Test
    public void wormholeSegmentsAreComputedExactlyOnce() throws IOException {
        String rt = source("PoemRuntime.java");
        int first = rt.indexOf("HoleRects.wormholeSegments");
        assertTrue("必须调用 wormholeSegments", first >= 0);
        assertEquals("分段只能算一次（绘制层与遮挡层共用结果）",
                -1, rt.indexOf("HoleRects.wormholeSegments", first + 1));

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

        String rt = source("PoemRuntime.java");
        String frame = bodyOf(rt, "private void updateFrame(long frameTimeNanos)");
        assertTrue("位置必须由时间算出", frame.contains("viewW - traveled"));
        assertFalse("帧内不得调用任何洞位移函数", frame.contains("shiftForWormhole"));
        assertFalse(frame.contains("wormholeShift"));
        assertFalse("帧内不得引用 HolePuncher", frame.contains("HolePuncher"));

        assertFalse("位移函数应已移除",
                source("HolePuncher.java").contains("static float shiftForWormhole"));
    }
    @Test
    public void holesOnlyAffectTextDrawing() throws IOException {
        String rt = source("PoemRuntime.java");
        String draw = bodyOf(rt, "private void drawTextWithHoles(Canvas canvas)");

        assertTrue("隧道必须 clipOutRect", draw.contains("clipOutRect"));
        assertTrue("必须正常绘制文字", draw.contains("canvas.drawText"));

        assertTrue("虫洞必须分段平移", draw.contains("canvas.translate"));
        assertTrue("绘制必须复用帧里算好的分段", draw.contains("segCount"));

        assertTrue("缓冲长度必须受 MAX_HOLES 约束",
                rt.contains("new float[HoleRects.MAX_HOLES + 1]"));
    }

    @Test
    public void holesNeverPunchThroughTheStatusBar() throws IOException {

        String hole = source("HolePuncher.java");
        String begin = bodyOf(hole, "static boolean beginClip(Canvas canvas, View view)");
        assertTrue("必须按可见分段挖", begin.contains("segLeft[i]"));
        assertFalse("不得直接按用户洞列表挖", begin.contains("holes.get(i)"));

        assertFalse("位移函数应已删除", hole.contains("shiftForWormhole"));
    }

    @Test
    public void tunnelDoesNotOccludeStatusBar() throws IOException {

        String rt = source("PoemRuntime.java");
        String upd = bodyOf(rt, "void updateOcclusion(float l, float t, float r, float b)");
        assertTrue("遮挡必须走 visibleSegments", upd.contains("HoleRects.visibleSegments"));
        assertFalse("运行时不得重算隧道减法", upd.contains("subtractTunnels"));
        assertTrue("必须使用诗句层屏幕原点", upd.contains("screenOriginX"));
        String holes = source("HoleRects.java");
        String vis = bodyOf(holes, "public static int visibleSegments(");
        assertTrue("可见分段必须减掉隧道", vis.contains("subtractTunnels"));
        String sub = bodyOf(holes, "public static int subtractTunnels(");
        assertTrue("只减隧道洞", sub.contains("h.isWormhole()"));
        assertTrue("必须跳过隧道区间", sub.contains("cursor = Math.max(cursor, hr)"));

        assertFalse("不得在帧内分配", sub.contains("new "));
        assertFalse("可见分段也不得分配", vis.contains("new "));
    }

    @Test
    public void holeFramesAreDrawnByTheAppItself() throws IOException {
        String overlay = source("ui/HoleFrameOverlay.kt");

        assertTrue("必须读状态栏高度", overlay.contains("WindowInsets.statusBars"));
        assertTrue("必须用 statusBars.getTop", overlay.contains("statusBars.getTop"));

        assertTrue("颜色必须按槽位取", overlay.contains("holeColor(hole.slot)"));

        assertFalse("框内不得画序号", overlay.contains("drawLabel") || overlay.contains("measurer"));
        assertTrue("必须支持编辑中的草稿", overlay.contains("draft: HoleDraft? = null"));
        assertTrue("草稿持有者必须存在", overlay.contains("object HoleDraftHolder"));

        assertTrue("只填左边界时必须画竖线", overlay.contains("l != null && r == null"));
        assertTrue("只填右边界时必须画竖线", overlay.contains("r != null && l == null"));
        assertTrue("两边都填时必须画矩形", overlay.contains("l != null && r != null && r > l"));

        assertTrue("编辑页必须推草稿",
                source("ui/screen/edit/HoleEditScreen.kt").contains("HoleDraftHolder.draft = HoleDraft("));

        assertTrue("画框层必须收到草稿",
                source("MainActivity.kt").contains("draft = HoleDraftHolder.draft"));

        String material = source("ui/screen/holes/CutoutMaterial.kt");
        String miuix = source("ui/screen/holes/CutoutMiuix.kt");
        assertTrue("洞页必须显示两种穿越样例", material.contains("HoleModeSamples(")
                && miuix.contains("HoleModeSamples("));

        for (String f : new String[]{"ui/screen/holes/CutoutMaterial.kt",
                "ui/screen/holes/CutoutMiuix.kt"}) {
            assertTrue(f + " 必须给样例传主题色", source(f).contains("holeColor = "));
        }

        String ctl = source("SystemUiController.java");
        assertFalse("不得再靠 App 是否前台来判断", ctl.contains("isSelfForeground"));
        for (String f : new String[]{"ui/screen/holes/CutoutScreen.kt",
                "ui/screen/holes/CutoutMaterial.kt", "ui/screen/holes/CutoutMiuix.kt"}) {
            String text = source(f);
            assertFalse(f + " 不得再写 cutoutPreview 标志", text.contains("cutoutPreview = true"));
            assertFalse(f + " 不得再有「在状态栏显示框」开关",
                    text.contains("R.string.hole_show_on_status_bar"));
        }
    }

    @Test
    public void holesPageShowsTwoFixedSamples() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertTrue("必须同时演示隧道", samples.contains("HoleRects.Mode.TUNNEL"));
        assertTrue("必须同时演示虫洞", samples.contains("HoleRects.Mode.WORMHOLE"));
        assertTrue("虫洞必须复用真实分段算法", samples.contains("HoleRects.wormholeSegments"));
        assertTrue("洞位置必须写死为常量",
                samples.contains("HOLE_LEFT_FRAC = 0.30f")
                        && samples.contains("HOLE_RIGHT_FRAC = 0.62f"));
        assertTrue("两个样例必须共用进度", samples.contains("progress = progress"));
        assertTrue("样例必须是白底", samples.contains("Color.White"));

        assertTrue("洞口标记必须可传主题色",
                samples.contains("holeColor: Color = MaterialTheme.colorScheme.tertiary"));
        assertFalse("洞口标记不得再写死灰色",
                samples.contains("Color(0xFF1A1C1E).copy(alpha = 0.06f)"));

        for (String f : new String[]{"ui/screen/holes/CutoutMaterial.kt",
                "ui/screen/holes/CutoutMiuix.kt"}) {
            assertTrue(f + " 必须显示两个样例", source(f).contains("HoleModeSamples("));
        }
    }
    @Test
    public void bothPreviewsAlwaysShowAPoemAtTheSameTime() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertTrue("必须用 PreviewClock 推进", samples.contains("PreviewClock.progressAt"));
        assertFalse("不得再 coerceIn 到 [0,1]（会永久冻结在末尾）",
                samples.contains("coerceIn(0f, 1f)"));
        assertTrue("必须用 cycleEnd 收缩循环范围", samples.contains("PreviewClock.cycleEnd"));
        assertTrue("进度必须映射到 [0, cycleEnd)",
                samples.contains("p * cycleEnd"));
        assertTrue("两行必须共用画笔", samples.contains("paint: Paint"));
        assertTrue("文字宽度必须只测一次", samples.contains("val textWidth = remember"));
        assertTrue("两行必须共用画布宽度", samples.contains("onWidth(w)"));
        assertTrue(new File(MAIN, "PreviewClock.java").isFile());
    }

    @Test
    public void holesPageUsesTheSameUiAsTheLibrary() throws IOException {

        String screen = source("ui/screen/holes/CutoutScreen.kt");
        assertTrue("必须点卡片进编辑页", screen.contains("onEdit = { slot -> navigator.push(Route.HoleEdit(slot)) }"));

        assertTrue("列表页必须有启停开关", screen.contains("onToggle"));
        assertFalse("列表页不得有删除", screen.contains("onDelete"));

        assertTrue("新增必须进编辑页", screen.contains("navigator.push(Route.HoleNew)"));
        for (String f : new String[]{"ui/screen/holes/CutoutMaterial.kt",
                "ui/screen/holes/CutoutMiuix.kt"}) {
            assertTrue(f + " 必须有新增入口", source(f).contains("R.string.hole_add"));
        }

        for (String f : new String[]{"ui/screen/holes/CutoutScreen.kt",
                "ui/screen/holes/CutoutMaterial.kt", "ui/screen/holes/CutoutMiuix.kt"}) {
            assertFalse(f + " 列表里不得有输入框", source(f).contains("TextField("));
        }

        for (String f : new String[]{"ui/screen/edit/HoleEditMaterial.kt",
                "ui/screen/edit/HoleEditMiuix.kt"}) {
            String edit = source(f);
            assertTrue(f + " 编辑页必须避让键盘", avoidsIme(edit));
            assertTrue(f + " 编辑页必须能删除", edit.contains("R.string.delete"));

            assertTrue(f + " 新增模式必须隐藏删除", edit.contains("if (!state.isNew)"));
        }

        String poemEdit = source("ui/screen/edit/PoemEditScreen.kt");
        assertTrue("删除必须先确认", poemEdit.contains("rememberConfirmDialog()"));
        assertTrue("确认必须等用户点完再继续", poemEdit.contains("awaitConfirm("));
        assertTrue("洞删除也必须先确认",
                source("ui/screen/edit/HoleEditScreen.kt").contains("awaitConfirm("));
    }

    @Test
    public void addingHoleNeverRecolorsExistingOnes() throws IOException {
        String cutout = source("ui/screen/holes/CutoutScreen.kt");

        assertTrue("新洞必须取空闲槽位",
                source("ui/screen/edit/HoleEditScreen.kt").contains("HoleRects.nextFreeSlot("));

        String holes = source("HoleRects.java");
        assertTrue("Hole 必须带槽位", holes.contains("public final int slot;"));
        assertTrue("必须能生成新槽位", holes.contains("public static int nextFreeSlot("));
        assertTrue("解析时必须补齐槽位", holes.contains("assignSlots("));
        assertTrue("解析时必须去重", holes.contains("sanitize("));

        assertTrue("编码必须写入槽位", holes.contains(".append(h.slot)"));
    }

    @Test
    public void cutoutIsNumericInputNotDragging() throws IOException {
        String logic = source("ui/screen/edit/HoleEditScreen.kt");
        String material = source("ui/screen/edit/HoleEditMaterial.kt");
        String miuix = source("ui/screen/edit/HoleEditMiuix.kt");
        assertTrue("必须校验右边界 > 左边界", logic.contains("r <= l"));
        assertTrue("Material 必须声明为数字输入", material.contains("KeyboardType.Number"));
        assertTrue("Miuix 必须声明为数字输入", miuix.contains("SuperEditArrow("));

        assertTrue("数字键盘必须只允许数字",
                source("ui/component/miuix/SuperEditArrow.kt").contains("KeyboardType.Number"));

        String screen = source("ui/screen/holes/CutoutScreen.kt");
        assertTrue("必须能添加", screen.contains("onAdd = { addHole() }"));
        for (String f : new String[]{"ui/screen/holes/CutoutMaterial.kt",
                "ui/screen/holes/CutoutMiuix.kt"}) {
            assertTrue(f + " 必须有新增入口", source(f).contains("R.string.hole_add"));
        }
        assertTrue("每个洞可选虫洞/隧道", logic.contains("HoleRects.Mode.WORMHOLE")
                && logic.contains("HoleRects.Mode.TUNNEL"));
        assertTrue("不同洞用不同颜色", material.contains("holeColor") && miuix.contains("holeColor"));
        assertTrue("删除必须确认", source("ui/screen/edit/PoemEditScreen.kt")
                .contains("rememberConfirmDialog()"));

        assertFalse("不得再有清空全部", screen.contains("R.string.hole_clear"));
        assertFalse("校准 Activity 应已移除",
                new File(MAIN, "CutoutCalibrationActivity.java").isFile());
        String manifest = read(MANIFEST);
        assertFalse("Manifest 不得再声明校准 Activity",
                manifest.contains("CutoutCalibrationActivity"));
    }

    @Test
    public void libraryScreenIsTappableRowWithStableKey() throws IOException {

        String logic = source("ui/screen/library/LibraryScreen.kt");
        String material = source("ui/screen/library/LibraryMaterial.kt");
        String miuix = source("ui/screen/library/LibraryMiuix.kt");
        for (String skin : new String[]{"Material", "Miuix"}) {
            String text = skin.equals("Material") ? material : miuix;
            assertTrue(skin + " 的 LazyColumn 必须使用稳定 key",
                    text.contains("key = { _, poem -> poem.id }"));
            assertTrue(skin + " 必须有启用开关", text.contains("Switch(")
                    && text.contains("poem.enabled"));
        }
        assertTrue("整行可点即编辑", material.contains("onClick = onEdit")
                && miuix.contains("onClick = onEdit"));
        assertFalse("不得再用编辑对话框", logic.contains("pendingDelete"));
        assertTrue("新增必须走二级页", logic.contains("navigator.push(Route.PoemEdit(null))"));
        for (String f : new String[]{"ui/screen/edit/PoemEditMaterial.kt",
                "ui/screen/edit/PoemEditMiuix.kt"}) {
            String edit = source(f);
            assertTrue(f + " 编辑页必须避让键盘", avoidsIme(edit));
            assertTrue(f + " 编辑页必须能删除已有诗", edit.contains("R.string.delete"));
        }
    }
    @Test
    public void librarySupportsDeleteAndTextImportExport() throws IOException {

        String logic = source("ui/screen/library/LibraryScreen.kt");
        assertTrue("必须有导入入口", logic.contains("R.string.poem_import"));
        assertTrue("必须有导出入口", logic.contains("R.string.poem_export"));
        assertTrue("导入必须走纯文本编解码", logic.contains("PoemTextCodec.decode"));
        assertTrue("导出必须走纯文本编解码", logic.contains("PoemTextCodec.encode"));

        String edit = source("ui/screen/edit/PoemEditScreen.kt");
        assertTrue("必须能删除", edit.contains("onDelete"));
        assertTrue("删除必须先确认", edit.contains("rememberConfirmDialog()"));
        assertTrue("删除必须等用户点完再继续", edit.contains("awaitConfirm("));

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

        String activity = source("MainActivity.kt");
        assertNotNull(activity);
        assertTrue("必须有古诗库页面分支", activity.contains("1 -> LibraryScreen("));
        assertTrue("必须有白名单页面分支", activity.contains("entry<Route.Whitelist>"));
        assertTrue("必须有洞页面分支", activity.contains("entry<Route.Holes>"));
        assertTrue("必须有关于页面分支", activity.contains("entry<Route.About>"));
        assertTrue("必须有外观页面分支", activity.contains("entry<Route.Appearance>"));
        assertTrue("必须有状态页面分支", activity.contains("entry<Route.Status>"));
        String library = source("ui/screen/library/LibraryMaterial.kt");
        assertTrue("LazyColumn 必须使用稳定 key", library.contains("key = { _, poem -> poem.id }"));

        assertFalse("不得残留 debug_screen 调试入口",
                activity.contains("debug_screen") || activity.contains("debugTarget"));
    }

    @Test
    public void configDefaultsMatchTheProductContract() throws IOException {
        String config = source("PoemConfig.java");
        assertTrue(config.contains("public boolean landscapePause = true"));
        assertTrue(config.contains("public boolean bobEnabled = false"));
        assertTrue("总开关默认必须关闭", config.contains("public boolean enabled = false"));
        assertTrue("速度默认 120", config.contains("SPEED_DEFAULT = 120"));
    }

    @Test
    public void dragReorderRespectsTheVisibleSubset() throws IOException {
        String library = source("ui/screen/library/LibraryScreen.kt");
        assertTrue("拖动必须走「只在可见诗之间移动」的实现",
                library.contains("moveAmongVisible(working.value, id, visibleIds, 1)"));
        assertTrue("必须把可见 id 集合算出来", library.contains("val visibleIds = remember(filtered)"));

        assertTrue("序号必须来自整表下标", library.contains("state.indexById[poem.id]")
                || source("ui/screen/library/LibraryMaterial.kt")
                .contains("state.indexById[poem.id]"));
        assertFalse("不得再用过滤后的下标当序号", library.contains("index = index,"));

        String lib = source("PoemLibrary.java");
        assertTrue("移动算法必须在 PoemLibrary 里可单测",
                lib.contains("public static boolean moveAmongVisible("));
        assertTrue("必须用交换而不是删除再插入：隐藏的诗要原地不动",
                lib.contains("Collections.swap(list, from, to)"));
    }

    @Test
    public void importAndExportBothUseTheSystemFilePicker() throws IOException {
        String library = source("ui/screen/library/LibraryScreen.kt");
        assertTrue("导出必须用 CreateDocument",
                library.contains("ActivityResultContracts.CreateDocument("));
        assertTrue("导入必须用 OpenDocument",
                library.contains("ActivityResultContracts.OpenDocument()"));
        assertTrue("必须有「从文件导入」入口",
                source("ui/screen/library/LibraryMaterial.kt").contains("R.string.poem_import_from_file")
                        && source("ui/screen/library/LibraryMiuix.kt").contains("R.string.poem_import_from_file"));
        assertTrue("读文件必须走 contentResolver", library.contains("contentResolver.openInputStream"));
        assertTrue("写文件必须走 contentResolver", library.contains("contentResolver.openOutputStream"));
        assertTrue("文件读写必须在 IO 线程", library.contains("withContext(Dispatchers.IO)"));
        assertFalse("不得残留 PendingExport 静态槽位", library.contains("object PendingExport"));
        assertFalse("不得残留 startActivityForResult",
                library.contains("startActivityForResult"));
        String activity = source("MainActivity.kt");
        assertFalse("MainActivity 不得再覆写 onActivityResult",
                activity.contains("onActivityResult"));
    }

    @Test
    public void navigationStackSurvivesProcessDeath() throws IOException {
        String stack = source("ui/navigation3/RouteStack.kt");
        assertTrue("导航栈必须用 rememberSaveable", stack.contains("rememberSaveable(saver = saver)"));
        assertTrue("必须用 listSaver 编码", stack.contains("listSaver"));

        assertTrue("空栈必须补根页", stack.contains("routes.add(Route.Main)"));

        String route = source("ui/navigation3/Route.kt");
        assertTrue("Route 必须能编码", route.contains("fun encode(): String"));
        assertTrue("Route 必须能解码", route.contains("fun decode(s: String): Route?"));

        assertTrue("未知编码必须返回 null", route.contains("else -> null"));
    }

    @Test
    public void reorderIsReachableWithoutDragging() throws IOException {

        String library = source("ui/screen/library/LibraryScreen.kt");
        assertTrue("必须提供自定义无障碍操作", library.contains("CustomAccessibilityAction("));
        assertTrue("必须接上移", library.contains("R.string.move_up"));
        assertTrue("必须接下移", library.contains("R.string.move_down"));
        String strings = read(new File(RES, "values/strings.xml"));
        assertTrue("上移文案不能是死的", strings.contains("name=\"move_up\""));
        assertTrue("下移文案不能是死的", strings.contains("name=\"move_down\""));
    }

    @Test
    public void slidersAcceptExactNumericInput() throws IOException {

        String arrow = source("ui/component/miuix/SuperEditArrow.kt");
        assertTrue("数值必须可点开编辑", arrow.contains("showDialog.value = true"));
        assertTrue("必须有数字键盘", arrow.contains("KeyboardType.Number"));

        assertTrue("必须过滤非数字输入", arrow.contains("FilterNumber("));

        assertTrue("Material 洞编辑页必须用数字键盘",
                source("ui/screen/edit/HoleEditMaterial.kt").contains("KeyboardType.Number"));
        assertTrue("Miuix 洞编辑页必须用可编辑的数值行",
                source("ui/screen/edit/HoleEditMiuix.kt").contains("SuperEditArrow("));
    }

    @Test
    public void dragStartGivesHapticFeedback() throws IOException {
        String library = source("ui/screen/library/LibraryScreen.kt");
        assertTrue(library.contains("HapticFeedbackType.LongPress"));
        assertTrue(library.contains("LocalHapticFeedback.current"));
    }

    @Test
    public void whitelistPinsSelectedAppsToTheTop() throws IOException {
        String screen = source("ui/screen/whitelist/WhitelistScreen.kt");
        assertTrue("必须按「是否已选」排序",
                screen.contains("compareBy<AppState.AppEntry> { if (it.packageName in selectedSet) 0 else 1 }"));
        assertTrue("组内必须按名称稳定排序", screen.contains("thenBy { it.label.lowercase() }"));
    }

    private static final java.util.Set<String> PENDING_STRING_CLEANUP = new java.util.HashSet<>(
            java.util.Arrays.asList(

            ));

    @Test
    public void stringPlaceholderCountsMatchCallSites() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));

        java.util.Map<String, Integer> arity = new java.util.HashMap<>();
        java.util.regex.Matcher entry = java.util.regex.Pattern
                .compile("<string name=\"([^\"]+)\">(.*?)</string>", java.util.regex.Pattern.DOTALL)
                .matcher(strings);
        while (entry.find()) {
            String name = entry.group(1);
            String body = entry.group(2);
            java.util.regex.Matcher ph = java.util.regex.Pattern
                    .compile("%(\\d+)\\$").matcher(body);
            int max = 0;
            while (ph.find()) {
                max = Math.max(max, Integer.parseInt(ph.group(1)));
            }
            arity.put(name, max);
        }

        StringBuilder bad = new StringBuilder();
        scanStringCalls(MAIN, arity, bad);
        assertEquals("字符串占位符个数和调用处不匹配：" + bad, 0, bad.length());
    }

    private void scanStringCalls(File dir, java.util.Map<String, Integer> arity,
                                 StringBuilder bad) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                scanStringCalls(f, arity, bad);
            } else if (f.isFile() && f.getName().endsWith(".kt")) {
                String text = read(f);

                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("R\\.string\\.(\\w+)").matcher(text);
                while (m.find()) {
                    String name = m.group(1);
                    Integer need = arity.get(name);
                    if (need == null || need == 0) {
                        continue;
                    }

                    int open = text.lastIndexOf('(', m.start());
                    if (open < 0) {
                        continue;
                    }
                    String between = text.substring(open + 1, m.start()).trim();
                    if (!between.isEmpty()) {

                        continue;
                    }
                    int depth = 1;
                    int commas = 0;
                    boolean sawContent = false;
                    int i = m.end();
                    for (; i < text.length() && depth > 0; i++) {
                        char c = text.charAt(i);
                        if (c == '(' || c == '[' || c == '{') {
                            depth++;
                            sawContent = true;
                        } else if (c == ')' || c == ']' || c == '}') {
                            depth--;
                        } else if (c == ',' && depth == 1) {
                            int j = i + 1;
                            while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                                j++;
                            }
                            if (j < text.length() && text.charAt(j) == ')') {
                                continue;
                            }
                            commas++;
                        } else if (!Character.isWhitespace(c)) {
                            sawContent = true;
                        }
                    }

                    int passed = commas;
                    if (passed != need) {
                        bad.append(f.getName()).append(':').append(name)
                           .append("(传 ").append(passed).append(" 个, 需要 ")
                           .append(need).append(") ");
                    }
                }
            }
        }
    }

    @Test
    public void noDeadStringResources() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<string name=\"([^\"]+)\">").matcher(strings);
        StringBuilder dead = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            if ("app_name".equals(name) || "module_description".equals(name)) {
                continue;
            }
            if (PENDING_STRING_CLEANUP.contains(name)) {
                continue;
            }
            if (!isStringReferenced(name)) {
                dead.append(name).append(' ');
            }
        }
        assertEquals("存在没人引用的字符串资源：" + dead, 0, dead.length());
    }

    private boolean isStringReferenced(String name) throws IOException {

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
    @Test
    public void userFacingCopyAvoidsDeveloperJargon() throws IOException {
        String strings = read(new File(RES, "values/strings.xml"));
        String[] jargon = {
                "SystemUI", "注入", "静态作用域", "clipOutRect", "px/s 的",
                "原生时间显示多久", "不参与滚动",
        };
        for (String word : jargon) {
            assertFalse("界面文案不该出现术语「" + word + "」", strings.contains(">" + word));
        }
        assertTrue("必须用「系统界面」而不是 SystemUI",
                strings.contains("系统界面"));
    }

    @Test
    public void libraryRowStaysInSyncWithStatusBar() throws IOException {
        String util = source("ui/util/MarqueeText.kt");
        assertTrue("周期正文必须在尾部追加恰好十个空格",
                util.contains("MarqueeCycle.text(text)") && source("MarqueeCycle.java").contains("GAP_SPACES = 10"));
        assertTrue("每轮开始前必须等待一秒",
                source("MarqueeCycle.java").contains("PAUSE_MILLIS = 1000L")
                        && util.contains("delay(cyclePauseMs.coerceAtLeast(0L))"));
        assertTrue("滚动周期宽度必须来自第一份实际排版", util.contains("cycleWidthPx = it.size.width"));
        assertTrue("必须绘制第二周期无障碍隐藏副本", util.contains("modifier = Modifier.clearAndSetSemantics"));
        assertTrue("偏移必须始终钳位在周期内", source("MarqueeCycle.java").contains("Math.min(cycleWidth, position"));
        for (String f : new String[]{"ui/screen/library/LibraryMaterial.kt",
                "ui/screen/library/LibraryMiuix.kt"}) {
            String text = source(f);
            assertTrue(f + " 行文本必须和状态栏同源（displayText）",
                    text.contains("poem.displayText(showAuthor, showSource)"));
            assertTrue(f + " 序号与滚动文字必须是独立 sibling，文字使用剩余权重宽度",
                    text.contains("Modifier.widthIn(min = 16.dp)")
                            && text.contains("modifier = Modifier.weight(1f)"));
        }
        String material = source("ui/screen/library/LibraryMaterial.kt");
        String miuix = source("ui/screen/library/LibraryMiuix.kt");

        assertTrue("Material 诗库行必须使用分段形状", material.contains("SegmentedItem(index = visibleIndex")
                && bodyOf(material, "private fun PoemRow(").contains("SegmentedListItem("));
        assertTrue("文字必须左对齐", util.contains("textAlign = TextAlign.Left")
                && util.contains("wrapContentWidth(align = Alignment.Start, unbounded = true)"));
        assertTrue("必须绘制第二周期保证循环连续", util.contains("modifier = Modifier.clearAndSetSemantics"));
        assertTrue("周期间必须暂停 1 秒", util.contains("delay(cyclePauseMs.coerceAtLeast(0L)"));
        assertFalse("不得保留旧的首轮 2 秒延迟", util.contains("startDelayMs"));
        assertTrue("Material 对话框为 4 行", material.contains("noWrapLines = true,")
                && material.contains("minLines = 4,"));
        assertTrue("Miuix 对话框宽 560dp、输入 6 行", miuix.contains("maxWidth = 560.dp,")
                && miuix.contains("minLines = 6,"));

        String uiState = source("ui/screen/library/LibraryUiState.kt");
        assertTrue("诗库状态必须带 showAuthor/showSource",
                uiState.contains("val showAuthor: Boolean") && uiState.contains("val showSource: Boolean"));
        String screen = source("ui/screen/library/LibraryScreen.kt");
        assertTrue("诗库必须从播放配置读开关",
                screen.contains("showAuthor = cfg.showAuthor")
                        && screen.contains("showSource = cfg.showSource"));

        String overlay = source("ui/HoleFrameOverlay.kt");
        assertTrue("预览必须按 enabled 分色",
                overlay.contains("if (hole.enabled) 0.28f else 0.10f"));
    }

    @Test
    public void restartFailurePromptsForRootAuthorization() throws IOException {
        String status = source("ui/screen/status/StatusScreen.kt");
        String helper = source("RestartHelper.kt");
        String resources = read(new File(RES, "values/strings.xml"));
        assertTrue("权限拒绝结果必须显示给用户", status.contains("RestartResult.ROOT_DENIED")
                && status.contains("Toast.makeText(context, messageRes"));
        assertTrue("必须先探测并验证 root UID", helper.contains("runSu(\"id -u\", rootProbe = true)"));
        assertTrue("授权拒绝提示必须给出可操作步骤",
                resources.contains("Root 管理器中允许") && resources.contains("获取 root，再重试"));
        assertTrue("命令失败不得归类成无 root", helper.contains("RestartResult.COMMAND_FAILED"));
    }
    @Test
    public void materialOverviewAndStatusGroupsUseRoundedSegmentedRows() throws IOException {
        String home = source("ui/screen/home/HomeMaterial.kt");
        String status = source("ui/screen/status/StatusMaterial.kt");
        assertTrue("当前配置行必须消费分段形状", home.contains("private fun SummaryItem")
                && home.substring(home.indexOf("private fun SummaryItem")).contains("SegmentedListItem("));
        assertTrue("状态自检行必须消费分段形状", status.contains("private fun CheckItem")
                && status.substring(status.indexOf("private fun CheckItem")).contains("SegmentedListItem("));
        assertTrue("设备信息行必须消费分段形状", status.contains("private fun InfoItem")
                && status.substring(status.indexOf("private fun InfoItem")).contains("SegmentedListItem("));
    }

    @Test
    public void miuixAboutUsesNormalReadableTopScrolling() throws IOException {
        String about = source("ui/screen/about/AboutMiuix.kt");
        assertTrue("Miuix 关于页必须使用普通 LazyColumn 滚动", about.contains("LazyColumn(")
                && about.contains("state = lazyListState"));
        assertFalse("不得用 nested-scroll 消费顶部手势", about.contains("NestedScrollConnection")
                || about.contains(".nestedScroll("));
        assertFalse("不得在顶部启用弹性 overscroll", about.contains("overScrollVertical()"));
        assertTrue("顶栏必须透明以透出整页渐变", about.contains("color = Color.Transparent"));
        assertTrue("顶部栏标题必须始终可读", about.contains("titleColor = colorScheme.onBackground"));
        assertTrue("Scaffold 容器不得再铺不透明白底",
                about.contains("containerColor = Color.Transparent"));
        assertTrue("渐变必须铺满整页（含顶栏/状态栏条带）", about.contains("isFullSize = true"));
        assertFalse("关于页不应再依赖滚动进度控制顶栏透明度",
                about.contains("titleColor = colorScheme.onSurface.copy")
                        || about.contains("AboutTitleFadeStart"));
        assertFalse("Miuix 关于页不再显示许可证", about.contains("about_license"));
        assertFalse("Material 关于页不再显示许可证",
                source("ui/screen/about/AboutMaterial.kt").contains("about_license"));

        assertTrue("图标区高度必须实测", about.contains("onSizeChanged")
                && about.contains("logoHeightDp = size.height.toDp()"));
    }

    @Test
    public void statusRestartIsLastGroup() throws IOException {
        for (String f : new String[]{"ui/screen/status/StatusMaterial.kt",
                "ui/screen/status/StatusMiuix.kt"}) {
            String text = source(f);
            int deviceInfo = text.indexOf("R.string.status_screen_width");
            int restart = text.indexOf("R.string.restart_systemui");
            assertTrue(f + " 必须存在设备信息和重启入口", deviceInfo >= 0 && restart >= 0);
            assertTrue(f + " 重启入口必须位于设备信息之后", restart > deviceInfo);
        }
    }

    @Test
    public void settingsGroupsHaveTitles() throws IOException {
        String[] groups = {
                "settings_section_appearance",
                "settings_section_playback",
                "order_label",
                "settings_section_display",
                "holes_title",
                "settings_section_pause",
        };
        String material = source("ui/screen/settings/SettingsMaterial.kt");
        String miuix = source("ui/screen/settings/SettingsMiuix.kt");
        assertTrue("Material 分组标题必须使用纯文本 SectionTitle", material.contains("SectionTitle("));
        assertTrue("Miuix 分组标题必须使用 SmallTitle", miuix.contains("SmallTitle("));
        for (String group : groups) {
            assertTrue("Material 缺少分组标题: " + group, material.contains("R.string." + group));
            assertTrue("Miuix 缺少分组标题: " + group, miuix.contains("R.string." + group));
        }
        String appearance = source("ui/screen/appearance/AppearanceScreen.kt");
        assertTrue("界面风格选项必须显示 Material / Miuix",
                appearance.contains("listOf(\"Material\", \"Miuix\")"));
    }

    @Test
    public void systemNameReadsRealFirmwareProps() throws IOException {
        String status = source("ui/screen/status/StatusScreen.kt");
        int miui = status.indexOf("ro.miui.ui.version.name");
        int hyperOs = status.indexOf("ro.mi.os.version.name");
        int android = status.indexOf("Build.VERSION.RELEASE");
        assertTrue("必须尝试读取 MIUI/HyperOS 版本属性", miui >= 0 && hyperOs >= 0);
        assertTrue("必须按 MIUI → HyperOS → Android 版本的优先级兜底",
                miui < hyperOs && hyperOs < android);
        assertTrue("必须保留 Android 版本作为最终回退", status.contains("Android ${Build.VERSION.RELEASE}"));
    }

    @Test
    public void holeSamplesFollowDarkTheme() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertTrue("样例必须判断深色模式", samples.contains("val isDark = isInDarkTheme()"));
        assertTrue("深色下必须是暗底", samples.contains("bg = if (isDark) Color(0xFF141218)"));
        assertTrue("深色下必须是亮字", samples.contains("fg = if (isDark) Color(0xFFE3E2E6)"));
    }
    @Test
    public void miuixSecondaryPagesUseSmallTopAppBarAndSharedBackButton() throws IOException {
        String[] pages = {
                "ui/screen/status/StatusMiuix.kt",
                "ui/screen/appearance/AppearanceMiuix.kt",
                "ui/screen/holes/CutoutMiuix.kt",
                "ui/screen/edit/PoemEditMiuix.kt",
                "ui/screen/edit/HoleEditMiuix.kt",
                "ui/screen/whitelist/WhitelistMiuix.kt",
                "ui/screen/about/AboutMiuix.kt",
        };
        for (String f : pages) {
            String text = source(f);
            assertTrue(f + " 二级页必须用 SmallTopAppBar", text.contains("SmallTopAppBar("));
            assertFalse(f + " 不得再用一级页的大标题 TopAppBar",
                    text.contains("TopAppBar(") && !text.contains("SmallTopAppBar("));
            assertTrue(f + " 返回按钮必须走共享 BackButton 组件", text.contains("BackButton("));
        }
    }
    @Test
    public void retappingCurrentTabScrollsToTop() throws IOException {
        String pager = source("ui/component/bottombar/BottomBar.kt");
        assertTrue("重按当前 tab 必须触发 rescroll 而不是直接 return",
                pager.contains("TopTabScrollRegistry.rescroll(targetIndex, coroutineScope)"));
        String[] pages = {
                "ui/screen/home/HomeScreen.kt",
                "ui/screen/library/LibraryScreen.kt",
                "ui/screen/settings/SettingsScreen.kt",
        };
        for (String f : pages) {
            String text = source(f);
            assertTrue(f + " 必须注册回滚闭包", text.contains("TopTabScrollRegistry.register("));
            assertTrue(f + " 必须在销毁时注销", text.contains("TopTabScrollRegistry.unregister("));
        }
        String registry = source("ui/util/TopTabScrollRegistry.kt");
        assertTrue("注册表必须存在 rescroll", registry.contains("fun rescroll("));
    }
}
