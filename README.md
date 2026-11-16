# 状态栏古诗（Status Bar Poem）

极简 LSPosed 模块：**只在系统状态栏滚动古诗**，诗句播完恢复原生状态栏；时间停留结束后继续下一首。

- 包名：`io.github.rude56.poemstatus`
- 版本：1.0.0（versionCode 1）
- minSdk 33 / compileSdk 37 / targetSdk 37
- LSPosed 作用域：**仅 `com.android.systemui`**，且是 **静态作用域**（`staticScope=true`，用户改不了）
- 不申请任何联网权限

---

## 一、功能

| 功能 | 说明 |
|---|---|
| 滚动诗句 | 从状态栏右侧进入，向左滚动并完全离开 |
| 遮挡 | 诗句经过时隐藏与之相交的状态栏模块，走过即恢复**原始 alpha** |
| 播完恢复 | 移除 Overlay，完整恢复所有模块，显示系统原生时间 |
| 时间停留 | 0–600 秒，结束后自动下一首 |
| 诗库 | 内置 6 首；可增删改、启停、上移下移；重复添加会被拒绝 |
| 升级语义 | 删掉的内置诗**不会**因升级复活；诗库删空后保持为空 |
| 作者 / 出处 | 各自独立显示开关；隐藏后实际显示文本参与宽度与遮挡计算 |
| 播放顺序 | 顺序循环 / 随机播放 |
| 速度 | 20–600 px/s，默认 120 |
| 跳过挖孔 | 默认开启；系统未上报挖孔时可用「真屏拖动矩形」手动指定 |
| 上下浮动 | 默认关闭；开启后**每首随机一个固定偏移（±1.5px）**，整首不变，不逐帧起伏 |
| 横屏暂停 | 默认开启 |
| 锁屏暂停 | 默认开启；锁屏界面不绘制诗句，解锁恢复 |
| 白名单应用 | 名单内应用在前台时暂停并恢复系统栏 |
| 总开关 | 默认**关闭**，需用户手动开启 |
| 界面 | Material 3 两层结构（底部 3 个 tab + 二级详情页），跟随系统动态取色与深浅色 |
| 概览页 | 主开关 + **LSPosed 连接自检** + 配置速览；开关关着时显示三步引导，开着但播不了时说清缺什么 |
| 连接自检 | 用「SystemUI 最后一次读配置的时间」判断模块是否真的注入成功；未连接时直接给出三步排查 |
| 诗库 | 左滑删除、长按拖动排序（行高实测）、点行编辑、**搜索过滤** |
| 导入导出 | 纯文本「诗句\|作者\|出处」，一行一条；导入自动跳过重复；导出可复制到剪贴板**或存成 .txt 文件** |
| 洞页 | 与诗库同一套 UI（卡片列表 → 点开编辑 → 滑删 → FAB 新增）；两个固定样例（隧道 / 虫洞各一行，白底黑字、位置写死、共用进度）；彩色框由 App 自己画在状态栏区域上，进页面即显示、离开即消失；**洞之间禁止重合**，编辑页有「居中」快捷按钮 |

---

## 二、省电设计（最高优先级）

本模块要在 SystemUI 里 24 小时常驻，所以「空闲时能不能真的睡着」比峰值性能更重要。
下表是第 24 轮专项审计后的实测值（MEIZU 20 Pro，1440×3200@120Hz）：

| 场景 | 帧回调 | 定时器 | 实测 SystemUI CPU | 实测日志量 |
|---|---|---|---|---|
| **熄屏** | **0** | **0** | **2.72%**（系统 AOD 自绘，模块零贡献） | 仅 1 行状态变化 / 93 秒 |
| 锁屏 / 白名单 / 横屏暂停 | 0 | 0 | 4.35% | **0 行 / 3 分钟** |
| 诗句爬行中 | 31 fps | 0 | 20.15% | ~4 行 / 分钟 |
| 总开关关闭 | 0 | 1 个 30 秒心跳 | — | 0 |

关键手段：

1. 帧间隔**按速度自适应**：走 1 设备像素需要 `1000/speed` ms，取它与 33.3ms 的较大者（即不高于 30fps）。20px/s 时主线程只醒 20 次/秒，600px/s 才到 30 次/秒；
2. 掉帧直接跳到最新位置，**不补帧**；
3. 位移不足 1 设备像素不绘制（双保险）；
4. 帧内零分配：复用 `Paint` / `FontMetrics` / 数组，无 `new`、无 StringBuilder、无日志、无 IO、无 View 树扫描；
5. 只 `invalidate` 「本帧洞 ∪ 上一帧洞」的并集，且**一次调用**（不是每段一次）；
6. 换首**不增删 View**：诗句层常驻复用，只清文字 + 停帧 + 清洞（`addView/removeView` 会让整条状态栏重新布局）；
7. 取色**推送式**：系统改变状态栏外观时由 `TintState` 回调，250ms 合流后重算一次，**没有任何轮询定时器**（Hook 失效时才降级到 2 秒兜底）；
8. **熄屏后所有定时器归零**：心跳不续期、前台轮询停止、配置不轮询。亮屏广播会把它们全部装回来；
9. 配置用 ContentObserver 推送 + 8 秒心跳兜底；旋转由 `ComponentCallbacks.onConfigurationChanged` 事件驱动，不靠轮询。

### 「每 N 秒白干活」清单

功能正确但持续耗电的问题最难发现 —— 它们不影响任何功能，光靠功能测试永远抓不到。
下面每一条都对应 `PowerRegressionTest` 里的一个用例，改动时请勿破坏：

| 曾经的问题 | 真实代价 | 现在的做法 |
|---|---|---|
| 暂停态每 3 秒调一次 `restoreModules()` | `HolePuncher.clear()` → `root.invalidate()`，**整条状态栏全量重绘** | `pause()` 只在「进入」暂停时恢复一次 |
| **熄屏后心跳仍固定 8 秒续期** | 一晚上 8 小时 = **3600 次无意义唤醒**，每次还顺带跑一遍 tick | `scheduleHeartbeat()` 熄屏直接返回；亮屏广播重新装回 |
| **熄屏后前台轮询仍每 5 秒反射查一次** | 同样 8 小时 = 5760 次唤醒，而屏幕是黑的，前台是谁毫无意义 | `syncPolling(白名单非空 && 屏幕亮着)` |
| **熄屏后配置仍每 30 秒跨进程 `call()`** | 一晚上 960 次；**App 进程已死时每次都会把它整个拉起来** | 熄屏时 `pollInterval = Long.MAX_VALUE`，只认 ContentObserver |
| **每首诗 7 行日志** | 13 秒一首 → **每天 4.6 万行写进 logd**（要落盘，是实打实的 IO） | 全部走 `Log.allow(key, ms)` 限频，`POEM hole root` 并入 `POEM start` → **每天约 5700 行** |
| **每次拉配置都 `new Handler`** | 24 小时几万个短命对象喂 GC | `static final Handler MAIN` |
| **前台解析每轮 `getMethods()`** | 每次新建 Method[] 并克隆参数类型数组；24 小时几万次 | 缓存 `taskMethodsCache` / `rootTaskMethodsCache` / `atmServiceCache` |
| **换首 `removeView` + `addView`** | 触发整棵状态栏 requestLayout + 重新挂载；连带重跑取色订阅与内容根 DFS | 诗句层常驻复用，`stop()` 只 `clearText()`；窗口重建时才摘 |
| **每首重新 `getIdentifier` + DFS 找内容根** | 每天约 6600 次资源表查询 + 子树递归 | `ViewTree.findContentRoot` 缓存，窗口换了才失效 |
| **`HolePuncher` 每帧只重绘「新洞」，旧洞的位置没人补画** | 诗句左移时右侧让出的条带保留上一帧挖掉的像素 → 图标缺一条且越拖越长 | 脏区 = 本帧洞 ∪ 上一帧洞，并成一次 invalidate |
| 没有洞可清时也 `invalidate()` | 同上，纯浪费 | `clear()` 先判断是否真的挖过洞 |
| `refreshCutout()` 被心跳无条件调用 | 重新解析洞 + `new int[]` + 拼日志 + 全条重绘 | 按 `holesJson` + 状态栏高度去重 |
| **系统挖孔在去重块外面** | 每个心跳都重算坐标转换 + 诗句层 `invalidate()` | 随该功能一并删除 |
| 每帧 `guard.guard("occlude", () -> ...)` | lambda 捕获 4 个 float，**每帧分配一个对象**（30/s） | 坐标写字段，`Body` 只建一次 |
| `guard` 成功路径 `fails.remove(id)` | 每帧一次 HashMap 写 | 新增 `guardFast`，成功路径不碰 Map |
| 配置每 2 秒跨进程 `call()` | SystemUI 每 2 秒醒一次；**App 进程死了会被反复拉起** | 前台是本 App 时 2 秒，否则 30 秒，熄屏不轮询 |
| 取色每 300ms 重扫整棵树 | 每秒 3 次全树 DFS | 弱引用缓存 + 推送式更新 |
| `CutoutSkipper.plan()` 从未被调用 | 「跳过系统挖孔」开关**永远无效** | 功能整体删除（详见本表下方说明） |
| 心跳固定 3 秒只为发现旋转 | 每次唤醒都在 `getRotation()` | 注册 `ComponentCallbacks`，旋转即置脏；心跳放宽到 8 秒 |
| 洞页样例 `awaitFrame()` + `progress += 0.0006f` | 120Hz 屏上**每秒重组 120 次**；且 60Hz 设备上「14 秒一轮」变成 28 秒 | 用 `withFrameNanos` 按时间推进 + 节流到 30fps |
| **洞列表只在配置变化时推给诗句层** | 全新启动那次推送被跳过（Overlay 还没创建），`holesJson` 去重又挡住后续推送 → **重启后不遮不跳，非得新加一个洞** | `ensureOverlayAttached()` 里补推一次 |
| **前台刷新节流丢状态** | 任务栈监听 250ms 后触发，落在 400ms 节流窗口里被丢弃且无人补跑 → 白名单**永久停在过时值** | 记 `pendingRefresh` 并排补跑 |
| **白名单解析只试一个 `getTasks` 重载** | 签名逐版本变过，失败即静默返回空串 | 遍历所有 `getTasks` 重载 + 焦点栈 + `getRunningTasks` |
| **只信任务栈监听** | 本机 ROM 上注册成功但**一次都不回调** | 白名单非空时始终保留低频兜底轮询 |
| **每帧两次 `getLocationOnScreen`** | 帧内两次系统调用取同一个常量 | 缓存到 `screenOriginX/Y`，只在挂载/布局变化时刷新 |
| **`wormholeSegments` 每帧算两遍** | 绘制层与遮挡层各扫一遍全部虫洞 | 帧里算一次，两层共用结果 |
| **`FramePacer` 一半是死代码** | 测试逐条断言 `advance/delayMs/shouldDraw`，生产代码零调用 → **测试覆盖的是假实现** | 删掉死方法，只测真正被调用的 `intervalNsForSpeed` |
| **熔断永久不恢复** | `FeatureGuard.reset()` 生产代码零调用，3 次异常后该进程内**永久失效且无日志** | 熔断打日志 + 每 N 次调用一次低频试探，成功即复位 |
| **「跳过系统挖孔」与虫洞互斥** | 挖孔把诗句切多段时提前 `return`，跳过隧道裁剪与虫洞分段 → **横跨挖孔那一帧虫洞整个失效** | 功能整体删除（用户不再需要它，且它与虫洞语义冲突） |
| **无洞路径传本地坐标、有洞路径传屏幕坐标** | 只因状态栏原点恰好为 0 才没暴露，换个布局整体偏移 | 所有出口统一加 `screenOriginX`，契约写进注释 |
| **洞页预览用 `dp`、运行时用 `sp`** | 系统字体缩放 ≠ 1 时预览比真实偏小，所见非所得 | 预览也改用 `sp` |
| **release 未开 R8** | APK 22.9 MB，绝大多数是 Compose | `minifyEnabled` + `shrinkResources` → **2.1 MB** |
| **虫洞传送了文字，却没传送遮挡** | 可见性判据拿**未位移的轨道左端**去比**已位移的屏幕右端**，跨过洞口的那一段被整段丢弃 → 字画在洞左边，遮挡没跟过去（最宽漏遮 ≈ 一个洞宽，实测 170px / 301 帧） | 分段整体下沉为纯函数 `HoleRects.visibleSegments`，裁边界改用**状态栏左右沿**；补逐帧像素覆盖测试 |
| **`holesJson` 的 JSON 兼容分支丢模式与槽位** | 该分支手工拼 `"l:r"`（只有两个字段），隧道洞读回来变成虫洞、颜色重排 | 改走 `HoleRects.encode()`，保真；补 `jsonHolesKeepModeAndSlot` |
| **画布裁剪用单一计数，`View.draw` 重入即永久残留** | 内层 `beginClip` 清零计数 → 外层 `endClip` 以为无事可做而不 restore → 裁剪永久留在内容根上，图标缺一块且无日志 | 抽出 `ClipStack`（真正的栈 + `restoreToCount`），hook 只在 `beginClip` 返回 true 时才 `endClip` |
| **一首诗的结束判据没考虑虫洞** | `left + width < 0` 假设轨道坐标 = 屏幕坐标，而虫洞把整句往左瞬移一个洞宽 → 屏幕上早已无字，却还要空等「洞宽 ÷ 速度」。实测本机（洞宽 980px / 194px/s）**每首空等 5.1 秒**，且这段屏幕全黑 | `HoleRects.finishLeft(holes, textWidth)` = `-textWidth + 总洞宽`；用真实分段逐帧验证「最后一个可见像素」与判据一致 |
| **洞页预览跑到 14 秒就冻结** | 进度被夹取到 1 且起点从不重置 → 两行永久停在末尾、都是空的，对比彻底失效 | `PreviewClock.progressAt` 按周期取模，永续循环 |
| **预览两行不同步** | 循环按整条轨道 `[0,1]` 走，而虫洞行提前 19% 走空 → 每轮 2.7 秒只剩隧道行还在滚 | 循环终点收缩到 `PreviewClock.cycleEnd`（两行都还有字的范围），到点立刻重启 |

> 上述 CPU 数据在 MEIZU 20 Pro（1440×3200@120Hz，SystemUI 常驻）上实测，方法为 `/proc/<pid>/stat` 的 utime+stime 差值（60~120 秒窗口），帧率取 `dumpsys gfxinfo` 的 `Total frames rendered` 差值。
>
> **结束判据的实测验证**：设备当时配置 `300:1280:wormhole`（洞宽 980px）、速度 194px/s。
> 修复后每首固定省下 `980/194 = 5.1` 秒。实测 6 个循环间隔为
> `12.77 / 8.48 / 8.72 / 9.74 / 9.01 / 8.84` 秒，全部落在修复后的预期区间 `6.3~13.6` 秒内，
> 而修复前的预期区间是 `11.4~18.6` 秒 —— 其中 8.48、8.72、9.01、8.84 四个值
> **低于修复前的下限**，旧版本不可能出现。
>
> **为什么删掉「跳过系统挖孔」**：它经历过三个阶段 —— 先是「穿过了 5 层参数却从没被调用」
> （开关永远无效），修好后又和虫洞打架：系统挖孔把诗句切成多段时会提前 `return`，
> 跳过下面的隧道裁剪与虫洞分段，于是**诗句横跨挖孔的那一帧，虫洞传送整个失效**，
> 字直接压在图标上。两个功能对「这一段该不该画」的答案根本不一致。
> 加上本机状态栏是 Compose 整块绘制的，挖孔避让的实际收益很小，所以整体删除，
> 只保留用户自己的「洞」。


---

## 三、架构

```
app/
  MainActivity                   唯一 Activity（Compose + Material 3 + 边到边）
  ui/                            设置界面
    PoemApp                      导航骨架：一级底部 tab / 二级返回栈
    OverviewScreen               概览：主开关 + 连接自检 + 配置速览 + 首次引导
    LibraryScreen                古诗库（列表、拖动排序、启停、导入导出、删除撤销）
    PoemEditScreen               新增 / 编辑诗句（整页，避让键盘）
    SettingsScreen               设置（播放 / 显示 / 洞 / 暂停 / 维护）
    CutoutScreen                 洞（实时预览 + 每个洞的左右边界与模式）
    WhitelistScreen              白名单应用
    AboutScreen                  关于（作用域、重启、隐私）
    PoemStripLayer               预览用诗句层（复用真实分段算法）
    components/Common.kt         统一设置行组件（SectionHeader/SettingsCard/SwitchRow/...）
    theme/Theme.kt               Material 3 主题（动态取色 + 深色 + 圆角 + 字体）
  PoemLibrary                    诗库与顺序（纯逻辑）
  ConfigProvider                 只读 ContentProvider
  ModuleMain                     现代 libxposed 入口（声明静态作用域）
  SystemUiController             Hook SystemUI、生命周期与暂停判定
  PoemRuntime                    状态机副作用、Overlay、动画
  HoleRects                      洞的解析/编码/分段几何（纯逻辑）
                                   wormholeSegments  绘制分段（传送位移）
                                   visibleSegments   遮挡分段（必须与绘制对齐）
                                   subtractTunnels   减掉隧道洞（那段不遮）
  ClipStack                      画布裁剪的 save 栈（防 View.draw 重入）
  HolePuncher                    洞挖孔引擎（在内容根上 clipOutRect）
                                   脏区 = 本帧洞 ∪ 上一帧洞，一次 invalidate
  ViewTree                       状态栏 View 树只读查找（找内容根 / 时钟 / 文字）
                                   内容根按窗口缓存，换首不再 DFS
  Reflect                        现代 API 的反射小工具（找类/找方法/读字段/调方法）
  Log                            日志门面（`allow(key, ms)` 限频，24 小时常驻必需）

模块声明（现代 libxposed，不再用 meta-data）：
  src/main/resources/META-INF/xposed/java_init.list   入口类
  src/main/resources/META-INF/xposed/module.prop       minApiVersion/targetApiVersion/staticScope=true
  src/main/resources/META-INF/xposed/scope.list        com.android.systemui

纯逻辑（无 android.* 依赖，JVM 可测）：
  ConfigKeys / PoemConfig / ConfigCodec / PoemItem / PoemLibrary / Playback
  PoemEngine（状态机）/ FramePacer
  HoleRects / ColorResolver / FeatureGuard / FeatureAudit
```

状态机：`IDLE → CRAWL → CLOCK → CRAWL …`，任一状态可 `→ PAUSED`。
进入 `CLOCK` / `PAUSED` 必须停帧回调并 `restoreModules()`。

Hook 点：`com.android.systemui.statusbar.window.StatusBarWindowView.onAttachedToWindow`。

### 界面结构（Material 3 两层）

| 层级 | 页面 | 导航 |
|---|---|---|
| 一级 | 概览 / 古诗库 / 设置 | 底部导航栏，三个 tab 平级互切、不压栈 |
| 二级 | 洞 / 白名单 / 关于 / 编辑诗句 | 顶部返回箭头 + 系统返回键退栈；二级页隐藏底部栏 |

设计约束（由 `SourceAuditTest` 钉住）：

- 一级页用 `TopAppBar`，二级页用 `TopAppBar` + `ArrowBack`；
- 底部 `NavigationBar` 只在 `current == null` 时出现；
- `BackHandler` 先退二级页，退到一级页才交还系统；
- 主题必须是 `lightColorScheme` / `darkColorScheme` + `dynamicLightColorScheme` / `dynamicDarkColorScheme`；
- 概览页只有主开关，不得再出现效果预览与快捷入口；
- 洞页必须有两个固定样例（隧道 / 虫洞各一行，位置写死、共用进度）；
- 状态栏上的彩色框只能由洞页开关控制，**离开页面必须自动关闭**。

### 洞的彩色框：为什么现在敢画在状态栏上

之前不敢画，是因为要靠 SystemUI 进程判断「App 是否在前台」——
任务栈回调在部分 ROM 上缺失、`getRunningTasks` 受限，结果框会闪现甚至常驻。

现在的做法是把判断挪到 App 侧：

```
进入洞页 → 用户打开开关 → 写 cutoutPreview=true → SystemUI 挂预览层
离开洞页 → DisposableEffect.onDispose → 写 cutoutPreview=false → SystemUI 摘掉
```

跨进程只传一个布尔值，不依赖任何前台判断。预览层是**独立实例**，
与播放层完全分开：没有文字、没有帧回调，因此零功耗，也不参与遮挡计算。

### App ↔ SystemUI 通信

- App 写 SharedPreferences；
- `ConfigProvider` 以 `call("poem_get_config", …, {token})` 返回 JSON（只读，无写入口）；
- SystemUI 经 ContentResolver 读取，**不读 App 私有文件、不联网**；
- 失败保留上一份有效配置，不崩溃；
- `configRevision` 变化才重建播放器。

---

## 四、测试

```bash
cd statusbar-poem
./gradlew :app:testDebugUnitTest
```

**218 个 JVM 用例全部通过**，覆盖：

1. 默认诗库 6 首
2. 显式空诗库保持为空
3. JSON 往返（中文 / 转义 / 空字段）
4. 删除内置诗不复活
5. 顺序播放从第一首开始
6. 随机播放返回有效对象且不连续重复
7. 重复检测
8. 配置字段与保存键一一对应
9. App 写入键与 Hook 解析键一致
10. 暂停路径存在 `restoreModules()`
11. 时间阶段无 FrameCallback
12. 自动取色不进入逐帧函数（源码审计）
13. 帧内零分配 / 零采样 / 零日志（源码审计）
14. 挖孔片段切分、外扩 1px、最多 3 段
15. 遮挡恢复使用原始 alpha，不统一置 1f
16. FeatureAudit：`古诗库` 标题、scope 仅 SystemUI、无 INTERNET 权限
17. 帧节流：33.3ms 对齐、掉帧不补、<1px 跳过
18. 虫洞分段几何：900 帧扫描下分段不重叠、不丢字、诗句更早离场
19. 隧道不影响位置、不遮挡状态栏
20. 洞只作用于诗句层，绝不挖穿状态栏
21. 预览层永远不往状态栏 `addView`
22. 遮挡位移只发生一次（帧位置不被洞改写）
23. **界面结构**：一级恰好 3 个 tab、二级页有返回箭头、`BackHandler` 先退栈、底部栏受 `current == null` 控制
24. **主题**：Material 3 动态取色 + 深色 + 圆角 + 字体，`MainActivity` 必须用 `PoemTheme` 且边到边
25. **概览页**：主开关卡片、连接自检、配置速览、首次引导（仅关闭时出现）
26. **洞页**：数值输入、多洞、每洞模式、改数值防抖 400ms
27. **诗库/编辑页**：稳定 key、整行可点、编辑页 `imePadding()` 避让键盘、可删除
28. 不得残留 `debug_screen` 调试入口
29. **纯文本编解码**：三字段解析、作者/出处可省、全角竖线与制表符、行首序号、嵌套《》、
    Windows 换行、往返无损、内置 6 首往返一致
30. **导入合并**：重复全部跳过、只加新的、导入空库全加
31. **概览页只留主开关**：不得再出现预览与快捷入口；开关开着但播不了时必须提示原因
32. **滑动删除与导入导出**：`SwipeToDismissBox`、只允许右→左、滑动后先确认
33. **洞页两个固定样例**：隧道+虫洞同时演示、位置写死、共用进度
34. **彩色框由 App 自己画**：按状态栏 inset 定位、颜色取洞自己的槽位、框内不标数字；
    不得再依赖 SystemUI 预览层
35. **双虫洞回归**：第二个洞的触发点必须补上右侧洞宽之和；逐帧扫描 2200 帧验证
    位移与分段不重叠
36. **洞页与诗库同 UI**：卡片列表 + 点开编辑 + 滑删 + FAB；列表里不得有输入框
37. **新增洞不改色**：颜色按槽位存，新洞取空闲槽位
38. **连接自检**：只认调用方是 SystemUI 的读取，未连接时必须给出三步排查
39. **省电回归（`PowerRegressionTest`）**：暂停态不得反复恢复系统栏、没洞可清时不得重绘、
    洞配置重建必须去重、每帧回调不得分配对象、`guardFast` 成功路径不碰 Map、
    配置轮询必须分档、取色必须推送式且合流；**熄屏必须停掉全部定时器**、
    每首日志必须限频、换首不得增删 View、前台反射必须缓存
40. **「跳过系统挖孔」已整体删除**：`CutoutSkipper` 类必须不存在，
    运行时/配置/设置页/字符串都不得残留 `skipCutout`（删除原因见「每 N 秒白干活」表下方说明）
41. **SystemUI 侧预览层已彻底移除**：不得残留 `overlayPreview` / `previewFill` / `showHolePreview`
42. **洞编辑页不得在组合期间改导航栈**（`LaunchedEffect` 退出）
43. **添加洞失败必须给反馈**（达到上限 / 没地方放）
44. **旧 alpha 遮挡方案已整体删除**：`ModuleOccluder` / `OcclusionMode` / `OcclusionPlanner`
    三类文件必须不存在，且运行时不得引用；帧内不得出现任何 `setAlpha`
45. **帧间隔自适应**：`intervalNsForSpeed` 纯函数验证（低速放宽、高速封顶、非法速度安全），
    并钉住运行时确实在用它
46. **心跳放宽 + 旋转事件驱动**：心跳 ≥8 秒，必须注册 `ComponentCallbacks`
47. **分段的重复计算已消除**：`wormholeSegments` 全文件只允许出现一次，
    绘制层与遮挡层共用帧里算好的结果
48. **洞禁止重合**：`parse()` 丢弃相交洞、`sanitize()` 保持顺序稳定、
    `overlapsAny` / `firstOverlap` 排除自身槽位、首尾相接不算重合
49. **App 侧不阻塞主线程**：重启 SystemUI 必须是 `suspend` + `Dispatchers.IO`
50. **拖动排序行高必须实测**（不得写死 `108f`）
51. **洞页样例按时间推进**（`withFrameNanos` + 30fps 节流），不得按帧数累加
52. **概览页连接自检绑生命周期**（`repeatOnLifecycle(STARTED)`）
53. **保存诗库必须先 `copy()`**（否则 structural equality 会吞掉状态通知）
54. **洞列表在 Overlay 创建时也要推**：全新启动时 `refreshCutout()` 早于 Overlay 创建，
    那次推送被跳过；只靠 `holesJson` 去重不会补 —— 这就是「重启后非得新加一个洞」的根因
55. **前台刷新节流不得吞状态**：必须 `pendingRefresh` + 补跑
56. **前台包名解析必须多候选**：遍历 `getTasks` 所有重载、焦点栈、
    `topActivity`/`baseActivity` 字段名、`getTopActivity` 方法
57. **白名单匹配必须宽容**：忽略首尾空白与大小写
58. **前台变化必须留日志**（`CONF foreground=` + `whitelisted -> pause`），

    否则白名单失效时无从判断
59. **静态作用域**：`META-INF/xposed/scope.list` 只能有 `com.android.systemui`，
    `module.prop` 必须 `staticScope=true`，且旧的 `xposedmodule`/`xposedscope`/
    `xposedminversion` meta-data 与 `assets/xposed_init` 必须全部消失
    （否则 LSPosed 按 legacy 模块处理，`staticScope` 直接不生效）
60. **熔断必须能自愈**（`FeatureGuardTest`，行为断言）：熔断期拒绝执行、
    低频试探成功后自动复位、试探失败保持熔断、不同 id 互不影响
61. **现代入口只处理 SystemUI**：`ModuleMain` 按包名过滤，
    且两处主文件不得再出现 `de.robv.android.xposed` / `XposedHelpers` / `XC_MethodHook`
62. **遮挡必须跟着传送后的文字走**（`OcclusionCoverageTest`，行为断言）：
    逐帧比较「文字层画出的像素集合」与「遮挡层挖掉的像素集合」，前者必须被后者
    完整覆盖。覆盖 `单虫洞 / 宽虫洞 / 虫洞+隧道 / 双虫洞` 四组配置，每组约 990 帧；
    另外钉住「没洞时遮挡裁到状态栏范围内」「整句离屏时一个洞都不挖」
    「隧道内部永不被遮挡」「分段严格递增且两两不重叠」
63. **配置不变量**（`ConfigInvariantTest`）：`copy()` 必须覆盖<b>每一个</b>字段
    （反射检查，漏一个就失败）、编解码往返无损且幂等、`holesJson` 的 JSON 兼容分支
    必须保住模式与颜色槽位、诗库文本往返无损、「导入自己刚导出的内容不新增任何条目」、
    `poemsSeeded` 后空库保持为空
64. **画布裁剪必须是栈**（`ClipStackTest`）：`View.draw` 重入时内层不得抹掉外层保存点；
    空栈出栈安全返回 -1；深度超限拒绝而不破坏栈；反复 10000 次 push/pop 无残留
65. **遮挡在非零原点下同样正确**：同一帧按 `originX = 0 / 137 / 320` 分别验证
    「文字画在哪，遮挡就在哪」，且遮挡段绝不越出状态栏边界
66. **24 小时常驻不空转**（`PowerRegressionTest` 新增 6 条）：
    熄屏必须让心跳 / 前台轮询 / 配置轮询**全部归零**，且亮屏广播能重新装回；
    每首必打的日志必须限频（`Log.allow`），暂停状态必须无条件打得出来；
    换首不得 `addView/removeView`，但状态栏窗口重建时必须摘掉旧诗句层；
    前台解析的方法列表与服务单例必须缓存；每次拉配置不得新建 `Handler`
67. **重绘区必须覆盖旧洞**（`OcclusionCoverageTest2`，行为断言）：
    逐帧验证「本帧重绘区 ⊇ 上一帧的洞」，覆盖整段轨迹 100+ 帧；
    字整段落进隧道（0 段）时仍必须擦掉上一帧的洞，且擦干净后不再产生无谓重绘；
    多段帧必须给出一次并集框而不是每段一次
68. **一首诗何时结束必须按「屏幕上最后一个字消失」判定**（`PoemFinishTest`）：
    旧判据 `left + width < 0` 假设「轨道坐标 = 屏幕坐标」，而虫洞把整句往左瞬移一个洞宽 ——
    屏幕上早就没字了，轨道还要再走一个洞宽。空等时长 = 洞宽 ÷ 速度。
    用真实分段算法逐帧扫描「最后一个可见像素」，与 `HoleRects.finishLeft` 比对，
    覆盖 `无洞 / 短虫洞 / 宽虫洞 / 超宽虫洞 / 双虫洞 / 虫洞+隧道` 六组配置；
    另外钉住「隧道不改变结束时机」「省下的时间正好等于洞宽÷速度」「多虫洞按总宽累加」
69. **洞页两行预览必须同时有古诗**（`PreviewClockTest`，行为断言）：
    两行存在的唯一理由就是逐帧对照，任一行提前走空即失效 —— 且不会报错。
    验证进度按周期取模而非夹取（否则 14 秒后永久冻结在两行全空）、
    循环终点收缩到「两行都还有字」、`[0, cycleEnd)` 内 400 点取样两行都必须有字；
    并用反证断言「按整条轨道循环时确实存在只有一行有字的时段」，
    防止有人把 `cycleEnd` 改回去
70. **搜索态拖动排序只在可见的诗之间换位**（`PoemLibraryTest`，行为断言）：
    显示的是过滤后的列表，旧实现却按整表下标换位 —— 搜索时长按拖动屏幕毫无反应，
    实际在和看不见的诗悄悄换位，松手后顺序已错。验证越过被过滤的诗、
    隐藏诗原地不动（用交换而非删除再插入）、到头返回 false 且不改列表、
    连续 200 次移动不丢诗不重复
71. **导入导出闭环**：导出走 `CreateDocument`、导入走 `OpenDocument`，
    两者都过 `contentResolver` 且在 IO 线程；不得残留 `PendingExport` 静态槽位
72. **删除可撤销**：列表滑删与编辑页删除都提供撤销，且还原的是删除前的整份列表
73. **导航栈扛得住进程被杀**：`rememberSaveable` + `listSaver`，未知编码返回 null 不崩
74. **排序有无障碍替代路径**：长按拖动之外必须有自定义无障碍操作（上移/下移），
    否则读屏用户无法调整顺序
75. **滑块可精确输入**：数值可点开编辑、数字键盘、超范围夹取而非报错
76. **白名单已选项置顶**：按「是否已选」排序，组内按名称稳定排序
77. **没有死的字符串资源**：递归扫描全部源码，`strings.xml` 里每个条目都必须有人引用
    （`app_name`/`module_description` 由清单引用，单独放行）
78. **界面文案不出现开发者术语**：拦 `SystemUI`/`注入`/`静态作用域` 等词，
    统一说「系统界面」「装进去」

---

## 五、构建

```powershell
# 1) 自举工具链（首次，下载 JDK21 / cmdline-tools / Gradle / SDK 到 .toolchain）
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1

# 2) 构建 + 测试
powershell -ExecutionPolicy Bypass -File scripts/build.ps1

# 3) 打包交付物到 dist/
powershell -ExecutionPolicy Bypass -File scripts/package.ps1
```

产物：

| 文件 | 大小 | 用途 |
|---|---|---|
| `dist/statusbar-poem-v1.0.0-release.apk` | **2.1 MB** | **日常装这个**（R8 裁剪过） |
| `dist/statusbar-poem-v1.0.0-debug.apk` | 22.9 MB | 只在需要完整堆栈/热修时用（不做任何裁剪） |
| `dist/statusbar-poem-v1.0.0-src.zip` | 0.2 MB | 源码 |
| `dist/SHA256SUMS.txt` | — | 上面三个的校验和 |

两个 APK 用同一个签名，可以互相覆盖安装。

### 为什么 debug 版比 release 大 10 倍

模块自己的逻辑只有几十 KB，那 22 MB 几乎全是 **Compose + Kotlin 运行时**：

| 组成 | debug | release |
|---|---:|---:|
| Compose UI / Foundation / Material3 全部符号 | 16.3 MB | — |
| Kotlin 运行时 + 标准库 | 4.6 MB | — |
| 其余 dex | 0.8 MB | — |
| **dex 合计** | **21.8 MB** | **1.9 MB** |

debug 版**完全不做裁剪**（`minifyEnabled false`），Compose 那几千个类和
Kotlin 的 `kotlin/` 包全部原样打进去。release 开 R8 之后这些没被调用的符号
被删掉、剩下的被混淆压缩，于是只剩 1.9 MB。

> 顺带一提：`io.github.rude56` 的类在 debug 的 `classes.dex` 里检索到 **0 次** ——
> 不是没打进去，而是本模块代码体量太小，它的字符串全落进了 dex 的公共字符串池，
> 按包名做子串匹配自然搜不到。


### 固定签名

`keystore/debug.keystore`（storepass/keypass = `android`，alias = `androiddebugkey`）已随仓库提交，
后续版本可直接覆盖安装，签名不会随构建机变化。

---

## 六、安装与启用

1. 安装 APK：

   ```bash
   adb install -r statusbar-poem-v1.0.0-debug.apk
   ```

2. 打开 **LSPosed** → 模块 → 勾选 **状态栏古诗**。
   作用域**不需要手动勾**：模块以静态作用域声明（`staticScope=true`），
   作用域固定为 **系统界面（com.android.systemui）**，LSPosed 里那一栏是只读的；
3. 重启 SystemUI：

   ```bash
   adb shell su -c "killall com.android.systemui"
   ```

   > 安装/更新模块后必须重启 SystemUI，否则新代码不会加载。

4. 打开 **状态栏古诗** → 打开 **启用模块** 开关（默认关闭）。

### 日志验证

```bash
adb logcat -s PoemStatus
```

至少应看到：

```
INJECTED process=com.android.systemui pid=… sdk=37 api=102 framework=LSPosed 2.2.1
POEM hook class=com.android.systemui.statusbar.window.StatusBarWindowView …
POEM hook installed scope=com.android.systemui
CONF apply ver=… enabled=true poems=6 speed=120 clock=10
POEM start id=… text=… speed=120
POEM clock hold 10s
POEM paused restore=ok
```

---

## 七、实测记录（MEIZU 20 Pro / KernelSU / LSPosed 2.2.1）

| 项目 | 结果 |
|---|---|
| 注入 | ✅ `INJECTED SystemUI`、`POEM hook installed` |
| 配置推送 | ✅ `CONF observer=on`、`CONF apply ver=46` |
| 滚动 | ✅ 截图可见诗句从右向左滚动 |
| 遮挡 | ✅ 仅诗句经过的模块变化，原状态栏其余部分不动 |
| 恢复原生时间 | ✅ 移除 Overlay 后原生时间完整回来 |
| 暂停恢复 | ✅ 锁屏 / 熄屏 / 横屏 均 `POEM paused restore=ok` |
| 功耗 | ✅ 爬行 6.05% vs 基线 6.50% |
| 挖孔 | ⚠️ 本机 ROM 未定义 `config_mainBuiltInDisplayCutout`，`DisplayCutout` 恒为空，故改为「手动填左右边界」的多个矩形 |

### 洞（虫洞 / 隧道）实测

| 项目 | 结果 |
|---|---|
| 虫洞（传送门） | ✅ 设备确认正常：字到洞口右沿瞬间出现在左沿，洞里不画 |
| 隧道 | ✅ 古诗那段被遮住，位置与速度不变；状态栏图标正常显示 |
| 洞不遮挡状态栏 | ✅ 洞只作用于古诗层，原生内容永不受影响 |
| 多洞 | ✅ 位移依次累加，全帧扫描无重叠、不丢字 |
| 虫洞的遮挡 | ✅ 遮挡与传送后的文字严格同步：逐帧像素覆盖测试（4 组配置 × ~990 帧）零漏遮 |
| 设置页预览 | ✅ 内嵌动画预览与状态栏同一套算法，无需装机即可看效果 |
| 框只在设置页 | ✅ 状态栏上永不存在预览层 |

> 虫洞语义经过多轮澄清才定稿，最终规则写在 `HANDOFF.md` 第三节。
> `HoleGeometryTest` 用全帧扫描断言「不重叠、不丢字」，是防止回退的唯一防线。
> `OcclusionCoverageTest` 补的是另一半：**文字画在哪里，遮挡就必须落在哪里** ——
> 它逐帧比较两个像素集合，另一层算错一个洞宽都会被抓到。

### 已知限制

1. **本机状态栏是 Compose 实现**：时间、信号、WiFi、电池由同一个 `AndroidComposeView` 整体绘制，
   能被单独隐藏的 View 很少。唯一的遮挡方式是「挖洞」：
   Hook `status_bar_container` 的 `View.draw(Canvas)`，按诗句真实可见的分段
   `clipOutRect`，于是诗句下方的内容消失、其余部分完全不动。
   （早期那套「逐个 View 改 alpha」的实现已整体删除，不存在任何模式开关。）
2. **锁屏暂停**依赖 `KeyguardManager.addKeyguardLockedStateListener`；
   老系统无此 API 时退回广播 + 心跳重算，延迟最多 3 秒。
3. 未做 GitHub Actions（按需求确认只保留本地脚本）。

---

## 八、目录

```
statusbar-poem/
├── app/                       应用与 Xposed 模块
│   └── src/main/java/io/github/rude56/poemstatus/
│       ├── ui/                设置界面（Material 3）
│       │   ├── components/    统一设置行组件
│       │   └── theme/         主题（动态取色 / 深色 / 圆角 / 字体）
│       └── *.java             Hook 侧与纯逻辑
├── keystore/debug.keystore    固定签名
├── scripts/                   构建 / 测试 / 打包 / 真机验证脚本
├── dist/                      交付物（APK + 源码 zip + SHA-256）
├── gradle/                    Gradle wrapper
├── HANDOFF.md                 交接说明
└── README.md
```

## 九、许可

仅供个人学习与自用。修改系统状态栏存在风险，请自行备份并确保能从 LSPosed 安全模式恢复。
