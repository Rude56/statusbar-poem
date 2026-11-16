# 安装与启用

## 前置条件

| 依赖 | 说明 |
|---|---|
| Root | KernelSU / Magisk |
| LSPosed | 2.x（支持 Android 14–17 的版本） |
| 系统 | Android 13（API 33）及以上；状态栏需为 `com.android.systemui` |

## 一、安装

```bash
# 日常使用：release 版，约 2 MB
adb install -r statusbar-poem-v1.0.0-release.apk
```

或在手机上直接点开 APK 安装。

| 版本 | 大小 | 什么时候用 |
|---|---|---|
| `-release.apk` | 2.1 MB | 日常使用（R8 裁剪过） |
| `-debug.apk` | 22.9 MB | 只在排查问题时用（不裁剪，堆栈最完整） |

两者同一个签名，可互相覆盖安装。

> 已装过旧版本时可直接覆盖：本模块使用**固定 debug keystore**，签名不变。

## 二、在 LSPosed 中启用

1. 打开 **LSPosed** 管理器；
2. 底部「模块」→ 找到 **状态栏古诗** → 打开开关；
3. **作用域不需要手动勾**：本模块以静态作用域声明（`staticScope=true`），
   作用域固定为 **系统界面**（`com.android.systemui`），那一栏在管理器里是只读的。
4. 返回。

## 三、重启 SystemUI

```bash
adb shell su -c "killall com.android.systemui"
```

或使用设置页里的 **重启系统界面** 按钮（需要 root 授权）。

> 安装、更新模块后**必须**重启 SystemUI，否则运行中的进程仍是旧代码。

## 四、打开模块总开关

打开 **状态栏古诗** App：

1. 顶部 **启用模块** → 打开（默认关闭）；
2. 设置 **播放速度**（20–600 px/s，默认 120）；
3. 设置 **时间停留**（0–600 秒，默认 10；0 表示播完立刻下一首）；
4. 按需要调整：顺序/随机、作者、出处、跳过挖孔、上下浮动、横屏暂停、锁屏暂停、白名单。

设置**立即生效**，不需要再重启 SystemUI（配置通过 ContentObserver 推送，最长 3 秒内同步）。

## 五、设置挖孔（本机必做）

本机 ROM 未上报 `DisplayCutout`，需要手动指定：

1. 主页 → **挖孔校准**；
2. 点 **在屏幕上拖动设置挖孔**；
3. 屏幕上会出现一个半透明矩形，透过它能看见真实状态栏：
   - 拖矩形内部 = 移动；
   - 拖四角 = 缩放；
   - **居中预设** 一键放到屏幕水平中央；
4. 对齐前置挖孔后点 **保存**。

## 六、诗库管理

主页 → **古诗虫** → 进入 **古诗库**：

- 每行右侧开关控制该首是否参与播放；
- **上移 / 下移** 调整顺序；
- **编辑 / 删除**（删除有确认弹窗）；
- 底部 **添加诗句**：重复（文本+作者+出处 相同）会被拒绝。

> 内置 6 首只在首次启动时写入一次。删掉之后**不会**因为升级而复活；
> 把诗库删空后也会一直保持为空。

## 七、验证是否生效

```bash
adb logcat -s PoemStatus
```

正常应看到：

```
INJECTED SystemUI process=com.android.systemui pid=12345 sdk=37
POEM hook installed scope=com.android.systemui
CONF apply ver=1 enabled=true poems=6 speed=120 clock=10
POEM start id=… text=长风破浪会有时，直挂云帆济沧海。 speed=120
POEM clock hold 10s
POEM paused restore=ok
```

若完全没有 `INJECTED`：

1. 确认 LSPosed 里模块已启用、作用域勾了 `com.android.systemui`；
2. 确认已重启 SystemUI；
3. 确认 App 里总开关已打开（关闭时模块不注入任何绘制）。

## 八、停止 / 卸载

- **临时停止**：App 内关闭 **启用模块**（立即恢复原生状态栏）；
- **彻底停用**：LSPosed 里关掉模块开关，再重启 SystemUI；
- **卸载**：正常卸载 APK 后重启 SystemUI。

## 九、出问题怎么办

如果状态栏异常（消失、反复重启），LSPosed 会进入**安全模式**（跳过全部模块注入）：

- 用 LSPosed 管理器关闭本模块开关；
- 或重启手机时**不要按音量键**（长按音量键会触发安全模式）。

恢复后可按上文日志逐项排查。
