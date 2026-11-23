package io.github.rude56.poemstatus;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PowerRegressionTest {
    private static final File MAIN = new File("src/main/java/io/github/rude56/poemstatus");
    private static final File RES = new File("src/main/res");
    private static String source(String name) throws IOException {
        File f = new File(MAIN, name);
        assertTrue("文件不存在: " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String read(File f) throws IOException {
        assertTrue("文件不存在: " + f, f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
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
    public void pauseDoesNotRestoreModulesRepeatedly() throws IOException {
        String engine = source("PoemEngine.java");
        String pause = bodyOf(engine, "public void pause(String reason)");

        int guard = pause.indexOf("if (changed)");
        assertTrue("pause 里必须有 changed 判定", guard >= 0);
        int restore = pause.indexOf("host.restoreModules()");
        assertTrue("pause 必须恢复系统栏", restore >= 0);
        assertTrue("restoreModules 必须受 changed 保护，否则暂停态每 3 秒白重绘一次",
                restore > guard);
    }

    @Test
    public void clearingNothingDoesNotInvalidate() throws IOException {
        String hole = source("HolePuncher.java");
        String clear = bodyOf(hole, "static void clear()");
        assertTrue("clear 必须判断「之前是否真的挖过洞」", clear.contains("hadClip"));
        assertTrue("只有在确实挖过洞时才 invalidate", clear.contains("hadClip && root != null"));
    }

    @Test
    public void holeConfigRebuildIsDeduplicated() throws IOException {
        String refresh = bodyOf(source("SystemUiController.java"),
                "private void refreshCutout(View container)");
        assertTrue("必须按 holesJson 去重", refresh.contains("lastHolesJson"));
        assertTrue("必须按状态栏高度去重（旋转要重建）", refresh.contains("lastHoleBarHeight"));
        int guard = refresh.indexOf("lastHolesJson");
        int rebuild = refresh.indexOf("setHoleConfig(");
        assertTrue("去重必须发生在重建之前", guard >= 0 && rebuild > guard);

        assertTrue("必须整段提前返回", refresh.contains("return;"));
    }

    @Test
    public void frameIntervalAdaptsToSpeed() throws IOException {
        String pacer = source("FramePacer.java");
        assertTrue("必须提供按速度算间隔的纯函数",
                pacer.contains("intervalNsForSpeed"));
        String rt = source("PoemRuntime.java");
        assertTrue("运行时必须调用它", rt.contains("FramePacer.intervalNsForSpeed"));
        assertTrue("间隔必须按速度算", rt.contains("frameIntervalNs"));
    }

    @Test
    public void heartbeatIsRelaxedAndRotationIsEventDriven() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("心跳必须放宽到 8 秒以上", ctl.contains("HEARTBEAT_MS = 8000L"));
        assertTrue("关闭态必须有更慢的一档", ctl.contains("IDLE_HEARTBEAT_MS"));
        assertTrue("必须注册配置变化回调", ctl.contains("registerComponentCallbacks"));
        assertTrue("必须有 onConfigurationChanged", ctl.contains("onConfigurationChanged"));
    }

    @Test
    public void perFrameCallbackDoesNotAllocate() throws IOException {
        String ctl = source("SystemUiController.java");
        String onFrame = bodyOf(ctl,
                "public void onFrame(float left, float top, float right, float bottom)");
        assertFalse("帧内不得分配 lambda（捕获变量会导致每帧一个对象）",
                onFrame.contains("() ->"));
        assertFalse("帧内不得有 new", onFrame.contains("new "));

        assertTrue("必须复用 Body 实例", ctl.contains("occludeBody"));
        assertTrue("必须走不碰 Map 的快路径", ctl.contains("guardFast"));
    }

    @Test
    public void guardFastDoesNotTouchMapOnSuccess() throws IOException {
        String guard = source("FeatureGuard.java");
        String fast = bodyOf(guard, "public boolean guardFast(String id, Body body)");
        assertFalse("成功路径不得 remove", fast.contains("fails.remove"));
        assertFalse("成功路径不得 put", fast.contains("fails.put"));
        assertTrue("失败仍要计数", fast.contains("recordFailure"));
    }

    @Test
    public void configPollingIsNotUnconditionallyFrequent() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("必须有低频档", ctl.contains("IDLE_POLL_MS"));
        String reload = bodyOf(ctl, "private void maybeReloadConfig()");
        assertTrue("必须在两种节奏间选择", reload.contains("pollInterval"));
        assertTrue("必须用选出来的节奏做判断", reload.contains(">= pollInterval"));
        assertTrue("configObserverFailed 必须真的被读到（否则是个死字段）",
                reload.contains("configObserverFailed"));
        assertTrue("熄屏必须不轮询", reload.contains("if (!screenInteractive)"));
        assertTrue("必须有「永不到期」的档位", reload.contains("Long.MAX_VALUE"));
        int offBranch = reload.indexOf("!screenInteractive");
        int dirtyCheck = reload.indexOf("needRefresh");
        assertTrue("熄屏判定必须早于刷新判定", offBranch >= 0 && dirtyCheck > offBranch);
    }

    @Test
    public void colorSamplingIsPushedNotPolled() throws IOException {
        String sampler = source("ColorSampler.java");
        String clock = bodyOf(sampler, "private int readClockColor(View contentRoot)");
        String text = bodyOf(sampler, "private int readAnyModuleTextColor(View contentRoot)");
        assertTrue("时钟查找结果必须缓存", clock.contains("clockRef"));
        assertTrue("文字控件查找结果必须缓存", text.contains("textRef"));
        assertTrue("必须用弱引用，避免把 View 钉住", sampler.contains("WeakReference"));

        assertTrue("必须有推送入口", sampler.contains("startTracking"));
        assertTrue("TintState 必须能回调", source("TintState.java").contains("setListener"));

        assertTrue("兜底轮询必须是低频", sampler.contains("SAMPLE_INTERVAL_MS = 2000L"));
        assertFalse("不得再有 300ms 轮询", sampler.contains("300L"));

        assertTrue("推送必须合流", sampler.contains("COALESCE_MS"));
        assertTrue("合流后异步采样", sampler.contains("sampleRunnable"));
    }
    @Test
    public void cutoutSkippingIsFullyRemoved() throws IOException {
        for (String f : new String[]{"PoemRuntime.java", "SystemUiController.java",
                "PoemConfig.java", "ConfigCodec.java", "ConfigStore.java", "ConfigKeys.java"}) {
            String text = source(f);
            assertFalse(f + " 不得再引用 CutoutSkipper", text.contains("CutoutSkipper"));
            assertFalse(f + " 不得再有 skipCutout 字段", text.contains("skipCutout"));
            assertFalse(f + " 不得再读系统挖孔", text.contains("readSystemCutout"));

            assertFalse(f + " 不得再读占位挖孔矩形",
                    text.contains("getBoundingRects") || text.contains("getDisplayCutout"));
        }
        assertFalse("CutoutSkipper 类必须已删除",
                new File(MAIN, "CutoutSkipper.java").isFile());

        for (String f : new String[]{"ui/screen/settings/SettingsScreen.kt",
                "ui/screen/settings/SettingsMaterial.kt",
                "ui/screen/settings/SettingsMiuix.kt"}) {
            assertFalse(f + " 不得再有该开关", source(f).contains("skip_cutout"));
        }
        assertFalse("字符串必须已删除",
                read(new File(RES, "values/strings.xml")).contains("skip_cutout"));
    }

    @Test
    public void previewSubsystemIsFullyRemovedFromSystemUi() throws IOException {
        String rt = source("PoemRuntime.java");
        assertFalse("不得再有预览层实例", rt.contains("overlayPreview"));
        assertFalse("不得再有预览专用字段", rt.contains("previewOnlyVisible"));
        assertFalse("不得再有预览画笔", rt.contains("previewFill"));
        assertFalse("不得再有预览挂载入口", rt.contains("void showHolePreview("));

        assertTrue("App 侧仍必须自己画框",
                new File(MAIN, "ui/HoleFrameOverlay.kt").isFile());
    }
    @Test
    public void holeEditDoesNotMutateNavigationDuringComposition() throws IOException {
        String edit = source("ui/screen/edit/HoleEditScreen.kt");
        assertTrue("必须用 LaunchedEffect 退出", edit.contains("LaunchedEffect(slot)"));
        assertFalse("不得在组合期间直接调 onDone()", edit.contains("        onDone()\n        return"));
    }
    @Test
    public void addingHoleAlwaysGivesFeedback() throws IOException {

        String cutout = source("ui/screen/holes/CutoutScreen.kt");
        assertTrue("达到上限要提示", cutout.contains("R.string.hole_max_reached"));
        assertTrue("没地方放要提示", cutout.contains("R.string.hole_no_room"));

        assertTrue("保存时必须二次校验不重合",
                source("ui/screen/edit/HoleEditScreen.kt").contains("HoleRects.firstOverlap("));
    }

    @Test
    public void addingHoleRequiresUserInput() throws IOException {
        String cutout = source("ui/screen/holes/CutoutScreen.kt");
        assertTrue("新增必须进编辑页", cutout.contains("navigator.push(Route.HoleNew)"));

        assertFalse("列表页不得自己分配槽位", cutout.contains("HoleRects.nextFreeSlot("));

        assertTrue("列表页的 findFreeSpot 只做前置校验",
                cutout.contains("findFreeSpot(holes, screenW, 100) == null"));

        String edit = source("ui/screen/edit/HoleEditScreen.kt");
        assertTrue("编辑页必须支持新增模式", edit.contains("slot: Int?"));
        assertTrue("新增时必须分配槽位", edit.contains("HoleRects.nextFreeSlot("));
        assertFalse("新增时不得预填边界", edit.contains("findFreeSpot("));
        assertTrue("新增时边界必须留空", edit.contains("initial?.left?.toString() ?: \"\""));
        assertTrue("必须把编辑中的草稿推给状态栏",
                edit.contains("HoleDraftHolder.draft = HoleDraft("));
    }

    @Test
    public void restartingSystemUiIsOffTheMainThread() throws IOException {
        String helper = source("RestartHelper.kt");
        assertTrue("必须在可取消的 IO 上下文运行", helper.contains("runInterruptible"));
        assertTrue("必须先实际请求 root 授权并验证 UID 0", helper.contains("runSu(\"id -u\", rootProbe = true)"));
        String classifier = source("RestartFailureClassifier.java");
        assertTrue("权限失败必须区分明确结果", classifier.contains("RestartResult.ROOT_DENIED")
                && classifier.contains("RestartResult.ROOT_UNAVAILABLE"));

        String status = source("ui/screen/status/StatusScreen.kt");
        assertTrue("必须用协程发起", status.contains("scope.launch"));
        assertTrue("必须 await 结果",
                status.contains("RestartHelper.restartSystemUi(context)"));
        assertTrue("必须把 root 失败结果显示给用户", status.contains("Toast.makeText(context, messageRes"));
        assertFalse("不得直接同步等待", status.contains("ProcessBuilder"));

        for (String f : new String[]{"ui/screen/status/StatusMaterial.kt",
                "ui/screen/status/StatusMiuix.kt"}) {
            assertTrue(f + " 必须有重启入口",
                    source(f).contains("actions.onRestartSystemUi"));
        }
        for (String f : new String[]{"ui/screen/home/HomeMaterial.kt",
                "ui/screen/home/HomeMiuix.kt", "ui/screen/home/HomeScreen.kt"}) {
            assertFalse(f + " 不得再有重启入口", source(f).contains("onRestartSystemUi"));
        }
    }
    @Test
    public void dragReorderUsesMeasuredRowHeight() throws IOException {
        String library = source("ui/screen/library/LibraryScreen.kt");
        assertFalse("不得写死行高像素", library.contains("= 108f"));
        assertTrue("必须实测行高", library.contains("onGloballyPositioned"));
        assertTrue("必须把实测值用在换位判定上", library.contains("rowHeightPx"));
    }

    @Test
    public void overviewConnectionCheckStopsInBackground() throws IOException {
        for (String f : new String[]{"ui/screen/home/HomeScreen.kt",
                "ui/screen/status/StatusScreen.kt"}) {
            String text = source(f);
            assertTrue(f + " 必须绑 STARTED 生命周期", text.contains("repeatOnLifecycle"));
            assertTrue(f + " 必须用 Lifecycle.State.STARTED",
                    text.contains("Lifecycle.State.STARTED"));
        }
        String home = source("ui/screen/home/HomeScreen.kt");
        assertTrue("概览页轮询必须按当前选中 tab 开关",
                home.contains("isOverviewVisible") && home.contains("if (isOverviewVisible)"));
        assertTrue("概览页 tab 状态变化必须重启/停止轮询协程",
                home.contains("LaunchedEffect(lifecycleOwner, isOverviewVisible)"));
    }

    @Test
    public void holeSamplesAdvanceByTimeNotFrames() throws IOException {
        String samples = source("ui/HoleModeSamples.kt");
        assertFalse("不得按帧数累加", samples.contains("progress += 0.0006f"));
        assertFalse("不得每帧都跑", samples.contains("awaitFrame()"));
        assertTrue("必须按时间戳算进度", samples.contains("withFrameNanos"));
        assertTrue("必须节流到约 30fps", samples.contains("33_000_000L"));
        assertTrue("洞对象必须复用", samples.contains("hole.withRange"));
    }
    @Test
    public void persistingPoemsCopiesConfigFirst() throws IOException {
        String state = source("AppState.kt");
        String body = bodyOf(state, "fun persistPoems(context: Context, list: List<PoemItem>)");
        assertTrue("必须先 copy", body.contains("config.value.copy()"));
        assertFalse("不得原地改同一个实例", body.contains("val next = config.value\n"));
    }

    @Test
    public void holeFramesAndSamplesStillPresent() throws IOException {
        assertTrue(new File(MAIN, "ui/HoleFrameOverlay.kt").isFile());
        assertTrue(new File(MAIN, "ui/HoleModeSamples.kt").isFile());
        assertTrue(new File(MAIN, "ViewTree.java").isFile());
    }

    @Test
    public void holesArePushedWhenOverlayIsCreated() throws IOException {
        String rt = source("PoemRuntime.java");

        assertTrue("必须有 pushHolesToOverlay", rt.contains("pushHolesToOverlay"));

        String cfg = bodyOf(rt, "void setHoleConfig(List<HoleRects.Hole> holes)");
        assertTrue("配置变化时必须推给诗句层", cfg.contains("pushHolesToOverlay()"));

        String attach = bodyOf(rt, "private boolean ensureOverlayAttached()");
        assertTrue("Overlay 创建/重挂载时必须补推一次洞列表",
                attach.contains("pushHolesToOverlay()"));
        int attachedOk = attach.indexOf("if (!attachOverlayView(overlay))");
        int push = attach.indexOf("pushHolesToOverlay()");
        assertTrue("必须在 attach 成功之后推", attachedOk >= 0 && push > attachedOk);
    }
    @Test
    public void disabledHolesAreFilteredBeforeReachingRenderers() throws IOException {
        String rt = read(new File(MAIN, "PoemRuntime.java"));

        String cfg = bodyOf(rt, "void setHoleConfig(List<HoleRects.Hole> holes)");
        assertTrue("setHoleConfig 必须过滤停用的洞", cfg.contains("h.enabled"));
        assertTrue("setHoleConfig 必须把过滤后的列表交给 HolePuncher",
                cfg.contains("HolePuncher.setHoles(configHoles)"));

        String setHoles = bodyOf(rt, "void setHoles(List<HoleRects.Hole> list)");
        assertTrue("overlay 的 setHoles 必须过滤停用的洞", setHoles.contains("h.enabled"));

        String holeRects = read(new File(MAIN, "HoleRects.java"));
        assertTrue("挖洞算法必须跳过停用的洞",
                holeRects.contains("!h.valid() || !h.enabled"));

        String puncher = read(new File(MAIN, "HolePuncher.java"));
        String puncherSetHoles = bodyOf(puncher, "static void setHoles(List<HoleRects.Hole> list)");
        assertTrue("HolePuncher.setHoles 必须过滤停用的洞 —— " +
                "它是真正挖洞的那一层，漏了它开关就等于没接",
                puncherSetHoles.contains("h.enabled"));
    }
    @Test
    public void foregroundRefreshThrottleDoesNotDropUpdates() throws IOException {
        String fg = source("ForegroundTracker.java");
        String body = bodyOf(fg, "void refreshAsync()");
        assertTrue("被节流挡下必须记账", body.contains("pendingRefresh"));
        assertTrue("必须排一次补跑", body.contains("postDelayed"));

        assertFalse("不得在节流窗口里直接丢弃",
                body.contains("if (now - lastRefreshAt < MIN_REFRESH_GAP_MS) {\n            return;"));
    }

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
    @Test
    public void foregroundChangesAreLogged() throws IOException {
        String ctl = source("SystemUiController.java");
        assertTrue("必须记录前台包名", ctl.contains("CONF foreground="));
        assertTrue("命中白名单要能看出来", ctl.contains("whitelisted -> pause"));
    }

    @Test
    public void screenOffStopsEveryTimer() throws IOException {
        String ctl = source("SystemUiController.java");
        String heartbeat = bodyOf(ctl, "private void scheduleHeartbeat()");
        assertTrue("熄屏必须直接返回（不续期）", heartbeat.contains("if (!screenInteractive)"));
        assertTrue("必须有「模块关闭」档", heartbeat.contains("IDLE_HEARTBEAT_MS"));

        int guard = heartbeat.indexOf("!screenInteractive");
        int post = heartbeat.indexOf("postDelayed");
        assertTrue("熄屏判定必须早于续期", guard >= 0 && post > guard);

        String receiver = bodyOf(ctl, "private final BroadcastReceiver screenReceiver");
        assertTrue("广播里必须重算心跳", receiver.contains("scheduleHeartbeat()"));
        assertTrue("广播里必须重算前台轮询", receiver.contains("syncForegroundPolling()"));
        String fg = source("ForegroundTracker.java");
        assertTrue("必须同步模块开关与白名单配置",
                fg.contains("void updateConfig(boolean whitelistActive, boolean moduleEnabled)"));
        assertTrue("关闭模块时必须停止前台轮询", fg.contains("setPolling(isTrackingNeeded())"));
        assertTrue("配置刷新必须检查完整跟踪条件", fg.contains("if (isTrackingNeeded())"));
        assertTrue("节流补跑也必须检查跟踪条件",
                bodyOf(fg, "void refreshAsync()").contains("if (!isTrackingNeeded() || pendingRefresh)"));
        assertTrue("异步结果必须在主线程回调前重新检查条件",
                bodyOf(fg, "void refreshAsync()").contains("if (!isTrackingNeeded() || result.equals(current))"));
        assertTrue("轮询与任务事件必须共享跟踪门控",
                fg.contains("return moduleEnabled && whitelistActive && screenInteractive"));
        assertTrue("轮询条件变化时必须取消旧任务", fg.contains("main.removeCallbacks(pollRunnable)"));
        assertTrue("关闭跟踪时不发起任务事件查询",
                bodyOf(fg, "private final Runnable refreshRunnable").contains("if (isTrackingNeeded())"));
    }

    @Test
    public void foregroundPollingRequiresEnabledModuleAndWhitelist() throws IOException {
        String fg = source("ForegroundTracker.java");
        String update = bodyOf(fg, "private void updatePolling()");
        assertTrue("轮询必须同时依赖模块、白名单与屏幕状态",
                update.contains("setPolling(isTrackingNeeded())"));
        assertTrue("共享门控必须检查三项状态",
                bodyOf(fg, "private boolean isTrackingNeeded()")
                        .contains("moduleEnabled && whitelistActive && screenInteractive"));
        String config = bodyOf(fg, "void updateConfig(boolean whitelistActive, boolean moduleEnabled)");
        assertTrue("重新启用且屏幕亮起时必须刷新前台状态", config.contains("if (isTrackingNeeded())"));
    }

    @Test
    public void perPoemLoggingIsRateLimited() throws IOException {

        String log = source("Log.java");
        assertTrue("必须有限频闸门", log.contains("public static boolean allow("));
        String ctl = source("SystemUiController.java");
        String startCrawl = bodyOf(ctl,
                "public void startCrawl(PoemItem poem, String displayText, int speedPx)");
        assertTrue("POEM start 必须限频", startCrawl.contains("Log.allow("));
        String clock = bodyOf(ctl, "public void scheduleClock(int seconds)");
        assertTrue("clock hold 必须限频", clock.contains("Log.allow("));
        String state = bodyOf(ctl, "public void onStateChanged(PoemEngine.State state)");
        assertTrue("状态日志必须限频", state.contains("Log.allow("));

        assertTrue("暂停必须无条件记录",
                state.contains("state == PoemEngine.State.PAUSED"));

        String rt = source("PoemRuntime.java");
        String start = bodyOf(rt, "boolean start(String text, int speedPx, boolean bob, Listener l)");
        assertFalse("POEM hole root 不得单独成一条（已并入 POEM start）",
                start.contains("POEM hole root="));

        String sampler = source("ColorSampler.java");
        assertTrue("取色诊断间隔必须放宽到分钟级",
                sampler.contains("DIAG_LOG_MS = 300_000L"));
    }
    @Test
    public void poemRotationDoesNotAddOrRemoveViews() throws IOException {
        String rt = source("PoemRuntime.java");
        String stop = bodyOf(rt, "void stop()");
        assertFalse("换首不得移除 Overlay", stop.contains("removeOverlay()"));
        assertTrue("必须清掉文字，否则会留在屏幕上", stop.contains("clearText()"));
        assertTrue("必须停帧", stop.contains("stopFrame()"));

        String setContainer = bodyOf(rt, "boolean setStatusContainer(View v)");
        assertTrue("状态栏窗口重建时必须摘掉旧诗句层",
                setContainer.contains("removeOverlay()"));
        assertTrue("容器替换必须能通知控制器复位", setContainer.contains("return replaced"));
        String attach = bodyOf(source("SystemUiController.java"),
                "private void onStatusBarAttached(View window)");
        assertTrue("容器替换后必须复位状态机",
                bodyOf(source("SystemUiController.java"), "private void tickInner()")
                        .contains("view-replaced"));
        assertFalse("attach 不得绕过熄屏判定直接安排固定心跳",
                attach.contains("startHeartbeat()"));
        assertTrue("attach 必须走尊重屏幕/启用状态的心跳调度",
                attach.contains("scheduleHeartbeat()"));
    }

    @Test
    public void configFetchFailuresBackOffExponentially() {
        assertEquals("无失败不延迟", 0L, SystemUiController.configRetryDelayMs(0));
        assertEquals(8000L, SystemUiController.configRetryDelayMs(1));
        assertEquals(16000L, SystemUiController.configRetryDelayMs(2));
        assertEquals(32000L, SystemUiController.configRetryDelayMs(3));
        assertEquals("封顶 60 秒", 60000L, SystemUiController.configRetryDelayMs(4));
        assertEquals("长期失败仍封顶", 60000L, SystemUiController.configRetryDelayMs(100));
    }

    @Test
    public void configRetryBackoffIsWiredAndObserverResetsIt() throws IOException {
        String ctl = source("SystemUiController.java");
        String reload = bodyOf(ctl, "private void maybeReloadConfig()");
        assertTrue("必须按失败次数算退避", reload.contains("configRetryDelayMs(configFetchFailures)"));
        assertTrue("失败必须累计次数", reload.contains("configFetchFailures++"));
        assertTrue("成功必须清零", reload.contains("configFetchFailures = 0"));
        assertFalse("失败不得再把 dirty 置 true 绕过退避",
                reload.contains("configDirty = true;"));
        String observer = bodyOf(ctl, "private void registerConfigObserver()");
        assertTrue("observer 的真实变更必须立即重试", observer.contains("configFetchFailures = 0"));
    }
    @Test
    public void foregroundListenerRegistersOnlyWhenNeeded() throws IOException {
        String fg = source("ForegroundTracker.java");
        assertTrue("必须有注册门控", fg.contains("ensureListenerRegistered()"));
        String gate = bodyOf(fg, "private void ensureListenerRegistered()");
        assertTrue("只在需要跟踪时注册", gate.contains("!isTrackingNeeded()"));
        assertTrue("只尝试注册一次", gate.contains("registrationAttempted"));
        String sync = bodyOf(fg, "void syncPolling(boolean screenInteractive)");
        assertTrue("亮屏/熄屏变化后必须补注册监听", sync.contains("ensureListenerRegistered()"));
        String proxy = bodyOf(fg, "private boolean tryRegisterTaskStackListener()");
        assertTrue("任务事件必须按跟踪条件过滤", proxy.contains("foregroundEvent && isTrackingNeeded()"));
    }

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

    @Test
    public void configFetcherReusesItsHandler() throws IOException {
        String fetch = source("ConfigFetcher.java");
        assertTrue("必须复用 Handler", fetch.contains("static final android.os.Handler MAIN"));
        String async = bodyOf(fetch, "static void fetchAsync(Context ctx, Callback cb)");
        assertFalse("不得在每次请求里 new Handler", async.contains("new android.os.Handler"));
        assertTrue("必须用复用的那个", async.contains("MAIN.post("));
    }
}
