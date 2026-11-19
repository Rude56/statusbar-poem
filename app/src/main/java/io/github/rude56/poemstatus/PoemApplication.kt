package io.github.rude56.poemstatus

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.rude56.poemstatus.ui.theme.AppSettings

class PoemApplication : Application(), ViewModelStoreOwner {
    private val appViewModelStore by lazy { ViewModelStore() }
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
    override fun onCreate() {
        super.onCreate()
        app = this

        if (!isUserUnlocked()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val enable = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("enable_predictive_back", true)
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }
    }
    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    companion object {

        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val method = ApplicationInfo::class.java
                    .getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }
}
val currentAppSettings: AppSettings
    get() = io.github.rude56.poemstatus.ui.theme.ThemeController.getAppSettings(app)
