package io.github.rude56.poemstatus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.rude56.poemstatus.ui.PoemApp
import io.github.rude56.poemstatus.ui.theme.PoemTheme

/** 唯一 Activity：所有页面都在 Compose 内导航（不额外起 Activity）。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边显示：内容延伸到状态栏/导航栏下面，由 Scaffold 的 inset 处理避让。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            PoemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PoemApp(context = this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时刷新一次，避免其它进程（或自己刚才的写操作）造成展示滞后。
        AppState.reload(this)
    }
}
