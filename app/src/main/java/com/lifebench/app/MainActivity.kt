package com.lifebench.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifebench.app.data.Repo
import com.lifebench.app.navigation.AppNav
import com.lifebench.app.ui.theme.LifeBenchTheme
import com.lifebench.app.ui.theme.ThemeMode

/**
 * 应用入口 Activity：挂载 Compose 内容，从设置中心读取主题模式与字体缩放，
 * 包裹全局主题后载入导航图。主题/字体的更改通过 State 即时生效，无需重建 Activity。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
            val fontScale by Repo.settings.fontScale.collectAsStateWithLifecycle(1.0f)
            val mode = when (themeMode) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            LifeBenchTheme(themeMode = mode, fontScale = fontScale) {
                AppNav()
            }
        }
    }
}
