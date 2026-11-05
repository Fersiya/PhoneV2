package com.example.pccontrol

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Современная палитра: глубокий Midnight Navy с акцентами Electric Cyan и Neon Violet
val CyberDarkPalette = darkColorScheme(
    primary = Color(0xFF00E5FF),          // Неоновый циан
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Color(0xFFA78BFA),        // Фиолетовый
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF3B1E78),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = Color(0xFF10B981),         // Изумрудно-зеленый (статус онлайн)
    background = Color(0xFF0A0F1D),       // Глубокий темный фон
    surface = Color(0xFF111827),          // Поверхность карточек
    surfaceVariant = Color(0xFF1F2937),   // Контейнеры
    onSurface = Color(0xFFF9FAFB),        // Четкий белый текст
    onSurfaceVariant = Color(0xFF9CA3AF), // Вспомогательный серый текст
    outline = Color(0xFF374151),          // Тонкие рамки
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)
