# 状态栏古诗

一个 [LSPosed 模块](https://modules.lsposed.org/module/io.github.rude56.poemstatus)：在系统状态栏里滚动播放古诗。

诗句从屏幕右侧滚入、向左滚过状态栏，只遮住诗句正下方那一小段；
播完恢复原生状态栏，停留片刻后播放下一首。

- 仅注入 `com.android.systemui`，静态作用域，不需要也不能勾选其他应用
- 不申请联网权限，全部离线
- Android 13（API 33）及以上

## 安装

需要 Root 与 LSPosed。

1. 安装 APK
2. 在 LSPosed 中启用本模块（作用域已固定为「系统界面」，那一栏是只读的）
3. 重启系统界面：App → 设置 → 维护 →「重启系统界面」（需 root 授权），或直接重启手机
4. 打开 App，开启总开关

装完或更新模块后必须重启一次系统界面，否则跑的还是旧代码；之后改设置即时生效。

卸载：卸载 APK 后重启系统界面。

## 构建

需要 JDK 21、Gradle 9.6.0（AGP 9.4.1）与 Android SDK Platform 37 / Build-Tools 37.0.0。

./gradlew :app:assembleRelease

产物在 `app/build/outputs/apk/release/`。发布签名从 `keystore.properties` 读取，格式见 `keystore.properties.example`；没有该文件时请自行签名产物。

## 致谢

- 灵感来源：**[io.github.wumisc.antiburn](https://github.com/Xposed-Modules-Repo/io.github.wumisc.antiburn)**
- UI 设计参考：**[miuix](https://github.com/compose-miuix-ui/miuix)、[KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)**

## 许可

[MIT](LICENSE)
