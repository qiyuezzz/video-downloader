package com.example.videodownload

import android.Manifest
import android.app.UiModeManager
import android.database.ContentObserver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.videodownload.navigation.AppNavigation
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.ui.theme.VideoDownloadTheme

class MainActivity : ComponentActivity() {
    private var flymeNightMode by mutableIntStateOf(-1)
    private val nightModeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            syncFlymeNightMode()
        }
    }

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

        syncFlymeNightMode()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(FLYME_NIGHT_MODE_KEY),
            false,
            nightModeObserver,
        )
        val settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(
                initial = SettingsDataStore.THEME_SYSTEM,
            )
            val systemDarkTheme = isSystemInDarkTheme()
            val followedSystemDarkTheme =
                if (Build.MANUFACTURER.equals("Meizu", ignoreCase = true)) {
                    when (flymeNightMode) {
                        UiModeManager.MODE_NIGHT_YES -> true
                        UiModeManager.MODE_NIGHT_NO -> false
                        else -> systemDarkTheme
                    }
                } else {
                    systemDarkTheme
                }
            val isDarkTheme = when (themeMode) {
                SettingsDataStore.THEME_LIGHT -> false
                SettingsDataStore.THEME_DARK -> true
                else -> followedSystemDarkTheme
            }
            SideEffect { updateWindowAppearance(isDarkTheme) }
            VideoDownloadTheme(darkTheme = isDarkTheme) {
                AppNavigation()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        syncFlymeNightMode()
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(nightModeObserver) }
        super.onDestroy()
    }

    private fun syncFlymeNightMode() {
        flymeNightMode = Settings.Secure.getInt(
            contentResolver,
            FLYME_NIGHT_MODE_KEY,
            -1,
        )
    }

    private fun updateWindowAppearance(isDarkTheme: Boolean) {
        val windowBackground = if (isDarkTheme) {
            0xFF343538.toInt()
        } else {
            ContextCompat.getColor(this, R.color.app_window_background)
        }
        window.setBackgroundDrawable(windowBackground.toDrawable())
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    companion object {
        private const val FLYME_NIGHT_MODE_KEY = "ui_night_mode"
    }
}
