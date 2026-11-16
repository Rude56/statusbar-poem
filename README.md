# 状态栏古诗

一个 LSPosed 模块：在系统状态栏里滚动播放古诗。

诗句从右侧进入，向左滚过状态栏，播完恢复原生内容，停留片刻后播放下一首。

- 包名：`io.github.rude56.poemstatus`
- 版本：1.0.0
- 系统：Android 13 及以上
- 作用域：仅系统界面（静态作用域）
- 不联网

## 功能

- 滚动播放，播完自动恢复原生状态栏
- 时间停留 0–600 秒
- 内置 6 首，可增删改、启停、排序、搜索
- 支持导入导出纯文本
- 顺序 / 随机播放，速度 20–600 px/s
- 作者、出处可单独隐藏
- 横屏、锁屏、指定应用前台时自动暂停
- 可选「洞」：让诗句在指定区域传送或隐藏

界面跟随系统深浅色与动态取色。

## 安装

需要 Root 与 LSPosed。

1. 安装 APK
2. 在 LSPosed 中启用本模块
3. 重启系统界面（App 内有一键按钮）
4. 打开 App，开启总开关

设置修改后立即生效。

## 构建

需要 JDK 21 与 Android SDK 37。

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

发布签名从 `keystore.properties` 读取，格式见 `keystore.properties.example`。没有该文件时会用默认 debug 签名，仍可正常构建。

## 许可

[MIT](LICENSE)
