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
    surfaceDim = NovaSurfaceDim,
    surfaceBright = NovaSurfaceBright,
    surfaceContainerLowest = NovaSurfaceContainerLowest,
    surfaceContainerLow = NovaSurfaceContainerLow,
    surfaceContainer = NovaSurfaceContainer,
    surfaceContainerHigh = NovaSurfaceContainerHigh,
    surfaceContainerHighest = NovaSurfaceContainerHighest,
    outline = NovaOutline,
    outlineVariant = NovaOutlineVariant,
)

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
    surface = NovaSurfaceDark,
    onSurface = NovaOnSurfaceDark,
    surfaceVariant = NovaSurfaceVariantDark,
    onSurfaceVariant = NovaOnSurfaceVariantDark,
    surfaceDim = NovaSurfaceDimDark,
    surfaceBright = NovaSurfaceBrightDark,
    surfaceContainerLowest = NovaSurfaceContainerLowestDark,
    surfaceContainerLow = NovaSurfaceContainerLowDark,
    surfaceContainer = NovaSurfaceContainerDark,
    surfaceContainerHigh = NovaSurfaceContainerHighDark,
    surfaceContainerHighest = NovaSurfaceContainerHighestDark,
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
