package io.github.rude56.poemstatus;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 省电回归：把「每 N 秒白干活」这类看不见的浪费钉成会失败的测试。
 *
 * <p>这些都是实测出来的真实问题 —— 每一条都能在 logcat 里看到对应的痕迹
 * （日志刷屏 / 状态栏反复重绘 / 跨进程反复唤醒），但都不影响功能，
 * 所以光靠功能测试永远抓不到，必须单独钉住。
 */
public class PowerRegressionTest {

    private static final File MAIN = new File("src/main/java/io/github/rude56/poemstatus");
    private static final File RES = new File("src/main/res");

    private static String source(String name) throws IOException {
        File f = new File(MAIN, name);
        assertTrue("文件不存在: " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** 读任意文件（资源文件用）。 */
    private static String read(File f) throws IOException {
        assertTrue("文件不存在: " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** 截取某个方法体的近似文本（按大括号配对）。 */
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

    /**
     * 暂停态不得反复恢复系统栏。
     *
     * <p>暂停期间 tick() 仍按心跳每 3 秒跑一次，每次都调 pause()。
     * 而 restoreModules() 最终会走到 HolePuncher.clear() 里的 root.invalidate()
     * —— 整条状态栏全量重绘。也就是「锁屏 / 白名单 / 横屏」这些正常暂停场景下，
     * 旧代码每 3 秒白重绘一次状态栏。
     */
    @Test
    public void pauseDoesNotRestoreModulesRepeatedly() throws IOException {
        String engine = source("PoemEngine.java");
        String pause = bodyOf(engine, "public void pause(String reason)");
        // restoreModules() 必须在 changed 分支里，不能无条件调用。
        int guard = pause.indexOf("if (changed)");
        assertTrue("pause 里必须有 changed 判定", guard >= 0);
        int restore = pause.indexOf("host.restoreModules()");
        assertTrue("pause 必须恢复系统栏", restore >= 0);
        assertTrue("restoreModules 必须受 changed 保护，否则暂停态每 3 秒白重绘一次",
                restore > guard);
    }

    /**
     * 没有洞要清的时候，clear() 不得触发重绘。
     *
     * <p>restoreModules() 在暂停路径上会被反复调用，而 root.invalidate()
     * 是整条状态栏全量重绘。本来就没挖过洞，重绘一次的结果和屏幕上完全一样。
     */
    @Test
    public void clearingNothingDoesNotInvalidate() throws IOException {
        String hole = source("HolePuncher.java");
        String clear = bodyOf(hole, "static void clear()");
        assertTrue("clear 必须判断「之前是否真的挖过洞」", clear.contains("hadClip"));
        assertTrue("只有在确实挖过洞时才 invalidate", clear.contains("hadClip && root != null"));
    }

    /**
     * 洞配置重建必须去重。
     *
     * <p>refreshCutout() 会被 tick（心跳）无条件调用，而它一旦真跑起来就会
     * 重新解析洞、给每个洞 new int[]、拼日志字符串，最后 invalidate。
     * 洞没变的时候这些全是白干。
     */
    @Test
    public void holeConfigRebuildIsDeduplicated() throws IOException {
        String refresh = bodyOf(source("SystemUiController.java"),
                "private void refreshCutout(View container)");
        assertTrue("必须按 holesJson 去重", refresh.contains("lastHolesJson"));
        assertTrue("必须按状态栏高度去重（旋转要重建）", refresh.contains("lastHoleBarHeight"));
        // 去重判定必须发生在真正重建之前。
        int guard = refresh.indexOf("lastHolesJson");
        int rebuild = refresh.indexOf("setHoleConfig(");
        assertTrue("去重必须发生在重建之前", guard >= 0 && rebuild > guard);
        // 去重必须覆盖「两个条件都没变」的情况，否则等于没去重。
        assertTrue("必须整段提前返回", refresh.contains("return;"));
    }

    /**
     * 帧间隔必须按速度自适应。
     *
     * <p>既然规则是「位移不足 1px 就不画」，那回调节奏也不该固定 30fps ——
     * 20px/s 时 1px 要走 50ms，每 33ms 醒一次纯属空转。
     */
    @Test
    public void frameIntervalAdaptsToSpeed() throws IOException {
        String pacer = source("FramePacer.java");
        assertTrue("必须提供按速度算间隔的纯函数",
                pacer.contains("intervalNsForSpeed"));
        // 纯逻辑本身的行为由 FramePacerTest 覆盖，这里只钉住「运行时确实用了它」。
        String rt = source("PoemRuntime.java");
        assertTrue("运行时必须调用它", rt.contains("FramePacer.intervalNsForSpeed"));
        assertTrue("间隔必须按速度算", rt.contains("frameIntervalNs"));
    }

    /** 心跳必须放宽：旋转已经由 ComponentCallbacks 事件驱动，不需要 3 秒轮询。 */
    @Test
    public void heartbeatIsRelaxedAndRotationIsEventDriven() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("心跳必须放宽到 8 秒以上", ctl.contains("HEARTBEAT_MS = 8000L"));
        assertTrue("关闭态必须有更慢的一档", ctl.contains("IDLE_HEARTBEAT_MS"));
        assertTrue("必须注册配置变化回调", ctl.contains("registerComponentCallbacks"));
        assertTrue("必须有 onConfigurationChanged", ctl.contains("onConfigurationChanged"));
    }

    /**
     * 每帧回调不得分配对象。
     *
     * <p>旧写法 {@code guard.guard("occlude", () -> runtime.updateOcclusion(...))}
     * 里的 lambda 每次调用都会捕获 4 个 float 并分配一个新对象，30 次/秒。
     */
    @Test
    public void perFrameCallbackDoesNotAllocate() throws IOException {
        String ctl = source("SystemUiController.java");
        String onFrame = bodyOf(ctl,
                "public void onFrame(float left, float top, float right, float bottom)");
        assertFalse("帧内不得分配 lambda（捕获变量会导致每帧一个对象）",
                onFrame.contains("() ->"));
        assertFalse("帧内不得有 new", onFrame.contains("new "));
        // 必须走复用 Body 的快路径。
        assertTrue("必须复用 Body 实例", ctl.contains("occludeBody"));
        assertTrue("必须走不碰 Map 的快路径", ctl.contains("guardFast"));
    }

    /** guardFast 成功路径不得写 Map。 */
    @Test
    public void guardFastDoesNotTouchMapOnSuccess() throws IOException {
        String guard = source("FeatureGuard.java");
        String fast = bodyOf(guard, "public boolean guardFast(String id, Body body)");
        assertFalse("成功路径不得 remove", fast.contains("fails.remove"));
        assertFalse("成功路径不得 put", fast.contains("fails.put"));
        assertTrue("失败仍要计数", fast.contains("recordFailure"));
    }

    /**
     * 配置轮询必须分档，不能无条件每 2 秒跨进程读一次。
     *
     * <p>真实代价有两层：SystemUI 每 2 秒醒一次；更糟的是如果本 App 进程已经死了，
     * 这次 call 会把它整个拉起来 —— 等于每 2 秒唤醒一个本该休眠的应用进程。
     */
    @Test
    public void configPollingIsNotUnconditionallyFrequent() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("必须有低频档", ctl.contains("IDLE_POLL_MS"));
        String reload = bodyOf(ctl, "private void maybeReloadConfig()");
        assertTrue("必须在两种节奏间选择", reload.contains("pollInterval"));
        assertTrue("必须用选出来的节奏做判断", reload.contains(">= pollInterval"));
        assertTrue("configObserverFailed 必须真的被读到（否则是个死字段）",
                reload.contains("configObserverFailed"));
        // 熄屏必须彻底停掉轮询：这次 call 会把本 App 进程整个拉起来。
        assertTrue("熄屏必须不轮询", reload.contains("if (!screenInteractive)"));
        assertTrue("必须有「永不到期」的档位", reload.contains("Long.MAX_VALUE"));
        int offBranch = reload.indexOf("!screenInteractive");
        int dirtyCheck = reload.indexOf("needRefresh");
        assertTrue("熄屏判定必须早于刷新判定", offBranch >= 0 && dirtyCheck > offBranch);
    }

    /** 取色必须改成推送式，不得再有 300ms 定时轮询。 */
    @Test
    public void colorSamplingIsPushedNotPolled() throws IOException {
        String sampler = source("ColorSampler.java");
        String clock = bodyOf(sampler, "private int readClockColor(View contentRoot)");
        String text = bodyOf(sampler, "private int readAnyModuleTextColor(View contentRoot)");
        assertTrue("时钟查找结果必须缓存", clock.contains("clockRef"));
        assertTrue("文字控件查找结果必须缓存", text.contains("textRef"));
        assertTrue("必须用弱引用，避免把 View 钉住", sampler.contains("WeakReference"));
        // 推送式：系统外观变化时由 TintState 回调，而不是自己定时去问。
        assertTrue("必须有推送入口", sampler.contains("startTracking"));
        assertTrue("TintState 必须能回调", source("TintState.java").contains("setListener"));
        // 兜底轮询周期必须明显变慢（原来 300ms）。
        assertTrue("兜底轮询必须是低频", sampler.contains("SAMPLE_INTERVAL_MS = 2000L"));
        assertFalse("不得再有 300ms 轮询", sampler.contains("300L"));
        // 推送必须合流：系统图标色切换是带动画的，applyIconTint 每帧都会调，
        // 不合流等于把 300ms 轮询换成 60 次/秒轮询，比原来更费电。
        assertTrue("推送必须合流", sampler.contains("COALESCE_MS"));
        assertTrue("合流后异步采样", sampler.contains("sampleRunnable"));
    }

    /**
     * 「跳过系统挖孔」必须真正彻底移除。
     *
     * <p>这个开关曾经穿过 5 层参数，但没有任何一处调用 {@code CutoutSkipper.plan()}，
     * 于是它「看起来存在、实际永远无效」。加了调用之后又与虫洞/隧道互相干扰：
     * 挖孔把诗句切成多段时会提前 {@code return}，跳过下面的隧道裁剪与虫洞分段，
     * 结果是「诗句横跨挖孔那一帧，虫洞传送整个失效」。
     *
     * <p>按需求整体删除。这里钉住的是「删干净了」：任何一处残留都会让
     * 读者误以为它还生效。
     */
    @Test
    public void cutoutSkippingIsFullyRemoved() throws IOException {
        for (String f : new String[]{"PoemRuntime.java", "SystemUiController.java",
                "PoemConfig.java", "ConfigCodec.java", "ConfigStore.java", "ConfigKeys.java"}) {
            String text = source(f);
            assertFalse(f + " 不得再引用 CutoutSkipper", text.contains("CutoutSkipper"));
            assertFalse(f + " 不得再有 skipCutout 字段", text.contains("skipCutout"));
            assertFalse(f + " 不得再读系统挖孔", text.contains("readSystemCutout"));
            // 注意：LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS 是合法的窗口参数
            // （让诗句层能铺到挖孔区域），与被删掉的「读取挖孔矩形来避让」是两件事。
            assertFalse(f + " 不得再读占位挖孔矩形",
                    text.contains("getBoundingRects") || text.contains("getDisplayCutout"));
        }
        assertFalse("CutoutSkipper 类必须已删除",
                new File(MAIN, "CutoutSkipper.java").isFile());
        assertFalse("设置页不得再有该开关",
                source("ui/SettingsScreen.kt").contains("skip_cutout"));
        assertFalse("字符串必须已删除",
                read(new File(RES, "values/strings.xml")).contains("skip_cutout"));
    }

    /**
     * 预览层已彻底移到 App 侧，SystemUI 不得再留半套实现。
     *
     * <p>之前 cutoutPreview 标志根本没有任何地方设为 true，
     * 但 SystemUI 里仍常驻着一整条预览挂载路径（OverlayView 预览实例、
     * 预览画笔、逐帧判断），纯属死代码，还会误导读代码的人。
     */
    @Test
    public void previewSubsystemIsFullyRemovedFromSystemUi() throws IOException {
        String rt = source("PoemRuntime.java");
        assertFalse("不得再有预览层实例", rt.contains("overlayPreview"));
        assertFalse("不得再有预览专用字段", rt.contains("previewOnlyVisible"));
        assertFalse("不得再有预览画笔", rt.contains("previewFill"));
        assertFalse("不得再有预览挂载入口", rt.contains("void showHolePreview("));
        // 框由 App 自己画，这一点必须仍成立。
        assertTrue("App 侧仍必须自己画框",
                new File(MAIN, "ui/HoleFrameOverlay.kt").isFile());
    }

    /** 洞编辑页不得在组合期间改导航栈。 */
    @Test
    public void holeEditDoesNotMutateNavigationDuringComposition() throws IOException {
        String edit = source("ui/HoleEditScreen.kt");
        assertTrue("必须用 LaunchedEffect 退出", edit.contains("LaunchedEffect(slot)"));
        assertFalse("不得在组合期间直接调 onDone()", edit.contains("        onDone()\n        return"));
    }

    /** 添加洞失败时必须给用户反馈，不能静默。 */
    @Test
    public void addingHoleAlwaysGivesFeedback() throws IOException {
        String cutout = source("ui/CutoutScreen.kt");
        assertTrue("达到上限要提示", cutout.contains("hole_max_reached"));
        assertTrue("没地方放要提示", cutout.contains("hole_no_room"));
        assertTrue("新增必须二次校验不重合", cutout.contains("overlapsAny"));
    }

    // ------------------------------------------------------------ App 侧体验/正确性

    /**
     * 重启 SystemUI 不得阻塞主线程。
     *
     * <p>{@code ProcessBuilder(...).waitFor()} 会一直等到子进程退出，而 root
     * 管理器（Magisk / KernelSU）默认会弹授权框等用户点「允许」。
     * 以前这是同步调用、直接在按钮 onClick（主线程）里 —— 用户犹豫几秒就 ANR。
     */
    @Test
    public void restartingSystemUiIsOffTheMainThread() throws IOException {
        String helper = source("RestartHelper.kt");
        assertTrue("必须是挂起函数", helper.contains("suspend fun restartSystemUi"));
        assertTrue("必须切到 IO 线程", helper.contains("Dispatchers.IO"));
        String settings = source("ui/SettingsScreen.kt");
        assertTrue("必须用协程发起", settings.contains("scope.launch"));
        assertTrue("必须 await 结果", settings.contains("RestartHelper.restartSystemUi(context)"));
        // 主线程上不许出现同步阻塞的进程等待。
        assertFalse("按钮回调里不得直接同步等待", settings.contains("ProcessBuilder"));
    }

    /**
     * 拖动排序的行高必须实测，不能写死像素。
     *
     * <p>以前写的是 {@code rowHeightPx = 108f}，在 640dpi（density 4.0）下
     * 只有 27dp，而真实卡片约 67dp ≈ 268px —— 拖 1/4 行就换位，列表会「抢跑」。
     */
    @Test
    public void dragReorderUsesMeasuredRowHeight() throws IOException {
        String library = source("ui/LibraryScreen.kt");
        assertFalse("不得写死行高像素", library.contains("= 108f"));
        assertTrue("必须实测行高", library.contains("onGloballyPositioned"));
        assertTrue("必须把实测值用在换位判定上", library.contains("rowHeightPx"));
    }

    /**
     * 洞页的两个动画样例必须按时间推进，不能按帧数累加。
     *
     * <p>以前是 {@code while(true){ awaitFrame(); progress += 0.0006f }} ——
     * 120Hz 屏上每秒重组 120 次；而且「一轮 14 秒」是按 120Hz 推算的，
     * 60Hz 设备上会变成 28 秒，速度随刷新率变。
     */
    @Test
    public void holeSamplesAdvanceByTimeNotFrames() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertFalse("不得按帧数累加", samples.contains("progress += 0.0006f"));
        assertFalse("不得每帧都跑", samples.contains("awaitFrame()"));
        assertTrue("必须按时间戳算进度", samples.contains("withFrameNanos"));
        assertTrue("必须节流到约 30fps", samples.contains("33_000_000L"));
        // 帧内也不该每帧 new 洞对象与输出数组。
        assertTrue("洞对象必须复用", samples.contains("hole.withRange"));
    }

    /** 概览页的连接自检必须绑生命周期，不能在后台继续 2 秒一跳。 */
    @Test
    public void overviewConnectionCheckStopsInBackground() throws IOException {
        String overview = source("ui/OverviewScreen.kt");
        assertTrue("必须绑 STARTED 生命周期", overview.contains("repeatOnLifecycle"));
        assertTrue("必须用 Lifecycle.State.STARTED", overview.contains("Lifecycle.State.STARTED"));
    }

    /** 保存诗库必须 copy 后再改，否则 structural equality 吞掉通知。 */
    @Test
    public void persistingPoemsCopiesConfigFirst() throws IOException {
        String state = source("AppState.kt");
        String body = bodyOf(state, "fun persistPoems(context: Context, list: List<PoemItem>)");
        assertTrue("必须先 copy", body.contains("config.value.copy()"));
        assertFalse("不得原地改同一个实例", body.contains("val next = config.value\n"));
    }

    /** 洞页的框与样例仍然必须存在（防止重构时误删）。 */
    @Test
    public void holeFramesAndSamplesStillPresent() throws IOException {
        assertTrue(new File(MAIN, "ui/HoleFrameOverlay.kt").isFile());
        assertTrue(new File(MAIN, "ui/HoleModeSamples.kt").isFile());
        assertTrue(new File(MAIN, "ViewTree.java").isFile());
    }

    // ------------------------------------------------------------ 洞同步（重启后失效）

    /**
     * 洞列表必须在<b>诗句层被创建/重挂载时</b>也同步一次，不能只在配置变化时同步。
     *
     * <p>这是真机上「每次重启 SystemUI，文字不遮挡、不跳过虫洞，非得新添加一个洞」
     * 的根因：全新启动时 {@code refreshCutout()} 第一次运行的时刻 Overlay 还不存在
     * （第一首诗都没开始），那次 {@code overlay.setHoles()} 被跳过；而
     * {@code refreshCutout()} 又按 {@code holesJson} 去重，配置不变就再也不会推。
     * 于是诗句层的洞列表永远是空的 —— 整句连续绘制、不跳虫洞，
     * 而遮挡却按虫洞切出来的段去挖，洞那段既不画断也不挖洞，
     * 看上去就是「文字直接压在状态栏图标上」。
     *
     * <p>用户加一个洞会改 {@code holesJson}，去重失效、立刻恢复 —— 正是现象来源。
     */
    @Test
    public void holesArePushedWhenOverlayIsCreated() throws IOException {
        String rt = source("PoemRuntime.java");
        // 必须有一个统一的推送出口。
        assertTrue("必须有 pushHolesToOverlay", rt.contains("pushHolesToOverlay"));
        // 配置变化时要推。
        String cfg = bodyOf(rt, "void setHoleConfig(List<HoleRects.Hole> holes)");
        assertTrue("配置变化时必须推给诗句层", cfg.contains("pushHolesToOverlay()"));
        // 挂载时也要推 —— 这才是重启后失效的那一次。
        String attach = bodyOf(rt, "private boolean ensureOverlayAttached()");
        assertTrue("Overlay 创建/重挂载时必须补推一次洞列表",
                attach.contains("pushHolesToOverlay()"));
        // 推送必须发生在 attach 成功之后（否则 overlay 还是 null，推了也白推）。
        int attachedOk = attach.indexOf("if (!attachOverlayView(overlay))");
        int push = attach.indexOf("pushHolesToOverlay()");
        assertTrue("必须在 attach 成功之后推", attachedOk >= 0 && push > attachedOk);
    }

    // ------------------------------------------------------------ 白名单

    /**
     * 前台刷新的节流不得<b>吞掉</b>状态更新。
     *
     * <p>任务栈监听在事件后 250ms 触发，而节流窗口是 400ms —— 旧实现里
     * 被节流挡住的那次会被直接丢弃，且没人补跑，前台就永远停在过时的值上。
     * 表现是「加了白名单应用，在它里面照滚」，而且没有任何日志可查。
     */
    @Test
    public void foregroundRefreshThrottleDoesNotDropUpdates() throws IOException {
        String fg = source("ForegroundTracker.java");
        String body = bodyOf(fg, "void refreshAsync()");
        assertTrue("被节流挡下必须记账", body.contains("pendingRefresh"));
        assertTrue("必须排一次补跑", body.contains("postDelayed"));
        // 不能出现「直接 return」把更新丢掉。
        assertFalse("不得在节流窗口里直接丢弃",
                body.contains("if (now - lastRefreshAt < MIN_REFRESH_GAP_MS) {\n            return;"));
    }

    /**
     * 前台包名解析必须有多个候选。
     *
     * <p>{@code ActivityTaskManager.getTasks} 的签名在版本间反复变过
     * （`(int)` / `(int,boolean)` / `(int,boolean,boolean)` / `(int,boolean,boolean,int)`）。
     * 写死某一个重载在部分 ROM 上直接 {@code NoSuchMethodException}，
     * 而 {@code getRunningTasks} 对非自家进程常返回空 —— 两条都失败就静默失效。
     */
    @Test
    public void foregroundPackageResolutionTriesMultipleSignatures() throws IOException {
        String fg = source("ForegroundTracker.java");
        assertTrue("必须遍历 getTasks 的所有重载", fg.contains("m.getName()")
                || fg.contains("\"getTasks\".equals(m.getName())"));
        assertTrue("必须用 getMethods 而不是写死重载",
                fg.contains("service.getClass().getMethods()"));
        assertTrue("必须有焦点栈兜底", fg.contains("getFocusedRootTaskInfo"));
        assertTrue("必须兼容 topActivity/baseActivity 字段名",
                fg.contains("topActivity") && fg.contains("baseActivity"));
        assertTrue("必须兼容 getTopActivity 方法", fg.contains("getTopActivity"));
        assertTrue("解析失败必须留日志", fg.contains("foreground unresolved"));
    }

    /** 白名单匹配必须宽容（首尾空白 / 大小写），否则手输一条就永远匹配不上。 */
    @Test
    public void whitelistMatchingIsForgiving() {
        PoemConfig cfg = new PoemConfig();
        cfg.whitelist = new java.util.ArrayList<>(java.util.Arrays.asList(
                "  com.example.app  ", "Com.Other.Thing"));
        assertTrue("必须忽略首尾空白", cfg.isWhitelisted("com.example.app"));
        assertTrue("必须忽略大小写", cfg.isWhitelisted("com.other.thing"));
        assertFalse("不得误命中", cfg.isWhitelisted("com.example.app2"));
        assertFalse("空值安全", cfg.isWhitelisted(null));
        assertFalse("空串安全", cfg.isWhitelisted(""));
        assertFalse("空串安全（空白）", cfg.isWhitelisted("   "));
    }

    /** 前台变化必须留日志，否则白名单失效时无从判断。 */
    @Test
    public void foregroundChangesAreLogged() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("必须记录前台包名", ctl.contains("CONF foreground="));
        assertTrue("命中白名单要能看出来", ctl.contains("whitelisted -> pause"));
    }

    // ------------------------------------------------------------ 24 小时常驻

    /**
     * 熄屏后不得再有任何定时器。
     *
     * <p>这是本次审计里最贵的一条：心跳原本是固定 8 秒续期、前台轮询固定 5 秒续期，
     * <b>都不看屏幕状态</b>。熄屏 8 小时 = 3600 次心跳 + 5760 次跨进程反射查前台，
     * 而屏幕是黑的，这些唤醒换不来任何用户可见的效果。
     *
     * <p>亮屏有广播、任务栈变化有监听、配置变化有 ContentObserver，
     * 不存在「只能靠定时器才发现」的事件，所以熄屏时完全可以归零。
     */
    @Test
    public void screenOffStopsEveryTimer() throws IOException {
        String ctl = source("SystemUiController.java");
        String heartbeat = bodyOf(ctl, "private void scheduleHeartbeat()");
        assertTrue("熄屏必须直接返回（不续期）", heartbeat.contains("if (!screenInteractive)"));
        assertTrue("必须有「模块关闭」档", heartbeat.contains("IDLE_HEARTBEAT_MS"));

        // 熄屏分支必须出现在 postDelayed 之前，否则还是会续期。
        int guard = heartbeat.indexOf("!screenInteractive");
        int post = heartbeat.indexOf("postDelayed");
        assertTrue("熄屏判定必须早于续期", guard >= 0 && post > guard);

        // 亮屏/熄屏广播必须重新驱动心跳与前台轮询。
        String receiver = bodyOf(ctl, "private final BroadcastReceiver screenReceiver");
        assertTrue("广播里必须重算心跳", receiver.contains("scheduleHeartbeat()"));
        assertTrue("广播里必须重算前台轮询", receiver.contains("syncForegroundPolling()"));

        // 前台轮询必须能被屏幕状态关掉。
        String fg = source("ForegroundTracker.java");
        assertTrue("必须有 syncPolling", fg.contains("void syncPolling("));
        String sync = bodyOf(fg, "void syncPolling(boolean screenInteractive)");
        assertTrue("轮询必须同时受白名单与屏幕状态约束",
                sync.contains("whitelistActive") && sync.contains("screenInteractive"));
    }

    /**
     * 24 小时的日志量必须有上限。
     *
     * <p>实测：改之前每首诗打 7 行、约 13 秒一首 —— 每天 4.6 万行写进 logd。
     * 日志要落盘到环形缓冲区，那是实打实的 IO 与唤醒。现在凡是「每首都发生」
     * 的日志都必须经过 {@link Log#allow(String, long)} 限频。
     */
    @Test
    public void perPoemLoggingIsRateLimited() throws IOException {
        // 限频机制本身必须存在。
        String log = source("Log.java");
        assertTrue("必须有限频闸门", log.contains("public static boolean allow("));

        String ctl = source("SystemUiController.java");
        // 每首必打的三处：开始、时间停留、状态切换。
        String startCrawl = bodyOf(ctl,
                "public void startCrawl(PoemItem poem, String displayText, int speedPx)");
        assertTrue("POEM start 必须限频", startCrawl.contains("Log.allow("));
        String clock = bodyOf(ctl, "public void scheduleClock(int seconds)");
        assertTrue("clock hold 必须限频", clock.contains("Log.allow("));
        String state = bodyOf(ctl, "public void onStateChanged(PoemEngine.State state)");
        assertTrue("状态日志必须限频", state.contains("Log.allow("));
        // 暂停是异常状态，必须无条件打得出来（否则白名单/锁屏失效无从排查）。
        assertTrue("暂停必须无条件记录",
                state.contains("state == PoemEngine.State.PAUSED"));

        // 诗句层的每首日志已经并进 POEM start，不得再单独打一条。
        String rt = source("PoemRuntime.java");
        String start = bodyOf(rt, "boolean start(String text, int speedPx, boolean bob, Listener l)");
        assertFalse("POEM hole root 不得单独成一条（已并入 POEM start）",
                start.contains("POEM hole root="));

        // 取色诊断也不能每首一条。
        String sampler = source("ColorSampler.java");
        assertTrue("取色诊断间隔必须放宽到分钟级",
                sampler.contains("DIAG_LOG_MS = 300_000L"));
    }

    /**
     * 换首不得增删 View。
     *
     * <p>{@code addView/removeView} 会触发整棵状态栏 requestLayout + 重新挂载，
     * 比一次 invalidate 贵一个数量级，而它以前是<b>每首诗一次</b>。
     * 重新挂载还会连带重跑取色订阅与内容根 DFS 扫描。
     */
    @Test
    public void poemRotationDoesNotAddOrRemoveViews() throws IOException {
        String rt = source("PoemRuntime.java");
        String stop = bodyOf(rt, "void stop()");
        assertFalse("换首不得移除 Overlay", stop.contains("removeOverlay()"));
        assertTrue("必须清掉文字，否则会留在屏幕上", stop.contains("clearText()"));
        assertTrue("必须停帧", stop.contains("stopFrame()"));
        // 但窗口被系统重建时仍必须摘掉旧的（否则泄漏到废弃父容器上）。
        String setContainer = bodyOf(rt, "void setStatusContainer(View v)");
        assertTrue("状态栏窗口重建时必须摘掉旧诗句层",
                setContainer.contains("removeOverlay()"));
    }

    /**
     * 前台解析不得每轮都反射枚举方法。
     *
     * <p>{@code getMethods()} 每次都会新建 Method[] 并克隆参数类型数组。
     * 以前每 5 秒跑一遍，24 小时几万次 —— 全是可缓存的常量。
     */
    @Test
    public void foregroundReflectionIsCached() throws IOException {
        String fg = source("ForegroundTracker.java");
        String atm = bodyOf(fg, "private String fromActivityTaskManager()");
        assertFalse("取任务的方法列表必须缓存，不得每轮 getMethods()",
                atm.contains("service.getClass().getMethods()"));
        assertTrue("必须走缓存的 taskMethods", atm.contains("taskMethods(service)"));
        assertTrue("必须有方法缓存字段", fg.contains("taskMethodsCache"));
        assertTrue("必须有服务单例缓存", fg.contains("atmServiceCache"));
    }

    /**
     * 每次拉配置不得新建 Handler。
     *
     * <p>24 小时几万次轮询 = 几万个短命 Handler 对象，纯喂 GC。
     */
    @Test
    public void configFetcherReusesItsHandler() throws IOException {
        String fetch = source("ConfigFetcher.java");
        assertTrue("必须复用 Handler", fetch.contains("static final android.os.Handler MAIN"));
        String async = bodyOf(fetch, "static void fetchAsync(Context ctx, Callback cb)");
        assertFalse("不得在每次请求里 new Handler", async.contains("new android.os.Handler"));
        assertTrue("必须用复用的那个", async.contains("MAIN.post("));
    }
}
