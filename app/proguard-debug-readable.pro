# ciDebug 用：照常压缩裁剪，但保留可读的类名与行号。
#
# 目的：CI 上手动勾选就能拿到一个「能覆盖安装、堆栈看得懂」的包。
# release 会混淆，堆栈是 a.b.c；这里只关掉混淆，体积与 release 基本一致。
#
# 注意不要改成关闭 minifyEnabled —— 那会让 Compose 与 miuix 的类全部保留，
# APK 从 3.8 MB 涨到 72 MB。

# 保留所有类名与方法名（不混淆，只裁剪未使用的）。
-dontobfuscate

# 保留行号，堆栈才有 文件:行。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
