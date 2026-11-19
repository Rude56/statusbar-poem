# 入口类由 java_init.list 反射实例化，名字与构造函数不能被裁掉或改名。
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keepnames class io.github.rude56.poemstatus.ModuleMain

# 生命周期回调由框架反射调用，方法名必须保持。
-keepclassmembers class io.github.rude56.poemstatus.ModuleMain {
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
}

# libxposed 是 compileOnly，运行时由框架提供；R8 会误报缺类。
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# 以下类由系统或反射按名访问（Provider、Activity、JSON 结构）。
-keep class io.github.rude56.poemstatus.ConfigProvider { *; }
-keep class io.github.rude56.poemstatus.MainActivity { *; }

-keep class io.github.rude56.poemstatus.PoemItem { *; }
-keep class io.github.rude56.poemstatus.PoemConfig { *; }
-keep class io.github.rude56.poemstatus.HoleRects { *; }
-keep class io.github.rude56.poemstatus.HoleRects$Hole { *; }

# 保留行号：SystemUI 侧堆栈是唯一现场。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
