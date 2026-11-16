package io.github.rude56.poemstatus;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;

/**
 * 现代 libxposed API 入口（LSPosed 2.x）。
 *
 * <p>为什么从旧 API（{@code IXposedHookLoadPackage}）迁到现代 API：
 * 旧模块的作用域只是一个「建议列表」（{@code xposedscope}），
 * 用户可以随手把模块勾到任意应用上，模块自身无法拒绝。
 * 只有现代模块才能用 {@code META-INF/xposed/module.prop} 里的
 * <b>{@code staticScope=true}</b> 声明<b>静态作用域</b> ——
 * 用户再也不能把它应用到 {@code com.android.systemui} 之外。
 *
 * <p>模块声明相关的三个文件（都在 {@code src/main/resources/META-INF/xposed/}）：
 * <ul>
 *   <li>{@code java_init.list} —— 本类全限定名；</li>
 *   <li>{@code module.prop} —— {@code minApiVersion}/{@code targetApiVersion}/{@code staticScope}；</li>
 *   <li>{@code scope.list} —— 一行一个包名，只有 {@code com.android.systemui}。</li>
 * </ul>
 */
public final class ModuleMain extends XposedModule {

    /** 是否已经装过（同一个进程只会加载一次模块，这里只是防御）。 */
    private static boolean installed;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Log.i("INJECTED process=" + param.getProcessName()
                + " pid=" + Process.myPid()
                + " sdk=" + Build.VERSION.SDK_INT
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    /**
     * 目标进程里代码（含资源与自定义 ClassLoader）已经就绪。
     *
     * <p>状态栏 Hook 必须挂在这里而不是 {@code onModuleLoaded}：
     * 那个阶段 {@code com.android.systemui} 的类还没加载完。
     */
    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!FeatureAudit.SCOPE_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (installed) {
            return;
        }
        installed = true;
        try {
            SystemUiController.install(this, param.getClassLoader());
        } catch (Throwable t) {
            Log.w("POEM install failed: " + t.getClass().getSimpleName());
        }
    }
}
