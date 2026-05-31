package com.example.videodownload.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nova Design Language Palette
val NovaPrimary = Color(0xFF8B5CF6) // Electric Violet
val NovaPrimaryVariant = Color(0xFF6366F1) // Indigo
val NovaSecondary = Color(0xFF10B981) // Emerald
val NovaAccent = Color(0xFFF43F5E) // Rose
val NovaSurface = Color(0xFFF8FAFC) // Slate 50
val NovaOnSurface = Color(0xFF0F172A) // Slate 900
val NovaSurfaceDark = Color(0xFF0F172A) // Slate 900
val NovaOnSurfaceDark = Color(0xFFF1F5F9) // Slate 100

// Standard Material 3 Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Gradients
val NovaGradientPrimary = Brush.horizontalGradient(
    colors = listOf(NovaPrimary, NovaPrimaryVariant)
)

val NovaGradientSurface = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.7f))
)
