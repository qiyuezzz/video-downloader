package com.example.videodownload.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NovaPrimary,
    secondary = NovaSecondary,
    tertiary = NovaAccent,
    background = NovaSurfaceDark,
    surface = NovaSurfaceDark,
    onSurface = NovaOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = NovaPrimary,
    secondary = NovaSecondary,
    tertiary = NovaAccent,
    background = NovaSurface,
    surface = NovaSurface,
    onSurface = NovaOnSurface
)

@Composable
fun VideoDownloadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // 关闭动态颜色以保持 Nova 品牌视觉一致性
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NovaShapes,
        content = content
    )
}