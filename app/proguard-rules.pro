-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keepnames class io.github.rude56.poemstatus.ModuleMain

-keepclassmembers class io.github.rude56.poemstatus.ModuleMain {
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
}

-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class io.github.rude56.poemstatus.ConfigProvider { *; }
-keep class io.github.rude56.poemstatus.MainActivity { *; }

-keep class io.github.rude56.poemstatus.PoemItem { *; }
-keep class io.github.rude56.poemstatus.PoemConfig { *; }
-keep class io.github.rude56.poemstatus.HoleRects { *; }
-keep class io.github.rude56.poemstatus.HoleRects$Hole { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
