package com.example.pccontrol

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Премиальная кибер-палитра Obsidian Glass & Neon Accents
val CyberDarkPalette = darkColorScheme(
    primary = Color(0xFF00F0FF),          // Электрический ультра-циан
    onPrimary = Color(0xFF002930),
    primaryContainer = Color(0xFF0E3846),
    onPrimaryContainer = Color(0xFFCEFBFF),
    secondary = Color(0xFFB388FF),        // Неоновый лавандовый индиго
    onSecondary = Color(0xFF280B61),
    secondaryContainer = Color(0xFF3F1F82),
    onSecondaryContainer = Color(0xFFEDE7F6),
    tertiary = Color(0xFF10B981),         // Изумрудно-мятный (онлайн, успех)
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0A0D16),       // Глубокий бархатный Obsidian Black
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121726),          // Матовая сапфирово-графитовая поверхность
    onSurface = Color(0xFFF8FAFC),        // Кристальный четкий текст
    surfaceVariant = Color(0xFF1B2236),   // Элементы ввода, контейнеры, неактивные чипы
    onSurfaceVariant = Color(0xFF94A3B8), // Вспомогательный серебристо-серый
    outline = Color(0xFF26334D),          // Тонкие рамки с легким неоновым отблеском
    outlineVariant = Color(0xFF1A2234),
    error = Color(0xFFF43F5E),            // Яркий рубиново-розовый
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6)
)

// Дополнительные акцентные цвета
object CyberColors {
    val Cyan = Color(0xFF00F0FF)
    val Violet = Color(0xFFB388FF)
    val Emerald = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Ruby = Color(0xFFF43F5E)
    val CardBg = Color(0xFF121726)
    val CardBorder = Color(0xFF26334D)
    val GlassDark = Color(0xCC0A0D16)
}

private fun darkTheme(
    primary: Long, onPrimary: Long, secondary: Long, bg: Long, surface: Long, variant: Long, outline: Long
): ColorScheme = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(variant),
    onPrimaryContainer = Color(0xFFF1F5F9),
    secondary = Color(secondary),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(variant),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF10B981),
    background = Color(bg),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(surface),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(variant),
    onSurfaceVariant = Color(0xFFA1A8B8),
    outline = Color(outline),
    outlineVariant = Color(variant),
    error = Color(0xFFF43F5E),
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6)
)

/** Доступные темы: id, название, схема. Выбор хранится в настройках приложения. */
val AppThemes: List<Triple<String, String, ColorScheme>> = listOf(
    Triple("cyber", L("Кибер"), CyberDarkPalette),
    Triple("sunset", L("Закат"), darkTheme(0xFFFF9F43, 0xFF2B1600, 0xFFFF6B8B, 0xFF140E0C, 0xFF211815, 0xFF2E221D, 0xFF4A3329)),
    Triple("forest", L("Лес"), darkTheme(0xFF6EE7A0, 0xFF03210F, 0xFFD9F36B, 0xFF0A120D, 0xFF121D16, 0xFF1B2A20, 0xFF2C4434)),
    Triple("ocean", L("Океан"), darkTheme(0xFF5AA9FF, 0xFF00192E, 0xFF7CE0E8, 0xFF080F1A, 0xFF0F1A2B, 0xFF182740, 0xFF2A4166)),
    Triple("grape", L("Виноград"), darkTheme(0xFFC792FF, 0xFF220A40, 0xFFFF8AD8, 0xFF100B18, 0xFF1A1326, 0xFF261C38, 0xFF3F2E5E)),
    Triple(
        "light", L("Светлая"),
        lightColorScheme(
            primary = Color(0xFF0B6BCB), onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E8FB), onPrimaryContainer = Color(0xFF062B52),
            secondary = Color(0xFF7A4BD6), tertiary = Color(0xFF0E9F6E),
            background = Color(0xFFF4F6FA), onBackground = Color(0xFF12161F),
            surface = Color.White, onSurface = Color(0xFF12161F),
            surfaceVariant = Color(0xFFE6EAF2), onSurfaceVariant = Color(0xFF4B5565),
            outline = Color(0xFFB9C1D1), outlineVariant = Color(0xFFDCE1EB),
            error = Color(0xFFD7263D)
        )
    )
)

fun colorSchemeFor(id: String): ColorScheme = AppThemes.firstOrNull { it.first == id }?.third ?: CyberDarkPalette
