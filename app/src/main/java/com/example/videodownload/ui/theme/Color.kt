package com.example.videodownload.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// Nova Design Language 3.0 — 色彩系统
// 深色模式: 纯黑 OLED 友好背景 + 高对比度紫罗兰主色
// ============================================================

// ---------- 主色 (Primary) ----------
// 亮色: 浓郁的紫罗兰
val NovaPrimary = Color(0xFF7C3AED) // Violet 600
val NovaPrimaryVariant = Color(0xFF6D28D9) // Violet 700
val NovaOnPrimary = Color(0xFFFFFFFF)
val NovaPrimaryContainer = Color(0xFFEDE9FE) // Violet 100
val NovaOnPrimaryContainer = Color(0xFF3B0764) // Violet 950

// 深色: 明亮蓝——OLED 黑底上清晰可见，按钮文字用深蓝
val NovaPrimaryDark = Color(0xFF90CAF9) // Blue 200 — 亮蓝
val NovaPrimaryVariantDark = Color(0xFF64B5F6) // Blue 300
val NovaOnPrimaryDark = Color(0xFF0D47A1) // Blue 900 — 深蓝文字，在亮蓝按钮上可读
val NovaPrimaryContainerDark = Color(0xFF1565C0) // Blue 800
val NovaOnPrimaryContainerDark = Color(0xFFBBDEFB) // Blue 100

// ---------- 辅助色 (Secondary) ----------
val NovaSecondary = Color(0xFF059669) // Emerald 600
val NovaSecondaryContainer = Color(0xFFD1FAE5) // Emerald 100
val NovaOnSecondaryContainer = Color(0xFF022C22) // Emerald 950

val NovaSecondaryDark = Color(0xFF03DAC6) // Material Design Teal
val NovaSecondaryContainerDark = Color(0xFF004D40) // Teal 900
val NovaOnSecondaryContainerDark = Color(0xFFA7F3D0) // Emerald 200

// ---------- 强调色 (Tertiary) ----------
val NovaTertiary = Color(0xFFE11D48) // Rose 600
val NovaTertiaryContainer = Color(0xFFFFE4E6) // Rose 100
val NovaOnTertiaryContainer = Color(0xFF4C0519) // Rose 950

val NovaTertiaryDark = Color(0xFFFB7185) // Rose 400
val NovaTertiaryContainerDark = Color(0xFF881337) // Rose 900
val NovaOnTertiaryContainerDark = Color(0xFFFFE4E6) // Rose 100

// ---------- 警告色 (Warning) ----------
val NovaWarning = Color(0xFFD97706) // Amber 600
val NovaWarningContainer = Color(0xFFFEF3C7) // Amber 100
val NovaOnWarningContainer = Color(0xFF451A03) // Amber 950

val NovaWarningDark = Color(0xFFFBBF24) // Amber 400
val NovaWarningContainerDark = Color(0xFF78350F) // Amber 900
val NovaOnWarningContainerDark = Color(0xFFFEF3C7) // Amber 100

// ---------- 表面色 (Surface) — 亮色 ----------
val NovaBackground = Color(0xFFFFFFFF)
val NovaSurface = Color(0xFFFFFFFF)
val NovaSurfaceVariant = Color(0xFFF1F5F9) // Slate 100
val NovaOnSurface = Color(0xFF0F172A) // Slate 900
val NovaOnSurfaceVariant = Color(0xFF64748B) // Slate 500
val NovaOutline = Color(0xFFCBD5E1) // Slate 300
val NovaOutlineVariant = Color(0xFFE2E8F0) // Slate 200

// ---------- 表面色 (Surface) — 深色 OLED 优化 ----------
// 纯黑背景，不同灰度区分层次，不发蓝不发光
val NovaBackgroundDark = Color(0xFF000000)   // 纯黑，OLED 不发光
val NovaSurfaceDark = Color(0xFF121212)     // Material Dark Surface — 第一层
val NovaSurfaceVariantDark = Color(0xFF1C1C1C) // 第二层（卡片/列表）
val NovaOnSurfaceDark = Color(0xFFE4E4E7)   // Zinc 200 — 柔和高亮文字
val NovaOnSurfaceVariantDark = Color(0xFFA1A1AA) // Zinc 400 — 次级文字
val NovaOutlineDark = Color(0xFF3F3F46)     // Zinc 700
val NovaOutlineVariantDark = Color(0xFF27272A) // Zinc 800

// ---------- 错误色 (Error) ----------
val NovaError = Color(0xFFDC2626) // Red 600
val NovaErrorContainer = Color(0xFFFEE2E2) // Red 100
val NovaOnErrorContainer = Color(0xFF450A0A) // Red 950

val NovaErrorDark = Color(0xFFEF4444) // Red 500 — 亮红，深色背景上醒目
val NovaErrorContainerDark = Color(0xFF7F1D1D) // Red 900
val NovaOnErrorContainerDark = Color(0xFFFEE2E2) // Red 100

// ---------- 渐变 (Gradients) ----------
val NovaGradientPrimary = Brush.horizontalGradient(
    colors = listOf(NovaPrimary, NovaPrimaryVariant)
)

val NovaGradientPrimaryDark = Brush.horizontalGradient(
    colors = listOf(NovaPrimaryDark, NovaPrimaryVariantDark)
)

val NovaGradientSecondary = Brush.horizontalGradient(
    colors = listOf(NovaSecondary, Color(0xFF10B981))
)

val NovaGradientSecondaryDark = Brush.horizontalGradient(
    colors = listOf(NovaSecondaryDark, Color(0xFF6EE7B7))
)
