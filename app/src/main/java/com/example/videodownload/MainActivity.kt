package com.example.videodownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.drawable.toDrawable
import androidx.navigation.compose.rememberNavController
import com.example.videodownload.navigation.AppNavigation
import com.example.videodownload.ui.theme.VideoDownloadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 在 Compose 渲染前设置窗口背景，防止初始灰色闪烁
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window.setBackgroundDrawable(bgColor.toDrawable())

        setContent {
            val isDarkTheme = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            VideoDownloadTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
