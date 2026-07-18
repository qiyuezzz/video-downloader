package com.example.videodownload

import android.Manifest
import android.app.UiModeManager
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.drawable.toDrawable
import androidx.core.content.ContextCompat
import com.example.videodownload.navigation.AppNavigation
import com.example.videodownload.ui.theme.VideoDownloadTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户拒绝时下载仍可继续，系统只会隐藏普通通知。 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.decorView.isForceDarkAllowed = false
        }

        // 与 Compose 主题使用同一背景色，避免窗口与页面切换时明暗跳变
        val isDarkTheme = isGlobalDarkModeEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java).setApplicationNightMode(
                if (isDarkTheme) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
            )
        }
        val windowBackground = if (isDarkTheme) {
            0xFF343538.toInt()
        } else {
            ContextCompat.getColor(this, R.color.app_window_background)
        }
        window.setBackgroundDrawable(windowBackground.toDrawable())

        setContent {
            VideoDownloadTheme(darkTheme = isDarkTheme) {
                AppNavigation()
            }
        }

    }

    private fun isGlobalDarkModeEnabled(): Boolean {
        val flymeNightMode = Settings.Secure.getInt(contentResolver, "ui_night_mode", -1)
        val standardNightMode = getSystemService(UiModeManager::class.java).nightMode
        if (Build.MANUFACTURER.equals("Meizu", ignoreCase = true)) {
            when (flymeNightMode) {
                UiModeManager.MODE_NIGHT_YES -> return true
                UiModeManager.MODE_NIGHT_NO -> return false
            }
        }

        return when (standardNightMode) {
            UiModeManager.MODE_NIGHT_YES -> true
            UiModeManager.MODE_NIGHT_NO -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
    }
}
