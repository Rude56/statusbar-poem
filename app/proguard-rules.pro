# R8 规则（只在 release 生效；debug 完全不做混淆/裁剪，便于真机排查）。
#
# Hook 侧全部是字符串反射 —— R8 看不到那些引用，但它也改不到字符串本身，
# 所以只要保住「被框架实例化的入口类」与「模块元数据文件」就够了。

# ---- 现代 libxposed 入口 ----
# 框架按 META-INF/xposed/java_init.list 里的全限定名反射实例化并强转成 XposedModule。
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
# 入口类必须保留原名：java_init.list 里写的是源码里的名字。
-keepnames class io.github.rude56.poemstatus.ModuleMain

# 生命周期回调由框架反射调用，名字不能变。
-keepclassmembers class io.github.rude56.poemstatus.ModuleMain {
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
}

# ---- libxposed API 声明 ----
# 全部 compileOnly，运行时由框架提供；R8 会在裁剪时误报缺类/缺成员。
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# ---- Android 组件 ----
# ContentProvider 由系统实例化（SystemUI 通过 ContentResolver.call 访问）。
-keep class io.github.rude56.poemstatus.ConfigProvider { *; }
-keep class io.github.rude56.poemstatus.MainActivity { *; }

# JSON 解析依赖 org.json 的反射式字段访问（optString/put 等），保留结构。
-keep class io.github.rude56.poemstatus.PoemItem { *; }
-keep class io.github.rude56.poemstatus.PoemConfig { *; }
-keep class io.github.rude56.poemstatus.HoleRects { *; }
-keep class io.github.rude56.poemstatus.HoleRects$Hole { *; }

# 保留行号：SystemUI 侧异常堆栈是唯一现场，混淆掉就没法查了。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
