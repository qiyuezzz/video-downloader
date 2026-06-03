package com.example.videodownload.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = NovaPrimary,
    onPrimary = NovaOnPrimary,
    primaryContainer = NovaPrimaryContainer,
    onPrimaryContainer = NovaOnPrimaryContainer,

    secondary = NovaSecondary,
    secondaryContainer = NovaSecondaryContainer,
    onSecondaryContainer = NovaOnSecondaryContainer,

    tertiary = NovaTertiary,
    tertiaryContainer = NovaTertiaryContainer,
    onTertiaryContainer = NovaOnTertiaryContainer,

    error = NovaError,
    errorContainer = NovaErrorContainer,
    onErrorContainer = NovaOnErrorContainer,

    background = NovaBackground,
    onBackground = NovaOnSurface,
    surface = NovaSurface,
    onSurface = NovaOnSurface,
    surfaceVariant = NovaSurfaceVariant,
    onSurfaceVariant = NovaOnSurfaceVariant,
    outline = NovaOutline,
    outlineVariant = NovaOutlineVariant,
)

/**
 * 深色方案 — OLED 纯黑背景
 * primary: 亮蓝 #90CAF9，在纯黑上清晰可见，按钮文字使用深蓝
 */
private val DarkColorScheme = darkColorScheme(
    primary = NovaPrimaryDark,
    onPrimary = NovaOnPrimaryDark,
    primaryContainer = NovaPrimaryContainerDark,
    onPrimaryContainer = NovaOnPrimaryContainerDark,

    secondary = NovaSecondaryDark,
    secondaryContainer = NovaSecondaryContainerDark,
    onSecondaryContainer = NovaOnSecondaryContainerDark,

    tertiary = NovaTertiaryDark,
    tertiaryContainer = NovaTertiaryContainerDark,
    onTertiaryContainer = NovaOnTertiaryContainerDark,

    error = NovaErrorDark,
    errorContainer = NovaErrorContainerDark,
    onErrorContainer = NovaOnErrorContainerDark,

    background = NovaBackgroundDark,
    onBackground = NovaOnSurfaceDark,
    surface = NovaBackgroundDark, // 改为纯黑，防止灰色透出
    onSurface = NovaOnSurfaceDark,
    surfaceVariant = NovaSurfaceVariantDark,
    onSurfaceVariant = NovaOnSurfaceVariantDark,
    outline = NovaOutlineDark,
    outlineVariant = NovaOutlineVariantDark,
)

@Composable
fun VideoDownloadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
