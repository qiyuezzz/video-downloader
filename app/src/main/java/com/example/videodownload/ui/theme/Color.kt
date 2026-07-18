package com.example.videodownload.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 品牌色：浅色与深色模式保持相同的靛蓝语义。
val NovaPrimary = Color(0xFF5B5FEF)
val NovaPrimaryVariant = Color(0xFF7C4DFF)
val NovaOnPrimary = Color.White
val NovaPrimaryContainer = Color(0xFFE7E7FF)
val NovaOnPrimaryContainer = Color(0xFF23235F)

val NovaPrimaryDark = Color(0xFFAEB0FF)
val NovaPrimaryVariantDark = Color(0xFFC3A8FF)
val NovaOnPrimaryDark = Color(0xFF17184A)
val NovaPrimaryContainerDark = Color(0xFF303372)
val NovaOnPrimaryContainerDark = Color(0xFFE4E4FF)

// 青绿色用于下载、完成和积极状态。
val NovaSecondary = Color(0xFF0F9F91)
val NovaSecondaryContainer = Color(0xFFD5F5EF)
val NovaOnSecondaryContainer = Color(0xFF073A35)
val NovaSecondaryDark = Color(0xFF65DCCB)
val NovaSecondaryContainerDark = Color(0xFF124D47)
val NovaOnSecondaryContainerDark = Color(0xFFCBFFF5)

// 暖橙用于预览、提醒和需要注意的操作。
val NovaTertiary = Color(0xFFEF6C45)
val NovaTertiaryContainer = Color(0xFFFFE6DD)
val NovaOnTertiaryContainer = Color(0xFF5B1C0B)
val NovaTertiaryDark = Color(0xFFFFA184)
val NovaTertiaryContainerDark = Color(0xFF71321F)
val NovaOnTertiaryContainerDark = Color(0xFFFFE3DA)

val NovaWarning = Color(0xFFC77A08)
val NovaWarningContainer = Color(0xFFFFEFC9)
val NovaOnWarningContainer = Color(0xFF493000)
val NovaWarningDark = Color(0xFFFFC45C)
val NovaWarningContainerDark = Color(0xFF5F420B)
val NovaOnWarningContainerDark = Color(0xFFFFEFC9)

// 浅色表面使用冷灰背景，让白色内容卡片自然浮起。
val NovaBackground = Color(0xFFF6F7FC)
val NovaSurface = Color(0xFFFFFFFF)
val NovaSurfaceVariant = Color(0xFFF0F2F8)
val NovaOnSurface = Color(0xFF171927)
val NovaOnSurfaceVariant = Color(0xFF686B7D)
val NovaOutline = Color(0xFFBFC3D4)
val NovaOutlineVariant = Color(0xFFE0E3ED)
val NovaSurfaceDim = Color(0xFFD8DAE4)
val NovaSurfaceBright = Color(0xFFFFFFFF)
val NovaSurfaceContainerLowest = Color(0xFFFFFFFF)
val NovaSurfaceContainerLow = Color(0xFFF1F3F9)
val NovaSurfaceContainer = Color(0xFFEBEDF5)
val NovaSurfaceContainerHigh = Color(0xFFE6E8F0)
val NovaSurfaceContainerHighest = Color(0xFFE0E3ED)

// 深色模式使用 Flyme 切换页面后的稳定灰色，避免明暗跳变。
val NovaBackgroundDark = Color(0xFF343538)
val NovaSurfaceDark = Color(0xFF38393E)
val NovaSurfaceVariantDark = Color(0xFF303238)
val NovaOnSurfaceDark = Color(0xFFF2F2F8)
val NovaOnSurfaceVariantDark = Color(0xFFADB0C0)
val NovaOutlineDark = Color(0xFF454A5D)
val NovaOutlineVariantDark = Color(0xFF292E3E)
val NovaSurfaceDimDark = Color(0xFF343538)
val NovaSurfaceBrightDark = Color(0xFF44464C)
val NovaSurfaceContainerLowestDark = Color(0xFF303134)
val NovaSurfaceContainerLowDark = Color(0xFF36373C)
val NovaSurfaceContainerDark = Color(0xFF38393E)
val NovaSurfaceContainerHighDark = Color(0xFF3C3D43)
val NovaSurfaceContainerHighestDark = Color(0xFF42434A)

val NovaError = Color(0xFFD83B4D)
val NovaErrorContainer = Color(0xFFFFE1E5)
val NovaOnErrorContainer = Color(0xFF5A101A)
val NovaErrorDark = Color(0xFFFF8794)
val NovaErrorContainerDark = Color(0xFF6C2832)
val NovaOnErrorContainerDark = Color(0xFFFFE1E5)

val NovaGradientPrimary = Brush.linearGradient(
    listOf(NovaPrimary, NovaSecondary),
)
val NovaGradientPrimaryDark = Brush.linearGradient(
    listOf(NovaPrimaryDark, NovaSecondaryDark),
)
val NovaGradientSecondary = Brush.linearGradient(
    listOf(Color(0xFF0F9F91), Color(0xFF38BFAF)),
)
val NovaGradientSecondaryDark = Brush.linearGradient(
    listOf(Color(0xFF39BFAF), Color(0xFF75E2D3)),
)
