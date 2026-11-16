package io.github.rude56.poemstatus;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;

/**
 * 现代 libxposed API 入口：只有现代模块能用 {@code module.prop} 的 {@code staticScope=true} 声明静态作用域，
 * 把模块锁在 {@code com.android.systemui}（声明文件：{@code java_init.list} / {@code module.prop} / {@code scope.list}）。
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
     * 目标进程里代码（含资源与自定义 ClassLoader）已经就绪；只处理 {@link FeatureAudit#SCOPE_PACKAGE}。
     * 状态栏 Hook 必须挂在这里：{@code onModuleLoaded} 阶段 {@code com.android.systemui} 的类还没加载完。
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
