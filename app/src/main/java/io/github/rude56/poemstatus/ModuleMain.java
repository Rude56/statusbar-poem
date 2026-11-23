package io.github.rude56.poemstatus;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;

public final class ModuleMain extends XposedModule {
    private static boolean installed;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Log.i("INJECTED process=" + param.getProcessName()
                + " pid=" + Process.myPid()
                + " sdk=" + Build.VERSION.SDK_INT
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String pkg = param.getPackageName();
        Log.diag("PKG READY pkg=" + pkg + " pid=" + Process.myPid());
        if (!FeatureAudit.SCOPE_PACKAGE.equals(pkg)) {
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
