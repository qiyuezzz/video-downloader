package com.example.videodownload

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.videodownload.navigation.AppNavigation
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.ui.theme.VideoDownloadTheme
import com.example.videodownload.util.AppLanguage

class MainActivity : ComponentActivity() {
    private var attachedThemeMode = SettingsDataStore.THEME_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        val localizedContext = AppLanguage.wrapContext(newBase)
        attachedThemeMode = localizedContext.cachedThemeMode()

        val themedContext = when (attachedThemeMode) {
            SettingsDataStore.THEME_LIGHT -> localizedContext.withNightMode(
                Configuration.UI_MODE_NIGHT_NO,
            )
            SettingsDataStore.THEME_DARK -> localizedContext.withNightMode(
                Configuration.UI_MODE_NIGHT_YES,
            )
            else -> localizedContext
        }
        super.attachBaseContext(themedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasSelectedLanguage = AppLanguage.selectedLanguage(this) != null
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.decorView.isForceDarkAllowed = false
        }
        // 冷启动时 windowBackground 已由 values-night + attachBaseContext 的 withNightMode
        // 保证为正确颜色，无需 setApplicationNightMode；后者会让系统对 Light 主题做 forceDark
        // 反色，是「运行时切浅色变黑」的根因。

        val settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            val needsLanguageSelection = remember {
                !hasSelectedLanguage
            }
            val themeMode by settingsDataStore.themeMode.collectAsState(
                initial = attachedThemeMode,
            )
            LaunchedEffect(themeMode) {
                if (themeMode != attachedThemeMode) {
                    attachedThemeMode = themeMode
                    applicationContext.cacheThemeMode(themeMode)
                    // 不调用 setApplicationNightMode()/recreate()：
                    // Compose 主题与 updateWindowAppearance 已经能实时切换，
                    // 触发系统级 uiMode 变更或重建 Activity 都会在窗口空窗期出现黑屏闪烁。
                }
            }
            val isDarkTheme = when (themeMode) {
                SettingsDataStore.THEME_LIGHT -> false
                SettingsDataStore.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            SideEffect { updateWindowAppearance(isDarkTheme) }
            VideoDownloadTheme(darkTheme = isDarkTheme) {
                if (needsLanguageSelection) {
                    LanguageSelectionDialog(
                        onSelected = { language ->
                            AppLanguage.setLanguage(this@MainActivity, language)
                            recreate()
                        },
                    )
                } else {
                    AppNavigation()
                }
            }
        }

    }

    private fun updateWindowAppearance(isDarkTheme: Boolean) {
        val windowBackground = if (isDarkTheme) {
            ContextCompat.getColor(this, R.color.app_window_background_dark)
        } else {
            ContextCompat.getColor(this, R.color.app_window_background_light)
        }
        window.setBackgroundDrawable(windowBackground.toDrawable())
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

private const val THEME_CACHE_NAME = "theme_cache"
private const val THEME_CACHE_KEY = "theme_mode"

private fun Context.cachedThemeMode(): String =
    getSharedPreferences(THEME_CACHE_NAME, Context.MODE_PRIVATE)
        .getString(THEME_CACHE_KEY, SettingsDataStore.THEME_SYSTEM)
        ?.takeIf {
            it == SettingsDataStore.THEME_SYSTEM ||
                it == SettingsDataStore.THEME_LIGHT ||
                it == SettingsDataStore.THEME_DARK
        }
        ?: SettingsDataStore.THEME_SYSTEM

private fun Context.cacheThemeMode(themeMode: String) {
    getSharedPreferences(THEME_CACHE_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_CACHE_KEY, themeMode)
        .apply()
}

private fun Context.withNightMode(nightMode: Int): Context {
    val configuration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
    return createConfigurationContext(configuration)
}

@Composable
private fun LanguageSelectionDialog(onSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("选择语言 / Choose language") },
        text = { Text("请选择应用语言。You can change it later in Settings.") },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onSelected(AppLanguage.CHINESE) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("中文")
                }
                Button(
                    onClick = { onSelected(AppLanguage.ENGLISH) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("English")
                }
            }
        },
    )
}
